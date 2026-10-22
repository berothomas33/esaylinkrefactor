package com.pax.configservice.impl;

import androidx.annotation.Nullable;
import com.pax.bizentity.BuildConfig;
import com.pax.bizentity.db.helper.AmexAidDbHelper;
import com.pax.bizentity.db.helper.AmexDrlDbHelper;
import com.pax.bizentity.db.helper.BaseDaoHelper;
import com.pax.bizentity.db.helper.CapkRevokeDbHelper;
import com.pax.bizentity.db.helper.DaoManager;
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
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.xml.ClssXmlParamParser;
import com.pax.configservice.xml.EmvXmlParamParser;
import com.pax.configservice.xml.TerminalWideValues;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Applies a downloaded EMV parameter package (a zip containing {@code emv_param.emv} /
 * {@code clss_param.clss}) to the same GreenDAO tables {@link ConfigInit} seeds at first boot
 * from the bundled JSON assets — making the EMV/CLSS kernel configuration updatable at runtime
 * instead of fixed at build time.
 *
 * <p>One rule for both files (rule 7: parameters come from the host only): a file in the
 * package is the whole truth for its side — a section it brings replaces the table, a section
 * it doesn't bring is deleted. A file not in the package (or one that fails to parse) leaves its
 * side as it was. Each section is replaced in one database transaction: a failed insert rolls
 * its delete back, so a table is never left empty by a half-applied section. A package with
 * neither file is a failed download. A changed database makes the next transaction reload the
 * kernels' parameters ({@link EmvParamService#invalidate()}).
 *
 * <p>Scoped to what {@code emvParam.zip}'s sample files actually cover: contact AID/CAPK/
 * revocation, and PayPass/PayWave/Amex from the contactless side. DPAS/EFT/JCB/MIR/PBOC/PURE/
 * RUPAY aren't touched — see {@link ClssXmlParamParser}'s javadoc for why.
 */
public final class EmvParamUpdater {

    private static final String TAG = "EmvParamUpdater";

    /** The contact and contactless files of the last download, as kept by saveForDiagnosis. */
    public static final String CONTACT_FILE = "emv_param.emv";
    public static final String CONTACTLESS_FILE = "clss_param.clss";

    private EmvParamUpdater() {
    }

