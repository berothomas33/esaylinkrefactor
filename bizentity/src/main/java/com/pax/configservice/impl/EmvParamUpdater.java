package com.pax.configservice.impl;

import androidx.annotation.Nullable;
import com.pax.bizentity.BuildConfig;
import com.pax.bizentity.db.helper.AmexAidDbHelper;
import com.pax.bizentity.db.helper.AmexDrlDbHelper;
import com.pax.bizentity.db.helper.BaseDaoHelper;
import com.pax.bizentity.db.helper.CapkRevokeDbHelper;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Applies a downloaded EMV parameter package (a zip containing {@code emv_param.emv} /
 * {@code clss_param.clss}) to the same GreenDAO tables {@link ConfigInit} seeds at first boot
 * from the bundled JSON assets — making the EMV/CLSS kernel configuration updatable at runtime
 * instead of fixed at build time.
 *
 * <p>Unlike {@code ConfigInit}'s one-time seed, this is meant to be re-run whenever a fresh
 * package is downloaded, so each section this actually received a fresh (non-empty) list for
 * gets its table cleared before the fresh rows go in — {@link EmvParamService}'s
 * {@code insertXxx()} methods use {@code insertOrReplace()} keyed by autoincrement id, which
 * would otherwise just accumulate duplicate/stale rows (and break
 * {@code EmvAidDbHelper#findAID}'s assumption of a unique {@code aid} column) on a second apply.
 * A section the package didn't include (or that parsed empty) is left untouched rather than
 * wiped, so a partial package — or a failed download that never reaches this at all — can't
 * silently delete data it didn't come to replace; the bundled JSON assets (or whatever the last
 * successful apply left in place) always remain the fallback.
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

        List<EmvAid> contactAids = null;
        if (emvXml != null) {
            contactAids = applyEmvXml(emvXml, result);
        } else {
            LogUtils.w(TAG, "EMV param package had no *.emv entry — contact AID/CAPK unchanged");
        }
        if (clssXml != null) {
            // Terminal-wide values (Terminal Type, Additional Terminal Capabilities, Security
            // Capability) for contactless AIDs whose scheme block lacks them: from this
            // package's contact XML, else from the contact AIDs already stored.
            if (contactAids == null) {
                contactAids = GreendaoHelper.getEmvAidHelper().loadAll();
            }
            applyClssXml(clssXml, new TerminalWideValues(contactAids), result);
        } else {
            LogUtils.w(TAG, "EMV param package had no *.clss entry — CLSS params unchanged");
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
                GreendaoHelper.getEmvAidHelper().deleteAll();
                if (service.insertEmvAid(parsed.aids)) {
                    result.applied("contact AID", parsed.aids.size());
                } else {
                    result.failed("contact AID insert failed");
                }
            }

            List<?> capkList = parsed.capk.getCapkList();
            if (capkList != null && !capkList.isEmpty()) {
                GreendaoHelper.getEmvCapkHelper().deleteAll();
                CapkRevokeDbHelper.getInstance().deleteAll();
                if (service.insertEmvCapk(parsed.capk)) {
                    result.applied("CAPK", capkList.size());
                } else {
                    result.failed("CAPK insert failed");
                }
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
                PaypassAidDbHelper.getInstance().deleteAll();
                if (service.insertPaypassParam(parsed.payPass)) {
                    result.applied("PayPass", parsed.payPass.getAid().size());
                } else {
                    result.failed("PayPass insert failed");
                }
            }
            if (parsed.payWave != null && notEmpty(parsed.payWave.getAid())) {
                PaywaveAidDbHelper.getInstance().deleteAll();
                if (service.insertPaywaveParam(parsed.payWave)) {
                    result.applied("PayWave", parsed.payWave.getAid().size());
                } else {
                    result.failed("PayWave insert failed");
                }
            }
            if (parsed.amex != null && notEmpty(parsed.amex.getAid())) {
                AmexAidDbHelper.getInstance().deleteAll();
                AmexDrlDbHelper.getInstance().deleteAll();
                if (service.insertAmexParam(parsed.amex)) {
                    result.applied("Amex", parsed.amex.getAid().size());
                } else {
                    result.failed("Amex insert failed");
                }
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

    /** Deletes the kernel's tables; reported only when there were rows to delete. */
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
