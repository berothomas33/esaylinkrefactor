package com.emvenhance.core.host;

import com.emvenhance.core.card.TransactionConfig;
import io.reactivex.rxjava3.core.Single;

public interface CommunicationBehavior {

    Single<AuthResult> authorize(TransactionConfig config);
}
