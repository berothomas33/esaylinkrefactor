package com.emvenhance.core.emv;
import com.emvenhance.core.util.EmvLog;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public final class EmvEngine {

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();
    private final PublishSubject<EmvStepEvent> hookSteps = PublishSubject.create();

    private final ApduTrace apduTrace = new ApduTrace();

    /** The steps, for whoever shows them: a step arrives here before its hook runs. */
    public Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    /**
     * The same steps, for the behavior's observer that runs each step's hook. Its own stream,
     * served after emvSteps(): with one stream a hook ran inside the delivery, so a screen that
     * subscribed after the behavior saw a step only once its hook, and every step inside it, was
     * over — the steps in reverse.
     */
    public Observable<EmvStepEvent> hookSteps() {
        return hookSteps.hide();
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
        hookSteps.onNext(event);
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }
}
