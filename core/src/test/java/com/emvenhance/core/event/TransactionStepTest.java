package com.emvenhance.core.event;

import static org.junit.Assert.assertEquals;

import java.util.EnumSet;
import org.junit.Test;

/** The end of a transaction has one definition, shared by the engine and the behavior. */
public class TransactionStepTest {

    @Test
    public void onlyErrorCompletedAndCancelledEndATransaction() {
        EnumSet<TransactionStep> ends = EnumSet.noneOf(TransactionStep.class);
        for (TransactionStep step : TransactionStep.values()) {
            if (step.isEnd()) {
                ends.add(step);
            }
        }

        // APPROVED and DECLINED are outcomes, not the end: COMPLETED follows them.
        assertEquals(EnumSet.of(TransactionStep.ERROR, TransactionStep.COMPLETED,
                TransactionStep.CANCELLED), ends);
    }
}
