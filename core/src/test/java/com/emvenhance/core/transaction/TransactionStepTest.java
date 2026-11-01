package com.emvenhance.core.transaction;

import static org.junit.Assert.assertEquals;

import java.util.EnumSet;
import org.junit.Test;

public class TransactionStepTest {

    @Test
    public void onlyErrorCompletedAndCancelledEndATransaction() {
        EnumSet<TransactionStep> ends = EnumSet.noneOf(TransactionStep.class);
        for (TransactionStep step : TransactionStep.values()) {
            if (step.isEnd()) {
                ends.add(step);
            }
        }

        assertEquals(EnumSet.of(TransactionStep.ERROR, TransactionStep.COMPLETED,
                TransactionStep.CANCELLED), ends);
    }
}
