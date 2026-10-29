package com.emvenhance.core.emv;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.util.EmvLog;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public final class EmvEngine {

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final Authorizer authorizer;
    private final ApduTrace apduTrace = new ApduTrace();

    public interface Authorizer {
        AuthResult authorize(TransactionConfig config);
    }

    public EmvEngine(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    public Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    public void begin() {
        apduTrace.beginTransaction();
    }

    public ApduTrace apduTrace() {
        return apduTrace;
    }

    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        EmvLog.d("EmvStep: " + event);
        apduTrace.kernelStep(event.toString());
        emvSteps.onNext(event);
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }

    public AuthResult authorize(TransactionConfig config) {
        return authorizer.authorize(config);
    }
}
