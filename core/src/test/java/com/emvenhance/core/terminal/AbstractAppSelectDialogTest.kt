package com.emvenhance.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AbstractAppSelectDialog]: the cardholder's pick comes back as its index; a transaction that
 * ends while the choice is up, or no screen to show it on, gives [AbstractAppSelectDialog.NO_ANSWER]
 * (never "pick the first") and closes the choice.
 */
class AbstractAppSelectDialogTest {

    @Test
    fun thePickedIndexComesBack() {
        val dialog = TestDialog(pick = 1)

        assertEquals(1, dialog.selectBlocking(LABELS) { false })
    }

    @Test
    fun theEndOfTheTransactionGivesNoAnswerAndClosesTheChoice() {
        val dialog = TestDialog()
        val started = System.currentTimeMillis()

        assertEquals(AbstractAppSelectDialog.NO_ANSWER, dialog.selectBlocking(LABELS) { true })
        assertTrue("closed", dialog.dismissed)
        assertTrue("gave up within a poll, not at the 30 s timeout", System.currentTimeMillis() - started < 5_000)
    }

    @Test
    fun noScreenGivesNoAnswer() {
        val dialog = TestDialog(noScreen = true)

        assertEquals(AbstractAppSelectDialog.NO_ANSWER, dialog.selectBlocking(LABELS) { false })
    }

    /** Answers at once on show: `pick`, no screen, or nothing (the cardholder never chooses). */
    private class TestDialog(private val pick: Int? = null, private val noScreen: Boolean = false) :
        AbstractAppSelectDialog() {
        @Volatile
        var dismissed = false

        override fun show(labels: List<String>) {
            if (noScreen) {
                onNoScreen()
            } else if (pick != null) {
                onSelected(pick)
            }
        }

        override fun dismiss() {
            dismissed = true
        }
    }

    private companion object {
        val LABELS = listOf("VISA CREDIT", "VISA DEBIT")
    }
}
