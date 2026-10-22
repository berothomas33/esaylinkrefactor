package com.emvenhance.vendor;

import android.app.Application;
import androidx.annotation.NonNull;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.vendor.ingenico.IngenicoTerminal;

/**
 * Ingenico flavor's factory — the only place in this flavor that knows
 * {@link IngenicoTerminal}'s name.
 *
 * <p>UI and core never import vendor types — only {@link PosTerminal}.
 */
public final class TerminalFactory {

    private TerminalFactory() {
    }

    @NonNull
    public static PosTerminal create(Application application) {
        return new IngenicoTerminal();
    }
}
