package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.engine.EmvEngine

/**
 * What [PosTerminal] drives on a vendor's EMV kernel — and all it can call.
 *
 * Lifecycle: [prepare] → (PosTerminal search) → [start], once per attempt, each handed the
 * terminal's [EmvEngine] and the attempt's [PosTerminal.Transaction]; [dispose] once, when the
 * terminal itself goes away. A cancel isn't a call into the behavior: it reads that transaction
 * ([AbstractEmvBehavior.isCancelled]) at each step.
 *
 * A vendor doesn't implement this directly: it extends [AbstractEmvBehavior], which implements
 * it and declares the EMV step methods (`onApplicationSelection`, …) every vendor writes. Those
 * are `protected` there, not part of this contract, so nothing holding an `EmvBehavior` can run
 * a step: steps run only through [AbstractEmvBehavior.goToStep]. Tests stub this interface to
 * drive the terminal with no EMV behind it.
 *
 * All of it runs on the transaction thread and blocks there — never `suspend`: the native
 * kernel calls back into it and needs every answer on the same call stack.
 *
 * EMV only — no host / printer logic here.
 */
interface EmvBehavior {

    /**
     * Terminal initialization for one attempt.
     *
     * @param transaction where the behavior reports its steps (CARD_READ, APPROVED…), and what it
     *      asks whether a step may still run
     * @return `false` if it failed or the transaction ended meanwhile
     */
    fun prepare(engine: EmvEngine, config: TransactionConfig, transaction: PosTerminal.Transaction): Boolean

    /** EMV on the card the search found, for the same attempt as [prepare]. */
    fun start(engine: EmvEngine, config: TransactionConfig, card: CardPresence, transaction: PosTerminal.Transaction)

    /**
     * Releases what the behavior holds beyond one transaction — [AbstractEmvBehavior]'s step
     * handler. Called by [PosTerminal.dispose].
     */
    fun dispose() {}
}
