package com.emvenhance.core.host;

import android.util.Log;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/** Simple host / printer stubs for demos and unfinished wiring. */
public final class HostDefaults {

    private HostDefaults() {
    }

    public static CommunicationBehavior approveAlways() {
        return config -> Single.fromCallable(() -> {
            Thread.sleep(400);
            return AuthResult.approved("123456", "00", null);
        });
    }

    public static CommunicationBehavior declineUntilWired() {
        return config -> Single.just(AuthResult.declined("96", "Host not wired"));
    }

    public static PrinterBehavior logPrinter(String tag) {
        return lines -> Completable.fromAction(() -> {
            for (String line : lines) {
                Log.i(tag, line);
            }
        });
    }
}
