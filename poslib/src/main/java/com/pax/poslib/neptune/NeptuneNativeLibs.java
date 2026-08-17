package com.pax.poslib.neptune;

import com.pax.commonlib.utils.LogUtils;

/**
 * Native libraries required by Neptune Lite DAL (loaded via {@code nepcore.dex}).
 *
 * <p>{@code DeviceConfig}, {@code DCL}, and {@code HDSD} must be on the
 * application {@code nativeLibraryDir} <em>and</em> loaded before the first
 * {@code dal.getSys()}/{@code getPed()}/{@code getIcc()}/{@code getPicc()} call.
 * {@code DeviceConfig.<clinit>} uses the nepcore {@code DexClassLoader}; if
 * {@code libDeviceConfig.so} is missing that class is poisoned for the process
 * ({@link NoClassDefFoundError} on every later PED/ICC/PICC use).
 */
public final class NeptuneNativeLibs {
    private static final String TAG = "NeptuneNativeLibs";

    /** Load order for DAL hardware (PED / ICC / PICC / mag). */
    public static final String[] DAL_LIBS = {
            "DeviceConfig",
            "DCL",
            "HDSD",
    };

    private static volatile boolean loaded;

    private NeptuneNativeLibs() {
    }

    public static void loadDalLibraries() {
        if (loaded) {
            return;
        }
        for (String lib : DAL_LIBS) {
            try {
                System.loadLibrary(lib);
                LogUtils.i(TAG, "loaded " + lib);
            } catch (UnsatisfiedLinkError e) {
                LogUtils.e(TAG, "System.loadLibrary(" + lib + ") failed", e);
            }
        }
        loaded = true;
    }
}
