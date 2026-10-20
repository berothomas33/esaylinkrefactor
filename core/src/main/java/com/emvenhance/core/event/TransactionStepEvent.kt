package com.emvenhance.core.event

import java.util.Collections

/**
 * Immutable event published on the engine's transaction steps.
 *
 * Every emission is a new instance carrying the new [TransactionStep] plus an optional bag of
 * contextual data — PAN, issuer name, authorization result, error reason, etc. Using a `Map`
 * rather than individual fields keeps this class stable when new data needs arise: the vendor
 * implementation just puts a new key, and only the consumer that cares about that key reads it.
 *
 * The engine's `transactionSteps` flow replays the latest one, so a late collector (or one
 * reattaching after rotation) immediately receives the most recent event, which is exactly the
 * behaviour the UI needs.
 */
class TransactionStepEvent private constructor(
    val step: TransactionStep,
    val message: String?,
    data: Map<String, Any?>,
) {

    val data: Map<String, Any?> = Collections.unmodifiableMap(data)

    class Builder internal constructor(private val step: TransactionStep) {
        private var message: String = step.label
        private val data = HashMap<String, Any?>()

        fun message(message: String) = apply { this.message = message }

        fun put(key: String, value: Any?) = apply { data[key] = value }

        fun build(): TransactionStepEvent = TransactionStepEvent(step, message, data)
    }

    /** Type-safe convenience for reading a data entry. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T? = data[key] as T?

    /** String convenience — returns the value or a dash when absent. */
    fun getString(key: String): String = data[key]?.toString() ?: "—"

    override fun toString(): String =
        step.toString() + (if (message != null) ": $message" else "") +
            (if (data.isEmpty()) "" else " $data")

    companion object {
        // ─── Well-known data keys ────────────────────────────────────────────
        const val KEY_PAN = "pan"
        const val KEY_ISSUER_NAME = "issuerName"
        const val KEY_CARDHOLDER_NAME = "cardHolderName"
        const val KEY_MODE = "mode"
        const val KEY_AMOUNT = "amount"
        const val KEY_RESULT = "result"
        const val KEY_ERROR = "error"
        const val KEY_ONLINE_PIN = "onlinePin"
        const val KEY_PIN_BYPASS = "pinBypassAllowed"
        const val KEY_PIN_TRIES_LEFT = "pinTriesLeft"
        const val KEY_TVR = "tvr"
        const val KEY_TAC_DENIAL = "tacDenial"
        const val KEY_IAC_DENIAL = "iacDenial"
        const val KEY_ICC_DATA = "iccData"

        /**
         * On `WAITING_FOR_CARD`: why the card has to be presented again (tap too short, see
         * phone, chip not read...) — absent on a first search.
         */
        const val KEY_RETRY_PROMPT = "retryPrompt"

        // ─── Factory methods ─────────────────────────────────────────────────

        @JvmStatic
        fun idle(): TransactionStepEvent =
            TransactionStepEvent(TransactionStep.IDLE, null, emptyMap())

        @JvmStatic
        fun of(step: TransactionStep): TransactionStepEvent =
            TransactionStepEvent(step, step.label, emptyMap())

        @JvmStatic
        fun of(step: TransactionStep, message: String?): TransactionStepEvent =
            TransactionStepEvent(step, message, emptyMap())

        @JvmStatic
        fun of(step: TransactionStep, message: String?, data: Map<String, Any?>): TransactionStepEvent =
            TransactionStepEvent(step, message, HashMap(data))

        // ─── Builder for multi-field events ──────────────────────────────────

        @JvmStatic
        fun builder(step: TransactionStep): Builder = Builder(step)
    }
}
