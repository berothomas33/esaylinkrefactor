package com.emvenhance.core.event

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.EnumSet

/** The end of a transaction has one definition, shared by the engine and the behavior. */
class TransactionStepTest {

    @Test
    fun onlyErrorCompletedAndCancelledEndATransaction() {
        val ends = TransactionStep.values().filterTo(EnumSet.noneOf(TransactionStep::class.java)) { it.isEnd }

        // APPROVED and DECLINED are outcomes, not the end: COMPLETED follows them.
        assertEquals(EnumSet.of(TransactionStep.ERROR, TransactionStep.COMPLETED, TransactionStep.CANCELLED), ends)
    }
}
