package com.emvenhance.vendor;

import com.emvenhance.core.AuthResult;
import com.emvenhance.core.CommunicationBehavior;
import com.emvenhance.core.TransactionConfig;
import com.pax.commonlib.utils.LogUtils;
import io.reactivex.rxjava3.core.Single;

public class PaxCommunicationBehavior implements CommunicationBehavior {

    private static final String TAG = "PaxComm";

    @Override
    public Single<AuthResult> authorize(TransactionConfig config) {
        return Single.fromCallable(() -> {
            // TODO real acquirer call; declines until the host is wired up
            LogUtils.i(TAG, "authorize amount=" + config.getAmountMinor()
                    + " -> declined, host not wired");
            return AuthResult.declined("96", "Host not wired");
        });
    }
}