    public static EmvParamUpdateResult applyFromZip(byte[] zipBytes) {
        EmvParamUpdateResult result = new EmvParamUpdateResult();
        byte[] emvXml = null;
        byte[] clssXml = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".emv")) {
                    emvXml = readAll(zis);
                } else if (name.endsWith(".clss")) {
                    clssXml = readAll(zis);
                }
            }
        } catch (IOException e) {
            LogUtils.e(TAG, "Failed to unzip EMV param package", e);
            result.failed("couldn't read the downloaded package (" + e.getMessage() + ")");
            return result;
        }

        saveForDiagnosis(CONTACT_FILE, emvXml);
        saveForDiagnosis(CONTACTLESS_FILE, clssXml);

        if (emvXml == null && clssXml == null) {
            // An HTTP 200 that isn't a parameter package (an error page, an empty zip).
            result.failed("the download has neither " + CONTACT_FILE + " nor " + CONTACTLESS_FILE);
            return result;
        }
        List<EmvAid> contactAids = null;
        if (emvXml != null) {
            contactAids = applyEmvXml(emvXml, result);
        } else {
            LogUtils.w(TAG, "EMV param package had no *.emv entry — contact AID/CAPK unchanged");
        }
        if (clssXml != null && contactAids == null) {
            // Contactless AIDs take their terminal-wide values (Terminal Type, Additional
            // Terminal Capabilities, Security Capability) from this package's contact file.
            // Without it they would come from whatever is stored — the bundled JSON seed on a
            // terminal that never downloaded (rule 7) — so the contactless side is left as it was.
            result.failed(CONTACTLESS_FILE + " not applied: it needs this package's " + CONTACT_FILE);
        } else if (clssXml != null) {
            applyClssXml(clssXml, new TerminalWideValues(contactAids), result);
        } else {
            LogUtils.w(TAG, "EMV param package had no *.clss entry — CLSS params unchanged");
        }
        if (result.changedDatabase()) {
            EmvParamService.invalidate(); // the next transaction loads what was applied
        }
        return result;
    }

    /** @return the parsed contact AIDs (with their ICS values), or {@code null} if parsing failed */
    @Nullable
    private static List<EmvAid> applyEmvXml(byte[] xml, EmvParamUpdateResult result) {
        try {
            EmvXmlParamParser.Result parsed = EmvXmlParamParser.parse(new ByteArrayInputStream(xml));
            result.sectionsIn("emv_param.emv", parsed.sections);
            EmvParamService service = new EmvParamService();

            if (!parsed.aids.isEmpty()) {
                replace("contact AID", parsed.aids.size(), result,
                        () -> GreendaoHelper.getEmvAidHelper().deleteAll()
                                && service.insertEmvAid(parsed.aids));
            } else {
                remove("contact AID", result, GreendaoHelper.getEmvAidHelper());
            }

            List<?> capkList = parsed.capk.getCapkList();
            if (capkList != null && !capkList.isEmpty()) {
                // The revocation list goes with its CAPKs: none in the file is none revoked.
                replace("CAPK", capkList.size(), result,
                        () -> GreendaoHelper.getEmvCapkHelper().deleteAll()
                                && CapkRevokeDbHelper.getInstance().deleteAll()
                                && service.insertEmvCapk(parsed.capk));
            } else {
                remove("CAPK", result, GreendaoHelper.getEmvCapkHelper(),
                        CapkRevokeDbHelper.getInstance());
            }
            return parsed.aids;
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to parse/apply emv_param.emv", e);
            result.failed("emv_param.emv: " + e.getMessage());
            return null;
        }
    }

    private static void applyClssXml(byte[] xml, TerminalWideValues terminalWide,
            EmvParamUpdateResult result) {
        try {
            ClssXmlParamParser.Result parsed =
                    ClssXmlParamParser.parse(new ByteArrayInputStream(xml), terminalWide);
            result.sectionsIn("clss_param.clss", parsed.sections);
            EmvParamService service = new EmvParamService();

            if (parsed.payPass != null && notEmpty(parsed.payPass.getAid())) {
                replace("PayPass", parsed.payPass.getAid().size(), result,
                        () -> PaypassAidDbHelper.getInstance().deleteAll()
                                && service.insertPaypassParam(parsed.payPass));
            }
            if (parsed.payWave != null && notEmpty(parsed.payWave.getAid())) {
                replace("PayWave", parsed.payWave.getAid().size(), result,
                        () -> PaywaveAidDbHelper.getInstance().deleteAll()
                                && service.insertPaywaveParam(parsed.payWave));
            }
            if (parsed.amex != null && notEmpty(parsed.amex.getAid())) {
                replace("Amex", parsed.amex.getAid().size(), result,
                        () -> AmexAidDbHelper.getInstance().deleteAll()
                                && AmexDrlDbHelper.getInstance().deleteAll()
                                && service.insertAmexParam(parsed.amex));
            }
            removeKernelsNotInPackage(parsed, result);
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to parse/apply clss_param.clss", e);
            result.failed("clss_param.clss: " + e.getMessage());
        }
    }

    /**
     * The contactless package is the whole truth: a kernel it doesn't bring is deleted, never
     * left on the bundled JSON seed or an older download. That includes every kernel this
     * parser has no mapping for yet (DPAS, EFT, JCB, Mir, PBOC, Pure, RuPay) — the result
     * lists the file's own sections, so a section the host sent but nothing parsed shows up.
     * A deleted kernel registers no AID with the entry point (ClssEntryAddAid skips an empty
     * list), so its cards aren't selected contactless.
     */
    private static void removeKernelsNotInPackage(ClssXmlParamParser.Result parsed,
            EmvParamUpdateResult result) {
        if (parsed.payPass == null || !notEmpty(parsed.payPass.getAid())) {
            remove("PayPass", result, PaypassAidDbHelper.getInstance());
        }
        if (parsed.payWave == null || !notEmpty(parsed.payWave.getAid())) {
            remove("PayWave", result, PaywaveAidDbHelper.getInstance());
        }
        if (parsed.amex == null || !notEmpty(parsed.amex.getAid())) {
            remove("Amex", result, AmexAidDbHelper.getInstance(), AmexDrlDbHelper.getInstance());
        }
        remove("DPAS (no parser)", result, DpasAidDbHelper.getInstance());
        remove("EFT (no parser)", result, EFTAidDbHelper.getInstance());
        remove("JCB (no parser)", result, JcbAidDbHelper.getInstance());
        remove("Mir (no parser)", result, MirAidDbHelper.getInstance());
        remove("PBOC (no parser)", result, PBOCAidDbHelper.getInstance());
        remove("Pure (no parser)", result, PureAidDbHelper.getInstance());
        remove("RuPay (no parser)", result, RupayAidDbHelper.getInstance());
    }

    /**
     * Replaces one section — its deletes and inserts — in one database transaction: all or
     * nothing. A failed step rolls the deletes back, so the table keeps what it had.
     */
    private static void replace(String section, int rows, EmvParamUpdateResult result,
            Callable<Boolean> deleteAndInsert) {
        try {
            DaoManager.getInstance().getDaoSession().callInTx(() -> {
                if (!deleteAndInsert.call()) {
                    throw new IllegalStateException(section + " insert failed");
                }
                return null;
            });
            result.applied(section, rows);
        } catch (Exception e) {
            LogUtils.e(TAG, section + ": not applied, the table keeps what it had", e);
            result.failed(section + " insert failed — kept the previous values");
        }
    }

    /** Deletes the section's tables; reported only when there were rows to delete. */
    private static void remove(String kernel, EmvParamUpdateResult result,
            BaseDaoHelper<?>... tables) {
        int rows = 0;
        for (BaseDaoHelper<?> table : tables) {
            rows += table.loadAll().size();
        }
        if (rows == 0) {
            return;
        }
        for (BaseDaoHelper<?> table : tables) {
            if (!table.deleteAll()) {
                result.failed(kernel + ": delete failed");
                return;
            }
        }
        LogUtils.i(TAG, kernel + ": not in the package — deleted " + rows + " rows");
        result.removed(kernel, rows);
    }

    /**
     * Debug builds only: keeps the last downloaded parameter files in the app's external files
     * folder (Android/data/&lt;package&gt;/files/emv_param/), to compare what the host sent with
     * what was stored — e.g. {@code adb pull /sdcard/Android/data/<package>/files/emv_param}.
     * Parameters only, no card or key data.
     */
    private static void saveForDiagnosis(String name, @Nullable byte[] content) {
        if (!BuildConfig.DEBUG || content == null) {
            return;
        }
        try {
            File dir = diagnosisDir();
            if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                return;
            }
            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(content);
            }
            LogUtils.i(TAG, "Saved downloaded " + name + " to " + file.getAbsolutePath());
        } catch (Exception e) {
            LogUtils.w(TAG, "Couldn't save " + name + " for diagnosis: " + e.getMessage());
        }
    }

    /** Where saveForDiagnosis keeps the last downloaded files; null when storage is unavailable. */
    @Nullable
    public static File diagnosisDir() {
        return BaseApplication.getAppContext().getExternalFilesDir("emv_param");
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
