package com.emvenhance.core.terminal

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Multi-application selection: the cardholder picks which of the card's applications to use,
 * while the kernel waits on the transaction thread (its callback needs the index on the same
 * stack — a plain blocking wait, never a coroutine). The part every vendor shares lives here —
 * the wait, the timeout, giving up when the transaction ends, and "no answer" (never "pick the
 * first"). The vendor's subclass turns its own candidate data into labels and shows / closes the
 * choice on screen.
 */
abstract class AbstractAppSelectDialog {

    private val done = CountDownLatch(1)

    @Volatile
    private var selectedIndex = NO_ANSWER

    /**
     * Shows `labels` and blocks until the cardholder picks one: its index. [NO_ANSWER] (and the
     * choice closed) once the timeout passes, or `ended` turns true (a cancel).
     */
    fun selectBlocking(labels: List<String>, ended: () -> Boolean): Int {
        show(labels)
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        try {
            while (!done.await(POLL_MS, TimeUnit.MILLISECONDS)) {
                if (ended() || System.currentTimeMillis() >= deadline) {
                    dismiss()
                    return NO_ANSWER
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            dismiss()
            return NO_ANSWER
        }
        return selectedIndex
    }

    /** Puts the choice on screen (from the transaction thread: post it to the UI thread). */
    protected abstract fun show(labels: List<String>)

    /** Takes the choice off the screen; safe to call when it isn't there. */
    protected abstract fun dismiss()

    /** The cardholder picked `index`. */
    protected fun onSelected(index: Int) {
        selectedIndex = index
        done.countDown()
    }

    /** Nothing to show it on (no screen): no answer at once, never a wait with nothing shown. */
    protected fun onNoScreen() {
        selectedIndex = NO_ANSWER
        done.countDown()
    }

    companion object {
        /** No answer: the cardholder didn't choose in time, the transaction ended, or no screen. */
        const val NO_ANSWER = -1

        /** How long the cardholder has to choose; then [NO_ANSWER]. */
        private const val TIMEOUT_MS = 30_000L

        /** How often the wait checks whether the transaction ended (a cancel closes the choice). */
        private const val POLL_MS = 250L
    }
}
