package com.emvenhance.vendor;

import android.app.Application;
import androidx.annotation.NonNull;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.emvenhance.vendor.pax.PaxTerminal;

public final class TerminalFactory {

    private TerminalFactory() {
    }

    @NonNull
    public static PosTerminal create(Application application) {
        EmvFlowRuntime.init(application);
        return new PaxTerminal();
    }
}
