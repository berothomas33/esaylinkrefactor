package com.emvenhance.core.terminal;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;

/**
 * What {@link EmvEngine} drives on a vendor's EMV kernel — and all it can call.
 *
 * <p>Lifecycle: {@link #prepare} → (PosTerminal search) → {@link #start} → {@link #cancel}.
 *
 * <p>A vendor doesn't implement this directly: it extends {@link AbstractEmvBehavior}, which
 * implements it and declares the EMV step methods ({@code onApplicationSelection}, …) every
 * vendor writes. Those are {@code protected} there, not part of this contract, so nothing
 * holding an {@code EmvBehavior} can run a step: steps run only through
 * {@link AbstractEmvBehavior#goToStep}. Tests stub this interface to drive the engine and the
 * terminal with no EMV behind it.
 *
 * <p>EMV only — no host / printer logic here.
 */
public interface EmvBehavior {

    boolean prepare(EmvEngine engine, TransactionConfig config);

    void start(EmvEngine engine, TransactionConfig config, CardPresence card);

    void cancel();
}
