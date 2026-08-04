package com.emvenhance.vendor;

import android.app.Application;
import androidx.annotation.Nullable;
import com.pax.commonlib.convert.ConvertHelper;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.poslib.gl.GL;
import com.pax.poslib.model.ModelInfo;
import com.pax.poslib.neptune.Sdk;

/**
 * PAX-specific runtime initialization — acquires the Neptune DAL and prepares
 * model info, GL rendering, and logging. Only called once from
 * {@link com.emvenhance.EmvEnhanceApp#onCreate} when the vendor is PAX.
 *
 * <p>Replaces the former {@code EmvFlowRuntime} that lived in the now-deleted
 * {@code :emvflow} module.
 */
public final class PaxRuntime {

    private static final String TAG = "PaxRuntime";

    @Nullable
    private static IDAL dal;

    private PaxRuntime() { }

    /**
     * Initializes PAX platform services. Must be called before any terminal or
     * EMV kernel operation.
     */
    public static void init(Application app) {
        try {
            ConvertHelper.init();
            dal = Sdk.getInstance().getDal(app);
            GL.init(app);
            ModelInfo.init();
            LogUtils.i(TAG, "PAX runtime initialized, model=" + ModelInfo.getModel());
        } catch (Exception e) {
            LogUtils.e(TAG, "PAX runtime init failed", e);
        }
    }

    /**
     * Returns the Neptune DAL, or {@code null} if {@link #init} has not been called
     * or initialization failed.
     */
    @Nullable
    public static IDAL getDal() {
        return dal;
    }
}
