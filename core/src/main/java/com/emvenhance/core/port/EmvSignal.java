package com.emvenhance.core.port;

import androidx.annotation.Nullable;
import com.emvenhance.core.host.AuthResult;

/**
 * Typed async events fed into {@link com.emvenhance.core.engine.EmvEngine#resume}.
 */
public abstract class EmvSignal {

    private EmvSignal() {
    }

    public static final class ApplicationSelected extends EmvSignal {
        private final String aid;

        public ApplicationSelected(String aid) {
            this.aid = aid;
        }

        public String getAid() {
            return aid;
        }
    }

    public static final class PinEntered extends EmvSignal {
        private final boolean onlinePin;
        private final boolean bypass;
        private final int triesLeft;

        public PinEntered(boolean onlinePin, boolean bypass, int triesLeft) {
            this.onlinePin = onlinePin;
            this.bypass = bypass;
            this.triesLeft = triesLeft;
        }

        public boolean isOnlinePin() {
            return onlinePin;
        }

        public boolean isBypass() {
            return bypass;
        }

        public int getTriesLeft() {
            return triesLeft;
        }
    }

    public static final class PinCancelled extends EmvSignal {
    }

    public static final class HostResponse extends EmvSignal {
        private final AuthResult result;

        public HostResponse(AuthResult result) {
            this.result = result;
        }

        public AuthResult getResult() {
            return result;
        }
    }

    public static final class KernelCompleted extends EmvSignal {
        @Nullable
        private final AsyncToken token;
        private final boolean success;
        @Nullable
        private final String detail;

        public KernelCompleted(@Nullable AsyncToken token, boolean success,
                @Nullable String detail) {
            this.token = token;
            this.success = success;
            this.detail = detail;
        }

        @Nullable
        public AsyncToken getToken() {
            return token;
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public String getDetail() {
            return detail;
        }
    }

    public static final class CardDetected extends EmvSignal {
    }

    public static final class CardRemoved extends EmvSignal {
    }

    public static final class Timeout extends EmvSignal {
    }

    public static final class UserCancel extends EmvSignal {
    }
}
