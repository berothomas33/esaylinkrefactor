package com.emvenhance.vendor;

import androidx.annotation.NonNull;
import com.emvenhance.core.PosTerminal;
import com.emvenhance.vendor.fake.FakeTerminal;
import com.emvenhance.vendor.ingenico.IngenicoTerminal;
import com.emvenhance.vendor.pax.PaxTerminal;

/**
 * Creates the vendor {@link PosTerminal}. This is the only place that knows vendor class names.
 *
 * <p>UI and core never import Pax/Ingenico/Fake types — only {@link PosTerminal}.
 */
public final class TerminalFactory {

    private TerminalFactory() {
    }

    @NonNull
    public static PosTerminal create(@NonNull String vendor) {
        if ("PAX".equalsIgnoreCase(vendor)) {
            return new PaxTerminal();
        }
        if ("INGENICO".equalsIgnoreCase(vendor)) {
            return new IngenicoTerminal();
        }
        return new FakeTerminal();
    }

    /** PAX needs Neptune DAL / WMRouter kernel registry init. */
    public static boolean needsPaxRuntime(@NonNull String vendor) {
        return "PAX".equalsIgnoreCase(vendor);
    }
}
