package com.emvenhance.core.behavior;

import androidx.annotation.Nullable;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.port.AsyncToken;
import com.emvenhance.core.port.InteractionRequest;
import java.util.Collections;
import java.util.Map;

/**
 * Sealed outcome of {@link EmvStepBehavior#execute} / {@link EmvStepBehavior#onSignal}.
 * The only control-flow channel from a behavior back to {@link com.emvenhance.core.engine.EmvEngine}.
 */
public abstract class BehaviorResult {

    private BehaviorResult() {
    }

    public static Success success() {
        return new Success(null, Collections.emptyMap());
    }

    public static Success success(@Nullable EmvStep suggestedNext) {
        return new Success(suggestedNext, Collections.emptyMap());
    }

    public static Success success(@Nullable EmvStep suggestedNext, Map<String, Object> extras) {
        return new Success(suggestedNext, extras);
    }

    public static Skip skip(String reason) {
        return new Skip(reason);
    }

    public static Failure failure(EmvError error) {
        return new Failure(error);
    }

    public static WaitInteraction waitInteraction(InteractionRequest request) {
        return new WaitInteraction(request);
    }

    /** @param timeoutMs timeout in milliseconds, or {@code <= 0} for no timeout */
    public static WaitAsync waitAsync(AsyncToken token, long timeoutMs) {
        return new WaitAsync(token, timeoutMs);
    }

    public boolean isSuccess() {
        return this instanceof Success || this instanceof Skip;
    }

    public boolean isWaiting() {
        return this instanceof WaitInteraction || this instanceof WaitAsync;
    }

    public static final class Success extends BehaviorResult {
        @Nullable
        private final EmvStep suggestedNext;
        private final Map<String, Object> extras;

        Success(@Nullable EmvStep suggestedNext, Map<String, Object> extras) {
            this.suggestedNext = suggestedNext;
            this.extras = extras != null ? extras : Collections.emptyMap();
        }

        @Nullable
        public EmvStep getSuggestedNext() {
            return suggestedNext;
        }

        public Map<String, Object> getExtras() {
            return extras;
        }
    }

    public static final class Skip extends BehaviorResult {
        private final String reason;

        Skip(String reason) {
            this.reason = reason != null ? reason : "";
        }

        public String getReason() {
            return reason;
        }
    }

    public static final class Failure extends BehaviorResult {
        private final EmvError error;

        Failure(EmvError error) {
            this.error = error;
        }

        public EmvError getError() {
            return error;
        }
    }

    public static final class WaitInteraction extends BehaviorResult {
        private final InteractionRequest request;

        WaitInteraction(InteractionRequest request) {
            this.request = request;
        }

        public InteractionRequest getRequest() {
            return request;
        }
    }

    public static final class WaitAsync extends BehaviorResult {
        private final AsyncToken token;
        private final long timeoutMs;

        WaitAsync(AsyncToken token, long timeoutMs) {
            this.token = token;
            this.timeoutMs = timeoutMs;
        }

        public AsyncToken getToken() {
            return token;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }
    }
}
