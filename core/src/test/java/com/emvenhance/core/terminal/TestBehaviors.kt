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
    override fun onTerminalInitialization(engine: EmvEngine, config: TransactionConfig) {}
    override fun onApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onWaitApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onFinalApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onReadApplicationData(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onSetTransactionData(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onOfflineDataAuthentication(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onProcessRestrictions(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onCardholderVerification(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onOfflinePinVerification(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onTerminalRiskManagement(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onTerminalActionAnalysis(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onStartOnlineProcess(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onIssuerAuthentication(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onScriptProcessing(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
    override fun onTransactionCompletion(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {}
}

/** An [EmvBehavior] whose `prepare` returns `prepared` and every other call does nothing. */
internal fun stubBehavior(
    prepared: Boolean = false,
    onStart: (TransactionConfig) -> Unit = {},
): EmvBehavior = object : EmvBehavior {
    override fun prepare(engine: EmvEngine, config: TransactionConfig) = prepared
    override fun start(engine: EmvEngine, config: TransactionConfig, card: CardPresence) = onStart(config)
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
