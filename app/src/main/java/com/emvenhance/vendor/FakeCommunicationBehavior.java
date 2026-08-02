package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.emvenhance.core.CommunicationBehavior;
import com.pax.commonlib.utils.LogUtils;

public class FakeCommunicationBehavior implements CommunicationBehavior {

    private static final String TAG = "FakeComm";

    @Override
    public boolean authorize(@Nullable String pan, long amountMinor) {
        LogUtils.i(TAG, "authorize amount=" + amountMinor + " -> approved");
        return true;
    }
}
