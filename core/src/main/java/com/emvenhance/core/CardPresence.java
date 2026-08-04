package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Result of {@link PosTerminal#searchCard} — selected {@link EntryMethod} plus track/UID data.
 */
public final class CardPresence {

    private final EntryMethod entryMethod;
    @Nullable
    private final String track1;
    @Nullable
    private final String track2;
    @Nullable
    private final String track3;
    @Nullable
    private final byte[] serialInfo;
    @Nullable
    private final String manualPan;

    private CardPresence(EntryMethod entryMethod, @Nullable String track1,
            @Nullable String track2, @Nullable String track3, @Nullable byte[] serialInfo,
            @Nullable String manualPan) {
        this.entryMethod = entryMethod;
        this.track1 = track1;
        this.track2 = track2;
        this.track3 = track3;
        this.serialInfo = serialInfo;
        this.manualPan = manualPan;
    }

    public static CardPresence chip() {
        return new CardPresence(EntryMethod.CHIP, null, null, null, null, null);
    }

    public static CardPresence contactless(@Nullable byte[] serialInfo) {
        return new CardPresence(EntryMethod.CONTACTLESS, null, null, null, serialInfo, null);
    }

    public static CardPresence magstripe(@Nullable String track1, @Nullable String track2,
            @Nullable String track3) {
        return new CardPresence(EntryMethod.MAGSTRIPE, track1, track2, track3, null, null);
    }

    public static CardPresence manual(@Nullable String pan) {
        return new CardPresence(EntryMethod.MANUAL, null, null, null, null, pan);
    }

    public EntryMethod getEntryMethod() {
        return entryMethod;
    }

    public boolean isChip() {
        return entryMethod == EntryMethod.CHIP;
    }

    public boolean isContactless() {
        return entryMethod == EntryMethod.CONTACTLESS;
    }

    public boolean isMagstripe() {
        return entryMethod == EntryMethod.MAGSTRIPE;
    }

    public boolean isManual() {
        return entryMethod == EntryMethod.MANUAL;
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

    @Nullable
    public String getManualPan() {
        return manualPan;
    }

    public String getModeLabel() {
        switch (entryMethod) {
            case CHIP:
                return "Chip";
            case CONTACTLESS:
                return "Contactless";
            case MAGSTRIPE:
                return "Magstripe";
            case MANUAL:
                return "Manual";
            default:
                return entryMethod.name();
        }
    }
}
