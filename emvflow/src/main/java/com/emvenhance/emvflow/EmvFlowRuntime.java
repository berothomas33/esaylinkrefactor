package com.emvenhance.emvflow;

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
import com.sankuai.waimai.router.service.ServiceLoader;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Runtime host for :emvflow — owns DAL and Router initialization.
 *
 * <p>Background work runs on RxJava's IO scheduler.
 */
public final class EmvFlowRuntime {
    private static final String TAG = "EmvFlowRuntime";

    private static Application app;
    private static volatile IDAL dal;

    private EmvFlowRuntime() {
    }

    public static void init(@NonNull Application application) {
        app = application;
        ServiceLoader.lazyInit();
        Completable.fromAction(() -> {
                    ConvertHelper.init(true);
                    application.registerActivityLifecycleCallbacks(
                            new AppActivityLifecycleCallbacks());
                    dal = Sdk.getInstance().getDal(application);
                    GL.init(application);
                    ModelInfo.getInstance().buildCache();
                })
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> LogUtils.i(TAG, "EmvFlowRuntime initialized"),
                        t -> LogUtils.e(TAG, "init failed (expected off a PAX device)", t));
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
