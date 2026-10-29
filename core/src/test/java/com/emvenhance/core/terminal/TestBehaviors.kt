package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.engine.EmvEngine
import java.lang.reflect.Proxy

/**
 * A vendor whose every step method does nothing — tests override only the steps they play.
 * Not part of the main code: [AbstractEmvBehavior] keeps every step abstract on purpose.
 */
internal abstract class NoOpStepsBehavior : AbstractEmvBehavior() {
    override fun readIccTag(tag: Int): ByteArray? = null
    override fun onTerminalInitialization(config: TransactionConfig) {}
    override fun onApplicationSelection(config: TransactionConfig, card: CardPresence) {}
    override fun onWaitApplicationSelection(config: TransactionConfig, card: CardPresence) {}
    override fun onFinalApplicationSelection(config: TransactionConfig, card: CardPresence) {}
    override fun onReadApplicationData(config: TransactionConfig, card: CardPresence) {}
    override fun onSetTransactionData(config: TransactionConfig, card: CardPresence) {}
    override fun onOfflineDataAuthentication(config: TransactionConfig, card: CardPresence) {}
    override fun onProcessRestrictions(config: TransactionConfig, card: CardPresence) {}
    override fun onCardholderVerification(config: TransactionConfig, card: CardPresence) {}
    override fun onOfflinePinVerification(config: TransactionConfig, card: CardPresence) {}
    override fun onTerminalRiskManagement(config: TransactionConfig, card: CardPresence) {}
    override fun onTerminalActionAnalysis(config: TransactionConfig, card: CardPresence) {}
    override fun onStartOnlineProcess(config: TransactionConfig, card: CardPresence) {}
    override fun onIssuerAuthentication(config: TransactionConfig, card: CardPresence) {}
    override fun onScriptProcessing(config: TransactionConfig, card: CardPresence) {}
    override fun onTransactionCompletion(config: TransactionConfig, card: CardPresence) {}
}

/**
 * A behavior for terminal tests: `prepare` returns `prepared` and `start` calls `onStart`; it keeps
 * the engine `attach` gives it (as every behavior does) and runs no EMV step.
 */
internal fun stubBehavior(
    prepared: Boolean = false,
    onStart: (TransactionConfig) -> Unit = {},
): AbstractEmvBehavior = object : NoOpStepsBehavior() {
    override fun prepare(config: TransactionConfig, transaction: PosTerminal.Transaction) = prepared
    override fun start(config: TransactionConfig, card: CardPresence, transaction: PosTerminal.Transaction) =
        onStart(config)
}

/** Interface stub whose methods do nothing and return a default value. */
internal inline fun <reified T> stub(): T = Proxy.newProxyInstance(
    T::class.java.classLoader, arrayOf(T::class.java),
) { _, method, _ ->
    when (method.returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Long.TYPE -> 0L
        Integer.TYPE -> 0
        else -> null
    }
} as T
