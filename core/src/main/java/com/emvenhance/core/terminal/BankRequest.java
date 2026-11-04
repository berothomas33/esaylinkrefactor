package com.emvenhance.core.terminal;

import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.transaction.TransactionConfig;

public interface BankRequest {

    AuthResult send(TransactionConfig config);
}
