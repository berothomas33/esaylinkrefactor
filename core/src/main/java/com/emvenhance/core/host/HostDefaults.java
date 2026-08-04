package com.emvenhance.core.host;

import android.util.Log;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Shared host / printer stubs for demos and unfinished vendor wiring.
 */
public final class HostDefaults {

    private HostDefaults() {
    }

    /** Always approves after a short delay — phone / UI demos. */
    public static CommunicationBehavior approveAlways() {
        return config -> Single.fromCallable(() -> {
            Thread.sleep(400);
            return AuthResult.approved("123456", "00", null);
        });
    }

    /** Declines until a real host is wired. */
    public static CommunicationBehavior declineUntilWired() {
        return config -> Single.just(AuthResult.declined("96", "Host not wired"));
    }

    /** Logs receipt lines; no hardware. */
    public static PrinterBehavior logPrinter(String tag) {
        return lines -> Completable.fromAction(() -> {
            for (String line : lines) {
                Log.i(tag, line);
            }
        });
    }
}
