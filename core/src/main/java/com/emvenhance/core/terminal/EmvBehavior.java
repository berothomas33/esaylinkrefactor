package com.emvenhance.core.terminal;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;

public interface EmvBehavior {

    boolean prepare(TransactionConfig config, PosTerminal.Transaction transaction);

    void start(TransactionConfig config, CardPresence card, PosTerminal.Transaction transaction);

    default void dispose() { }
}
