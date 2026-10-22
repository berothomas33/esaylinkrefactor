package com.emvenhance.vendor;

import android.app.Application;
import androidx.annotation.NonNull;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.emvenhance.vendor.pax.PaxTerminal;

/**
 * PAX flavor's factory — the only place in this flavor that knows {@link PaxTerminal}'s name.
 *
 * <p>UI and core never import vendor types — only {@link PosTerminal}.
 */
public final class TerminalFactory {

    private TerminalFactory() {
    }

    /**
     * PAX needs Neptune DAL + EMV JNI libs before any kernel call.
     * {@link EmvFlowRuntime#init} is the documented bootstrap (NeptuneLiteUser.getDal,
     * then {@code EmvUtils.loadLibrary}, then {@code DeviceManager.setIDevice}).
     */
    @NonNull
    public static PosTerminal create(Application application) {
        EmvFlowRuntime.init(application);
        return new PaxTerminal();
    }
}
