package com.emvenhance.core.engine;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.util.ApduTrace;
import com.emvenhance.core.util.EmvLog;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.concurrent.atomic.AtomicInteger;

public final class EmvEngine {

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final Authorizer authorizer;

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
        retryCount.set(0);
        retryPrompt = null;
        pendingRetryConfig = null;
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

    @Nullable
    private volatile TransactionConfig pendingRetryConfig;
    @Nullable
    private volatile String retryPrompt;
    private final AtomicInteger retryCount = new AtomicInteger();
    private final ApduTrace apduTrace = new ApduTrace();

    public void requestRetry(TransactionConfig adjustedConfig) {
        requestRetry(adjustedConfig, null);
    }

    public void requestRetry(TransactionConfig adjustedConfig, @Nullable String prompt) {
        pendingRetryConfig = adjustedConfig;
        retryPrompt = prompt;
    }

    @Nullable
    public TransactionConfig consumePendingRetry() {
        TransactionConfig config = pendingRetryConfig;
        pendingRetryConfig = null;
        if (config != null) {
            apduTrace.transactionEvent("RETRY #" + retryCount.incrementAndGet());
        }
        return config;
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    @Nullable
    public String consumeRetryPrompt() {
        String prompt = retryPrompt;
        retryPrompt = null;
        return prompt;
    }
}
