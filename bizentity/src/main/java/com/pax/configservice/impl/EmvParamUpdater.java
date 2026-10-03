package com.pax.configservice.impl;

import androidx.annotation.Nullable;
import com.pax.bizentity.BuildConfig;
import com.pax.bizentity.db.helper.AmexAidDbHelper;
import com.pax.bizentity.db.helper.AmexDrlDbHelper;
import com.pax.bizentity.db.helper.CapkRevokeDbHelper;
import com.pax.bizentity.db.helper.GreendaoHelper;
import com.pax.bizentity.db.helper.PaypassAidDbHelper;
import com.pax.bizentity.db.helper.PaywaveAidDbHelper;
import com.pax.bizentity.db.helper.PaywaveFloorLimitDbHelper;
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

        saveForDiagnosis("emv_param.emv", emvXml);
        saveForDiagnosis("clss_param.clss", clssXml);

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
                PaywaveFloorLimitDbHelper.getInstance().deleteAll();
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
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to parse/apply clss_param.clss", e);
            result.failed("clss_param.clss: " + e.getMessage());
        }
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
            File dir = BaseApplication.getAppContext().getExternalFilesDir("emv_param");
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
