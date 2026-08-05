package com.emvenhance.core.flow;

import androidx.annotation.NonNull;

/**
 * Vendor cleanup that must run exactly once, however the transaction ended — approved,
 * declined, cancelled or crashed.
 *
 * <p>This is where reader close and kernel release belong. Today that logic sits in a
 * {@code finally} block duplicated inside each vendor's EMV class; hoisting it here means a
 * new vendor cannot forget it.
 */
@FunctionalInterface
public interface FlowFinalizer {

    void onFlowFinished(@NonNull EmvContext ctx, @NonNull TransactionResult result);

    @NonNull
    static FlowFinalizer noop() {
        return (ctx, result) -> {
        };
    }
}
