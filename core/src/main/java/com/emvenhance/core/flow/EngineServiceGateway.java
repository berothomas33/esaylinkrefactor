package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.host.AuthResult;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

/**
 * Routes behavior service calls to {@link EmvEngine}, which forwards to the
 * {@code CommunicationBehavior} / {@code PrinterBehavior} that {@code PosTerminal} attached.
 *
 * <p>Behaviors therefore reach the host and the printer without ever importing those types.
 */
public final class EngineServiceGateway implements ServiceGateway {

    private final EmvEngine engine;
    private final PinPrompter pinPrompter;

    public EngineServiceGateway(@NonNull EmvEngine engine, @NonNull PinPrompter pinPrompter) {
        this.engine = engine;
        this.pinPrompter = pinPrompter;
    }

    @NonNull
    @Override
    public Single<AuthResult> authorize(@NonNull TransactionConfig config) {
        // engine.authorize() publishes ONLINE_REQUIRED / ONLINE_PROCESSING / ONLINE_COMPLETED
        // and blocks on the attached port; the orchestrator's scheduler owns this thread.
        return Single.fromCallable(() -> engine.authorize(config));
    }

    @NonNull
    @Override
    public Completable print(@NonNull List<String> lines) {
        return Completable.fromAction(() -> engine.print(lines));
    }

    @NonNull
    @Override
    public Single<PinResult> promptPin(boolean onlinePin, int triesLeft) {
        return pinPrompter.prompt(onlinePin, triesLeft);
    }
}
