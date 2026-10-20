package com.emvenhance.core.host

import com.emvenhance.core.card.TransactionConfig

/**
 * Acquirer / host authorize port. Owned by [com.emvenhance.core.terminal.PosTerminal].
 *
 * A `suspend` function: the HTTP calls behind it suspend instead of blocking, and cancelling the
 * caller (the terminal's online timeout) cancels the request. `PosTerminal.authorize` is the one
 * caller — on the transaction thread, through `runBlocking`.
 */
fun interface CommunicationBehavior {

    suspend fun authorize(config: TransactionConfig): AuthResult
}
