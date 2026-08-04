package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Result of a terminal-owned card search.
 *
 * <p>Produced by {@link PosTerminal#searchCard} before EMV starts. The engine never performs
 * vendor-specific detection — it only consumes this value.
 */
public final class CardPresence {

    public enum Interface {
        CONTACT,
        CONTACTLESS,
        MAGSTRIPE
    }

    private final Interface cardInterface;
    @Nullable
    private final String track1;
    @Nullable
    private final String track2;
    @Nullable
    private final String track3;
    @Nullable
    private final byte[] serialInfo;

    private CardPresence(Interface cardInterface, @Nullable String track1,
            @Nullable String track2, @Nullable String track3, @Nullable byte[] serialInfo) {
        this.cardInterface = cardInterface;
        this.track1 = track1;
        this.track2 = track2;
        this.track3 = track3;
        this.serialInfo = serialInfo;
    }

    public static CardPresence contact() {
        return new CardPresence(Interface.CONTACT, null, null, null, null);
    }

    public static CardPresence contactless(@Nullable byte[] serialInfo) {
        return new CardPresence(Interface.CONTACTLESS, null, null, null, serialInfo);
    }

    public static CardPresence magstripe(@Nullable String track1, @Nullable String track2,
            @Nullable String track3) {
        return new CardPresence(Interface.MAGSTRIPE, track1, track2, track3, null);
    }

    public Interface getInterface() {
        return cardInterface;
    }

    public boolean isContact() {
        return cardInterface == Interface.CONTACT;
    }

    public boolean isContactless() {
        return cardInterface == Interface.CONTACTLESS;
    }

    public boolean isMagstripe() {
        return cardInterface == Interface.MAGSTRIPE;
    }

    @Nullable
    public String getTrack1() {
        return track1;
    }

    @Nullable
    public String getTrack2() {
        return track2;
    }

    @Nullable
    public String getTrack3() {
        return track3;
    }

    @Nullable
    public byte[] getSerialInfo() {
        return serialInfo;
    }

    public String getModeLabel() {
        switch (cardInterface) {
            case CONTACT:
                return "Contact";
            case CONTACTLESS:
                return "Contactless";
            case MAGSTRIPE:
                return "Magstripe";
            default:
                return cardInterface.name();
        }
    }
}
