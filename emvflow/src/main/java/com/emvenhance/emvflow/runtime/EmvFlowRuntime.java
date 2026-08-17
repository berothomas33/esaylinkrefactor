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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Runtime host for :emvflow — owns DAL initialization.
 *
 * <p>Does <b>not</b> call {@code ServiceLoader.lazyInit()}: WMRouter's Gradle plugin (the
 * Transform that merges every module's annotationProcessor-generated registrations into one
 * combined registry class) can't run on this project's AGP version — it calls the legacy
 * Transform API, which AGP removed in 8.0+, confirmed by trying it. Without that plugin,
 * {@code lazyInit()} can only ever throw {@code ClassNotFoundException} looking for a registry
 * class that never gets generated, so there's no reason to call it — it bought nothing and only
 * added a scary exception to every launch. Direct construction has already replaced
 * {@code Router.getService()} everywhere that's safe to touch (see git history); the remaining
 * real Router usages are inside PAX's own kernel/service modules and were already broken by
 * this same gap before this change.
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
