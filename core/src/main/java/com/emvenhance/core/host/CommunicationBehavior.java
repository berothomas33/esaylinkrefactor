package com.emvenhance.core.host;

import com.emvenhance.core.transaction.TransactionConfig;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public interface CommunicationBehavior {

    Single<AuthResult> authorize(TransactionConfig config);

    default Completable acknowledge(TransactionConfig sent) {
        return Completable.complete();
    }

    default Completable reverse(TransactionConfig sent) {
        return Completable.complete();
    }
}
