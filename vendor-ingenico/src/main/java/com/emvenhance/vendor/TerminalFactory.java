package com.emvenhance.vendor;

import android.app.Application;
import androidx.annotation.NonNull;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.vendor.ingenico.IngenicoTerminal;

public final class TerminalFactory {

    private TerminalFactory() {
    }

    @NonNull
    public static PosTerminal create(Application application) {
        return new IngenicoTerminal();
    }
}
