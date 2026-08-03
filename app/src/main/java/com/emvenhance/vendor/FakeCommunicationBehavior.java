package com.emvenhance.vendor;

import android.util.Log;
import com.emvenhance.core.AuthResult;
import com.emvenhance.core.CommunicationBehavior;
import com.emvenhance.core.TransactionConfig;
import io.reactivex.rxjava3.core.Single;

public class FakeCommunicationBehavior implements CommunicationBehavior {

    private static final String TAG = "FakeComm";

    @Override
    public Single<AuthResult> authorize(TransactionConfig config) {
        return Single.fromCallable(() -> {
            Log.i(TAG, "authorize amount=" + config.getAmountMinor() + " -> approved");
            // Simulate network delay
            Thread.sleep(500);
            return AuthResult.approved("123456", "00", null);
        });
    }
}
