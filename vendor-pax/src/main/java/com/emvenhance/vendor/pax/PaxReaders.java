package com.emvenhance.vendor.pax;

import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.entity.EPiccType;

/** The PAX card readers (mag, ICC, PICC), closed at the end of an attempt's EMV. */
final class PaxReaders {

    private static final String TAG = "PaxReaders";

    private PaxReaders() { }

    /**
     * Closes mag/icc/picc independently — each in its own try/catch — so one reader's close
     * failing (e.g. a reader already in a bad state) can't skip the rest. The previous
     * single-try version stopped at the first exception, silently leaving whatever came after it
     * open; that's the likely root cause of a "reader already open" failure on the next attempt.
     */
    static void close(boolean contactless) {
        if (EmvFlowRuntime.getDal() == null) {
            return;
        }
        try {
            EmvFlowRuntime.getDal().getMag().close();
        } catch (Exception e) {
            LogUtils.e(TAG, "close mag reader failed", e);
        }
        try {
            EmvFlowRuntime.getDal().getIcc().close((byte) 0);
        } catch (Exception e) {
            LogUtils.e(TAG, "close icc reader failed", e);
        }
        try {
            EmvFlowRuntime.getDal().getPicc(EPiccType.INTERNAL).close();
        } catch (Exception e) {
            LogUtils.e(TAG, "close internal picc reader failed", e);
        }
        if (contactless) {
            try {
                EmvFlowRuntime.getDal().getPicc(EPiccType.EXTERNAL).close();
            } catch (Exception e) {
                LogUtils.e(TAG, "close external picc reader failed", e);
            }
        }
    }
}
