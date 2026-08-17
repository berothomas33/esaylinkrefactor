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

    /** PAX needs the Neptune DAL / WMRouter kernel registry brought up before anything else. */
    @NonNull
    public static PosTerminal create(Application application) {
        EmvFlowRuntime.init(application);
        return new PaxTerminal();
    }
}
