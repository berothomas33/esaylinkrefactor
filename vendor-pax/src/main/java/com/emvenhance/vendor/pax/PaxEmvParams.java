package com.emvenhance.vendor.pax;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.network.EmvParamDownloadClient;
import com.emvenhance.network.HostHeaders;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.pax.bizentity.db.helper.AmexAidDbHelper;
import com.pax.bizentity.db.helper.DpasAidDbHelper;
import com.pax.bizentity.db.helper.EFTAidDbHelper;
import com.pax.bizentity.db.helper.GreendaoHelper;
import com.pax.bizentity.db.helper.JcbAidDbHelper;
import com.pax.bizentity.db.helper.MirAidDbHelper;
import com.pax.bizentity.db.helper.PBOCAidDbHelper;
import com.pax.bizentity.db.helper.PaypassAidDbHelper;
import com.pax.bizentity.db.helper.PaywaveAidDbHelper;
import com.pax.bizentity.db.helper.PureAidDbHelper;
import com.pax.bizentity.db.helper.RupayAidDbHelper;
import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.EmvCapk;
import com.pax.bizentity.entity.clss.amex.AmexAidBean;
import com.pax.bizentity.entity.clss.dpas.DpasAidBean;
import com.pax.bizentity.entity.clss.eft.EFTAidBean;
import com.pax.bizentity.entity.clss.jcb.JcbAidBean;
import com.pax.bizentity.entity.clss.mir.MirAidBean;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;
import com.pax.bizentity.entity.clss.pboc.PBOCAidBean;
import com.pax.bizentity.entity.clss.pure.PureAidBean;
import com.pax.bizentity.entity.clss.rupay.RupayAidBean;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.configservice.impl.ConfigInit;
import com.pax.configservice.impl.EmvParamUpdater;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.clss.BaseAid;
import com.pax.emvbase.param.clss.BaseParam;
import com.pax.emvbase.param.common.Capk;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * PAX's {@link EmvParams}: the GreenDAO parameter database, the kernel's parameter cache
 * ({@link PaxTerminal#kernelParams()}), and the host's parameter package
 * ({@link EmvParamDownloadClient} + {@link EmvParamUpdater}). Every row is rendered here, so the
 * screen never sees a PAX type. Nothing writes the database while a transaction runs.
 */
final class PaxEmvParams implements EmvParams {

    /**
     * The tmsFileDownload POS type: always "pax" for this vendor. Not ModelInfo#getTerminalModel()
     * — a captured old-app call hit foundation/tmsFileDownload/pax (200, real zip back) whatever
     * the model; the per-device model ("A920Pro" etc.) 500'd on the host.
     */
    private static final String POS_TYPE = "pax";

    private final PaxTerminal terminal;

    PaxEmvParams(PaxTerminal terminal) {
        this.terminal = terminal;
    }

    @Override
    public List<String> categories() {
        List<String> labels = new ArrayList<>();
        for (Category category : Category.values()) {
            labels.add(category.label);
        }
        return labels;
    }

    @Override
    public List<Row> rows(String category) {
        Category c = Category.of(category);
        List<Row> rows = new ArrayList<>();
        for (Object item : c.loader.load()) {
            rows.add(new PaxRow(c.renderer.render(item), item));
        }
        return rows;
    }

    /** {@inheritDoc} Read from {@link PaxTerminal#kernelParams()}, which never loads it. */
    @Nullable
    @Override
    public List<Row> cachedRows(String category) {
        EmvProcessParam cache = terminal.kernelParams();
        if (cache == null) {
            return null;
        }
        Category c = Category.of(category);
        List<?> entries = c.cached.entries(cache);
        if (entries == null) {
            throw new IllegalStateException(c.label + " is not in the kernel's cache");
        }
        List<Row> rows = new ArrayList<>();
        for (Object entry : entries) {
            rows.add(new PaxRow(c.cachedRenderer.render(entry), entry));
        }
        return rows;
    }

    /**
     * Refreshes the saved access token first if it is near or past expiry (rather than handing
     * the host whatever token onboarding first minted), then downloads with the authenticated
     * headers — the confirmed post-onboarding contract — and applies the package. Refused while
     * a transaction runs (checked again right before the apply): the apply deletes and rewrites
     * the tables that transaction may read. A changed database reloads at the next transaction
     * ({@link EmvParamUpdater} invalidates the cache).
     */
    @Override
    public Single<String> download() {
        OnboardingState state = new OnboardingState(BaseApplication.getAppContext());
        String sn = terminal.serialNumber();
        return Completable.fromAction(this::refuseWhileBusy)
                .andThen(new OnboardingClient().ensureValidAccessToken(HostHeaders.build(sn), state))
                .flatMap(accessToken -> new EmvParamDownloadClient()
                        .download(HostHeaders.buildAuthenticated(sn, accessToken), POS_TYPE))
                .map(zip -> {
                    refuseWhileBusy();
                    return EmvParamUpdater.applyFromZip(zip).summarize();
                });
    }

    /** Seeds the database from the bundled JSON; refused while a transaction runs. Blocking. */
    @Override
    public void seedDefaults() {
        refuseWhileBusy();
        new ConfigInit().init(); // synchronous; reloads the cache when done
    }

    private void refuseWhileBusy() {
        if (terminal.isBusy()) {
            throw new IllegalStateException("A transaction is running — try again when it ends");
        }
    }

    @Override
    public List<String> downloadedFiles() {
        return Arrays.asList(EmvParamUpdater.CONTACT_FILE, EmvParamUpdater.CONTACTLESS_FILE);
    }

    @Nullable
    @Override
    public File downloadedFile(String name) {
        File dir = EmvParamUpdater.diagnosisDir();
        File file = dir == null ? null : new File(dir, name);
        return file != null && file.isFile() ? file : null;
    }

    /** One entry — a database row or a cache entry — and every field of it. */
    private static final class PaxRow implements Row {

        private final String title;
        private final Object item;

        PaxRow(String title, Object item) {
            this.title = title;
            this.item = item;
        }

        @NonNull
        @Override
        public String title() {
            return title;
        }

        @Override
        public String details() {
            return describe(item);
        }
    }

    private interface Loader {
        List<?> load();
    }

    private interface Renderer {
        String render(Object item);
    }

    /** A category's entries in the kernel's cache; {@code null} when the cache lacks it. */
    private interface CacheEntries {
        @Nullable
        List<?> entries(EmvProcessParam cache);
    }

    /**
     * One EMV param category: how to load it from the database and render each row, and how to
     * read its entries in the kernel's parameter cache (what a transaction uses) and render them.
     */
    private enum Category {
        AID("AID",
                () -> GreendaoHelper.getEmvAidHelper().loadAll(),
                item -> row(((EmvAid) item).getAppName(), ((EmvAid) item).getAid()),
                EmvProcessParam::getEmvAidList,
                entry -> hex(((com.pax.emvbase.param.contact.EmvAid) entry).getApplicationID())),
        CAPK("CAPK",
                () -> GreendaoHelper.getEmvCapkHelper().loadAll(),
                item -> "RID " + ((EmvCapk) item).getRID() + "   Key " + ((EmvCapk) item).getKeyID(),
                cache -> cache.getCapkParam() == null ? null : cache.getCapkParam().getCapkList(),
                entry -> "RID " + hex(((Capk) entry).getRid())
                        + "   Key " + (((Capk) entry).getKeyId() & 0xFF)),
        AMEX("Amex",
                () -> AmexAidDbHelper.getInstance().loadAll(),
                item -> row(((AmexAidBean) item).getAppName(), ((AmexAidBean) item).getAid()),
                cache -> clss(cache.getAmexParam()), PaxEmvParams::clssRow),
        PAYPASS("PayPass",
                () -> PaypassAidDbHelper.getInstance().loadAll(),
                item -> row(((PayPassAidBean) item).getAppName(), ((PayPassAidBean) item).getAid()),
                cache -> clss(cache.getPayPassParam()), PaxEmvParams::clssRow),
        PAYWAVE("PayWave",
                () -> PaywaveAidDbHelper.getInstance().loadAll(),
                item -> row(((PaywaveAidBean) item).getAppName(), ((PaywaveAidBean) item).getAid()),
                cache -> clss(cache.getPayWaveParam()), PaxEmvParams::clssRow),
        DPAS("Dpas",
                () -> DpasAidDbHelper.getInstance().loadAll(),
                item -> row(((DpasAidBean) item).getAppName(), ((DpasAidBean) item).getAid()),
                cache -> clss(cache.getDpasParam()), PaxEmvParams::clssRow),
        EFT("EFT",
                () -> EFTAidDbHelper.getInstance().loadAll(),
                item -> row(((EFTAidBean) item).getAppName(), ((EFTAidBean) item).getAid()),
                cache -> clss(cache.getEftParam()), PaxEmvParams::clssRow),
        JCB("Jcb",
                () -> JcbAidDbHelper.getInstance().loadAll(),
                item -> row(((JcbAidBean) item).getAppName(), ((JcbAidBean) item).getAid()),
                cache -> clss(cache.getJcbParam()), PaxEmvParams::clssRow),
        MIR("Mir",
                () -> MirAidDbHelper.getInstance().loadAll(),
                item -> row(((MirAidBean) item).getAppName(), ((MirAidBean) item).getAid()),
                cache -> clss(cache.getMirParam()), PaxEmvParams::clssRow),
        PBOC("Pboc",
                () -> PBOCAidDbHelper.getInstance().loadAll(),
                item -> row(((PBOCAidBean) item).getAppName(), ((PBOCAidBean) item).getAid()),
                cache -> clss(cache.getPbocParam()), PaxEmvParams::clssRow),
        PURE("Pure",
                () -> PureAidDbHelper.getInstance().loadAll(),
                item -> row(((PureAidBean) item).getAppName(), ((PureAidBean) item).getAid()),
                cache -> clss(cache.getPureParam()), PaxEmvParams::clssRow),
        RUPAY("RuPay",
                () -> RupayAidDbHelper.getInstance().loadAll(),
                item -> row(((RupayAidBean) item).getAppName(), ((RupayAidBean) item).getAid()),
                cache -> clss(cache.getRuPayParam()), PaxEmvParams::clssRow);

        final String label;
        final Loader loader;
        final Renderer renderer;
        final CacheEntries cached;
        final Renderer cachedRenderer;

        Category(String label, Loader loader, Renderer renderer, CacheEntries cached,
                Renderer cachedRenderer) {
            this.label = label;
            this.loader = loader;
            this.renderer = renderer;
            this.cached = cached;
            this.cachedRenderer = cachedRenderer;
        }

        static Category of(String label) {
            for (Category category : values()) {
                if (category.label.equals(label)) {
                    return category;
                }
            }
            throw new IllegalArgumentException("No EMV param category " + label);
        }
    }

    /** A contactless kernel's cached AIDs; {@code null} when the cache lacks the kernel. */
    @Nullable
    private static List<?> clss(@Nullable BaseParam<? extends BaseAid> param) {
        return param == null ? null : param.getAidList();
    }

    /** A cached contactless AID, the way {@link #row} shows its database row. */
    private static String clssRow(Object entry) {
        BaseAid aid = (BaseAid) entry;
        byte[] name = aid.getAppName();
        return row(name == null ? "" : new String(name, StandardCharsets.US_ASCII).trim(),
                hex(aid.getAid()));
    }

    private static String hex(@Nullable byte[] bytes) {
        return bytes == null ? "" : ConvertUtils.bcd2Str(bytes);
    }

    private static String row(String appName, String aid) {
        return appName + "   (" + aid + ")";
    }

    // ─── Every field of one parameter object (a cached kernel AID, CAPK or the like) as text,
    // the way the kernel gets it: byte arrays in hex, nested objects and lists indented. ─────

    private static String describe(Object value) {
        StringBuilder text = new StringBuilder();
        append(text, value, "");
        return text.toString();
    }

    private static void append(StringBuilder text, Object value, String indent) {
        for (Class<?> type = value.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                Object fieldValue;
                try {
                    fieldValue = field.get(value);
                } catch (IllegalAccessException e) {
                    continue;
                }
                text.append(indent).append(field.getName()).append(" = ");
                appendValue(text, fieldValue, indent);
            }
        }
    }

    private static void appendValue(StringBuilder text, Object value, String indent) {
        if (value == null) {
            text.append("null\n");
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            text.append(bytes.length == 0 ? "(empty)" : ConvertUtils.bcd2Str(bytes)).append('\n');
        } else if (value instanceof Byte) {
            text.append(String.format("%02X", (Byte) value & 0xFF)).append('\n');
        } else if (value.getClass().isArray()) {
            StringBuilder items = new StringBuilder("[");
            for (int i = 0; i < Array.getLength(value); i++) {
                items.append(i == 0 ? "" : ", ").append(Array.get(value, i));
            }
            text.append(items).append("]\n");
        } else if (value instanceof Collection) {
            Collection<?> items = (Collection<?>) value;
            text.append(items.size()).append(" item(s)\n");
            int i = 0;
            for (Object item : items) {
                text.append(indent).append("  [").append(i++).append("]\n");
                if (isPlain(item)) {
                    text.append(indent).append("    ");
                    appendValue(text, item, indent + "    ");
                } else {
                    append(text, item, indent + "    ");
                }
            }
        } else if (isPlain(value)) {
            text.append(value).append('\n');
        } else {
            text.append('\n');
            append(text, value, indent + "  ");
        }
    }

    private static boolean isPlain(Object value) {
        return value == null || value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof Enum
                || value instanceof byte[] || value.getClass().isArray();
    }
}
