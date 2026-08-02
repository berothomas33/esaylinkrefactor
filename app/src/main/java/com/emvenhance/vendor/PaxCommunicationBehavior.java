package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.emvenhance.core.CommunicationBehavior;
import com.pax.commonlib.utils.LogUtils;

public class PaxCommunicationBehavior implements CommunicationBehavior {

    private static final String TAG = "PaxComm";

    @Override
    public boolean authorize(@Nullable String pan, long amountMinor) {
        // TODO real acquirer call; declines until the host is wired up
        LogUtils.i(TAG, "authorize amount=" + amountMinor + " -> declined, host not wired");
        return false;
    }
}
