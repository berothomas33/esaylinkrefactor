package com.emvenhance.emvflow.runtime;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pax.commonlib.application.AppActivityLifecycleCallbacks;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.poslib.gl.convert.ConvertHelper;
import com.pax.poslib.gl.impl.GL;
import com.pax.poslib.model.ModelInfo;
import com.pax.poslib.neptune.Sdk;

/**
 * Runtime host for :emvflow — owns PAX Neptune SDK initialization.
 *
 * <p>Follows the same synchronous init pattern as the PAX demo app:
 * DAL, GL, ConvertHelper, and ModelInfo are initialized in sequence.
 * The entire block is wrapped in a try/catch because {@code Sdk.getInstance()}
 * loads {@code libnativetouchevent.so} — a system library only present on
 * real PAX hardware — so {@link UnsatisfiedLinkError} is expected on
 * emulators and non-PAX devices.
 */
public final class EmvFlowRuntime {
    private static final String TAG = "EmvFlowRuntime";

    private static Application app;
    private static volatile IDAL dal;

    private EmvFlowRuntime() {
    }

    public static void init(@NonNull Application application) {
        app = application;
        ConvertHelper.init(true);
        application.registerActivityLifecycleCallbacks(new AppActivityLifecycleCallbacks());
        try {
            dal = Sdk.getInstance().getDal(application);
            GL.init(application);
            ModelInfo.getInstance().buildCache();
            LogUtils.i(TAG, "EmvFlowRuntime initialized");
        } catch (UnsatisfiedLinkError | Exception e) {
            LogUtils.e(TAG, "init failed (expected off a PAX device)", e);
        }
    }

    @NonNull
    public static Application getApp() {
        if (app == null) {
            throw new IllegalStateException("EmvFlowRuntime.init() not called");
        }
        return app;
    }

    @Nullable
    public static IDAL getDal() {
        return dal;
    }
}
