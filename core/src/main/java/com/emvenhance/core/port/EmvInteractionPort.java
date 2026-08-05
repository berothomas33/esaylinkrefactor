package com.emvenhance.core.port;

import java.util.List;

/**
 * UI prompts for cardholder / cashier interaction. Implemented by the app layer.
 * Results must be delivered via {@link com.emvenhance.core.engine.EmvEngine#resume}.
 */
public interface EmvInteractionPort {

    void onSelectApplication(List<String> candidates);

    void onEnterPin(String pinType);

    void onDisplayMessage(String message);

    void onConfirmAmount(long amountMinor);

    /** Silent / auto-answer port for demos and headless tests. */
    final class Auto implements EmvInteractionPort {
        @Override
        public void onSelectApplication(List<String> candidates) {
            // Engine / Fake resume immediately without UI.
        }

        @Override
        public void onEnterPin(String pinType) {
        }

        @Override
        public void onDisplayMessage(String message) {
        }

        @Override
        public void onConfirmAmount(long amountMinor) {
        }
    }
}
