package com.emvenhance.emvflow.runtime;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.emvflow.device.EmvDeviceImpl;
import com.pax.commonlib.application.AppActivityLifecycleCallbacks;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.emvlib.utils.EmvUtils;
import com.pax.jemv.device.DeviceManager;
import com.pax.neptunelite.api.NeptuneLiteUser;
import com.pax.poslib.gl.convert.ConvertHelper;
import com.pax.poslib.gl.impl.GL;
import com.pax.poslib.model.ModelInfo;
import com.pax.poslib.neptune.Sdk;

/**
 * Runtime host for :emvflow — owns PAX Neptune + EMV kernel bootstrap.
 *
 * <p>Call {@link #init(Application)} from the PAX {@code Application} (via
 * {@code TerminalFactory.create}) before any EMV work. The sequence matches the PAX
 * EDC demo plus the ApiMap_F kernel guide:
 *
 * <ol>
 *   <li>{@code ConvertHelper.init} and activity-lifecycle registration.</li>
 *   <li>Neptune DAL via {@code NeptuneLiteUser.getDal(Application)} — the documented
 *       Neptune Lite entry point. {@link Sdk} is then synced so PedHelper / ModelInfo
 *       see the same {@link IDAL}.</li>
 *   <li>{@code GL.init} and {@code ModelInfo.buildCache}.</li>
 *   <li>{@link EmvUtils#loadLibrary()} — {@code F_DEVICE}/{@code F_EMV} JNI libs
 *       required before {@code EMVCoreInit}.</li>
 *   <li>{@code DeviceManager.setIDevice} — kernel ICC/PIN/time callbacks go through
 *       {@link EmvDeviceImpl}, which needs DAL.</li>
 * </ol>
 *
 * <p>{@code EMVCoreInit} / {@code EMVSetCallback} stay per-transaction in
 * {@code ContactProcess.preTransProcess}, as the kernel guide requires them before
 * each transaction.
 *
 * <p>{@link #getDal()} retries acquisition if the first attempt failed (Neptune
 * copies {@code nepcore.dex} and can miss on a cold start).
 */
public final class EmvFlowRuntime {
    private static final String TAG = "EmvFlowRuntime";

    private static Application app;
    private static volatile IDAL dal;
    private static volatile boolean lifecycleRegistered;
    private static volatile boolean convertInitialized;
    private static volatile boolean glInitialized;
    private static volatile boolean librariesLoaded;

    private EmvFlowRuntime() {
    }

    public static synchronized void init(@NonNull Application application) {
        app = application;
        if (!convertInitialized) {
            ConvertHelper.init(true);
            convertInitialized = true;
        }
        if (!lifecycleRegistered) {
            application.registerActivityLifecycleCallbacks(new AppActivityLifecycleCallbacks());
            lifecycleRegistered = true;
        }
        acquireDal();
        if (!glInitialized) {
            try {
                GL.init(application);
                ModelInfo.getInstance().buildCache();
                glInitialized = true;
            } catch (UnsatisfiedLinkError | Exception e) {
                LogUtils.e(TAG, "GL/ModelInfo init failed", e);
            }
        }
        loadEmvNativeLibraries();
        bindEmvDevice();
        if (dal != null) {
            LogUtils.i(TAG, "EmvFlowRuntime initialized");
        } else {
            LogUtils.e(TAG, "EmvFlowRuntime init finished without Neptune DAL");
        }
    }

    @NonNull
    public static synchronized Application getApp() {
        if (app == null) {
            throw new IllegalStateException("EmvFlowRuntime.init() not called");
        }
        return app;
    }

    /**
     * Neptune {@link IDAL}, retrying acquisition if {@link #init} ran before the
     * PayDroid DAL proxy was ready.
     */
    @Nullable
    public static synchronized IDAL getDal() {
        if (dal == null && app != null) {
            acquireDal();
        }
        return dal;
    }

    /** {@code true} when Neptune DAL is available for EMV / card search. */
    public static boolean isReady() {
        return getDal() != null;
    }

    private static void acquireDal() {
        if (dal != null || app == null) {
            return;
        }
        try {
            dal = NeptuneLiteUser.getInstance().getDal(app);
        } catch (UnsatisfiedLinkError | Exception e) {
            LogUtils.e(TAG, "NeptuneLiteUser.getDal failed", e);
        }
        if (dal == null) {
            try {
                dal = Sdk.getInstance().getDal(app);
            } catch (UnsatisfiedLinkError | Exception e) {
                LogUtils.e(TAG, "Sdk.getDal failed", e);
            }
        } else {
            // PedHelper / DefaultModel still read DAL through Sdk.
            try {
                Sdk.getInstance().getDal(app);
            } catch (UnsatisfiedLinkError | Exception e) {
                LogUtils.e(TAG, "Sdk DAL sync failed", e);
            }
        }
    }

    private static void loadEmvNativeLibraries() {
        if (librariesLoaded) {
            return;
        }
        try {
            EmvUtils.loadLibrary();
            librariesLoaded = true;
            LogUtils.i(TAG, "EMV native libraries loaded");
        } catch (UnsatisfiedLinkError | Exception e) {
            LogUtils.e(TAG, "EMV native library load failed", e);
        }
    }

    private static void bindEmvDevice() {
        if (dal == null) {
            return;
        }
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
        } catch (UnsatisfiedLinkError | Exception e) {
            LogUtils.e(TAG, "DeviceManager.setIDevice failed", e);
        }
    }
}
