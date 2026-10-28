package com.emvenhance.core.terminal;

import com.emvenhance.core.transaction.TransactionConfig;

public interface EmvBehavior {

    void run(TransactionConfig config, PosTerminal.Transaction transaction);

    void cancelCardSearch();

    default void dispose() { }
}
