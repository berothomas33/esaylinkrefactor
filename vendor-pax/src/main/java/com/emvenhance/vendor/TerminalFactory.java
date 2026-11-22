package com.emvenhance.vendor;

import android.app.Application;
import androidx.annotation.NonNull;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.vendor.pax.PaxMasterKey;
import com.emvenhance.vendor.pax.PaxTerminal;
import com.pax.commonlib.utils.LogUtils;

public final class TerminalFactory {

    private TerminalFactory() {
    }

    @NonNull
    public static PosTerminal create(Application application) {
        EmvFlowRuntime.init(application);
        try {
            PaxMasterKey.generatePOSMasterKey(application);
        } catch (Exception e) {
            LogUtils.e("TerminalFactory", "POS master key not written: " + e.getClass().getSimpleName());
        }
        OnboardingState.useMasterKey(new PaxMasterKey());
        return new PaxTerminal();
    }
}
