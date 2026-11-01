package com.emvenhance.vendor.pax;

import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.entity.EPiccType;

final class PaxReaders {

    private static final String TAG = "PaxReaders";

    private PaxReaders() { }

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
