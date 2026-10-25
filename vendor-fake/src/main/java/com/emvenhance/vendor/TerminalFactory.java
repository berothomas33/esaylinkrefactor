package com.emvenhance.vendor;

import android.app.Application;
import androidx.annotation.NonNull;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.vendor.fake.FakeTerminal;

public final class TerminalFactory {

    private TerminalFactory() {
    }

    @NonNull
    public static PosTerminal create(Application application) {
        return new FakeTerminal();
    }
}
