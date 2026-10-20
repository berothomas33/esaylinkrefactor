package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.engine.EmvEngine

/**
 * What [EmvEngine] drives on a vendor's EMV kernel — and all it can call.
 *
 * Lifecycle: [prepare] → (PosTerminal search) → [start]; [dispose] once, when the terminal
 * itself goes away. Cancelling is the terminal's: its one flag, read through
 * [EmvEngine.isCancelled].
 *
 * A vendor doesn't implement this directly: it extends [AbstractEmvBehavior], which implements
 * it and declares the EMV step methods (`onApplicationSelection`, …) every vendor writes. Those
 * are `protected` there, not part of this contract, so nothing holding an `EmvBehavior` can run
 * a step: steps run only through [AbstractEmvBehavior.goToStep]. Tests stub this interface to
 * drive the engine and the terminal with no EMV behind it.
 *
 * All of it runs on the transaction thread and blocks there — never `suspend`: the native
 * kernel calls back into it and needs every answer on the same call stack.
 *
 * EMV only — no host / printer logic here.
 */
interface EmvBehavior {

    fun prepare(engine: EmvEngine, config: TransactionConfig): Boolean

    fun start(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    /**
     * Releases what the behavior holds beyond one transaction — [AbstractEmvBehavior]'s step
     * handler. Called by [EmvEngine.dispose] when its [PosTerminal] is disposed.
     */
    fun dispose() {}
}
