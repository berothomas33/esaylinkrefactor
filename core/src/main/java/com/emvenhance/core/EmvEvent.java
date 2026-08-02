package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * One-shot event published on the events subject: dialogs, prompts, receipt, outcome.
 *
 * <p>This is a single tagged class rather than a hierarchy of event subclasses, because
 * Java cannot {@code switch} over types and the UI reads better as one switch on
 * {@link Type}. Only the fields that belong to a given type are populated; the
 * accessors document which.
 */
public final class EmvEvent {

    public enum Type {
        STEP_CHANGED,
        CONFIRM_CARD,
        ASK_PIN,
        NEED_ONLINE,
        REMOVE_CARD,
        SECOND_TAP,
        SEE_PHONE,
        RECEIPT,
        COMPLETED,
        FAILED,
        MESSAGE
    }

    private final Type type;
    private final EmvStep step;
    private final String message;
    private final String detail;
    private final String pan;
    private final String issuerName;
    private final String cardHolderName;
    private final boolean approved;
    private final boolean onlinePin;
    private final boolean bypassAllowed;
    private final int leftTimes;

    private EmvEvent(Type type, @Nullable EmvStep step, @Nullable String message,
            @Nullable String detail, @Nullable String pan, @Nullable String issuerName,
            @Nullable String cardHolderName, boolean approved, boolean onlinePin,
            boolean bypassAllowed, int leftTimes) {
        this.type = type;
        this.step = step;
        this.message = message;
        this.detail = detail;
        this.pan = pan;
        this.issuerName = issuerName;
        this.cardHolderName = cardHolderName;
        this.approved = approved;
        this.onlinePin = onlinePin;
        this.bypassAllowed = bypassAllowed;
        this.leftTimes = leftTimes;
    }

    public static EmvEvent stepChanged(EmvStep step, @Nullable String detail) {
        return new EmvEvent(Type.STEP_CHANGED, step, step.getTitle(), detail,
                null, null, null, false, false, false, 0);
    }

    public static EmvEvent confirmCard(String pan, String issuerName, String cardHolderName) {
        return new EmvEvent(Type.CONFIRM_CARD, null, "Confirm card", null,
                pan, issuerName, cardHolderName, false, false, false, 0);
    }

    public static EmvEvent askPin(boolean onlinePin, boolean bypassAllowed, int leftTimes) {
        return new EmvEvent(Type.ASK_PIN, null, "Enter PIN", null,
                null, null, null, false, onlinePin, bypassAllowed, leftTimes);
    }

    public static EmvEvent needOnline() {
        return new EmvEvent(Type.NEED_ONLINE, null, "Going online", null,
                null, null, null, false, false, false, 0);
    }

    public static EmvEvent removeCard() {
        return new EmvEvent(Type.REMOVE_CARD, null, "Please remove card", null,
                null, null, null, false, false, false, 0);
    }

    public static EmvEvent secondTap() {
        return new EmvEvent(Type.SECOND_TAP, null, "Present card again", null,
                null, null, null, false, false, false, 0);
    }

    public static EmvEvent seePhone() {
        return new EmvEvent(Type.SEE_PHONE, null, "See phone", null,
                null, null, null, false, false, false, 0);
    }

    public static EmvEvent receipt(boolean approved, String message) {
        return new EmvEvent(Type.RECEIPT, null, message, null,
                null, null, null, approved, false, false, 0);
    }

    public static EmvEvent completed(String result) {
        return new EmvEvent(Type.COMPLETED, null, result, null,
                null, null, null, true, false, false, 0);
    }

    public static EmvEvent failed(String title, @Nullable String reason) {
        return new EmvEvent(Type.FAILED, null, title, reason,
                null, null, null, false, false, false, 0);
    }

    public static EmvEvent message(String text) {
        return new EmvEvent(Type.MESSAGE, null, text, null,
                null, null, null, false, false, false, 0);
    }

    public Type getType() {
        return type;
    }

    /** STEP_CHANGED only. */
    @Nullable
    public EmvStep getStep() {
        return step;
    }

    /** Display text. For FAILED this is the title. */
    public String getMessage() {
        return message;
    }

    /** STEP_CHANGED: kernel detail. FAILED: failure reason. */
    @Nullable
    public String getDetail() {
        return detail;
    }

    /** CONFIRM_CARD only. */
    @Nullable
    public String getPan() {
        return pan;
    }

    /** CONFIRM_CARD only. */
    @Nullable
    public String getIssuerName() {
        return issuerName;
    }

    /** CONFIRM_CARD only. */
    @Nullable
    public String getCardHolderName() {
        return cardHolderName;
    }

    /** RECEIPT and COMPLETED only. */
    public boolean isApproved() {
        return approved;
    }

    /** ASK_PIN only. */
    public boolean isOnlinePin() {
        return onlinePin;
    }

    /** ASK_PIN only. */
    public boolean isBypassAllowed() {
        return bypassAllowed;
    }

    /** ASK_PIN only: remaining PIN attempts. */
    public int getLeftTimes() {
        return leftTimes;
    }
}
