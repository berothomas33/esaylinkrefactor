package com.emvenhance.core.card;

import androidx.annotation.Nullable;

/**
 * Everything read off the card during EMV processing that the host needs for online
 * authorization — assembled once, right before {@code START_ONLINE_PROCESS}, and attached to
 * {@link TransactionConfig#withEmvResult}. Vendor-agnostic mirror of the old project's
 * {@code PosTransactionResult} (itself produced by a vendor SDK's EMV callback), trimmed to the
 * fields the exchange/sale request bodies actually read from it.
 *
 * <p>Built via {@link Builder} since it has this many fields — same shape as
 * {@code EmvTransParam.Builder}/{@code EmvProcessParam.Builder} already used elsewhere in this
 * codebase for the same reason.
 */
public final class EmvTransactionResult {

    @Nullable
    private final String pan;
    @Nullable
    private final String maskedPan;
    @Nullable
    private final String track2;
    /** YYMM, tag 5F24 truncated — see {@link Builder#expDate}. */
    @Nullable
    private final String expDate;
    @Nullable
    private final String cardHolderName;
    /** Field 55 (ICC System Related Data), hex BER-TLV — same value as {@code TransactionConfig#getIccData}. */
    @Nullable
    private final String chipData;
    @Nullable
    private final String pinBlock;
    @Nullable
    private final String emvAppLabel;
    @Nullable
    private final String emvAppName;
    @Nullable
    private final String aid;
    @Nullable
    private final String tvr;
    @Nullable
    private final String tsi;
    @Nullable
    private final String atc;
    /** Tag 9F26 when CID (9F27) says this cryptogram is an ARQC — {@code null} otherwise. */
    @Nullable
    private final String arqc;
    /** Tag 9F26 when CID (9F27) says this cryptogram is a TC — {@code null} otherwise. */
    @Nullable
    private final String tc;
    @Nullable
    private final String issuerName;
    private final boolean hasPin;
    private final EntryMethod entryMethod;

    private EmvTransactionResult(Builder b) {
        this.pan = b.pan;
        this.maskedPan = b.maskedPan;
        this.track2 = b.track2;
        this.expDate = b.expDate;
        this.cardHolderName = b.cardHolderName;
        this.chipData = b.chipData;
        this.pinBlock = b.pinBlock;
        this.emvAppLabel = b.emvAppLabel;
        this.emvAppName = b.emvAppName;
        this.aid = b.aid;
        this.tvr = b.tvr;
        this.tsi = b.tsi;
        this.atc = b.atc;
        this.arqc = b.arqc;
        this.tc = b.tc;
        this.issuerName = b.issuerName;
        this.hasPin = b.hasPin;
        this.entryMethod = b.entryMethod;
    }

    @Nullable
    public String getPan() {
        return pan;
    }

    @Nullable
    public String getMaskedPan() {
        return maskedPan;
    }

    @Nullable
    public String getTrack2() {
        return track2;
    }

    @Nullable
    public String getExpDate() {
        return expDate;
    }

    @Nullable
    public String getCardHolderName() {
        return cardHolderName;
    }

    @Nullable
    public String getChipData() {
        return chipData;
    }

    @Nullable
    public String getPinBlock() {
        return pinBlock;
    }

    @Nullable
    public String getEmvAppLabel() {
        return emvAppLabel;
    }

    @Nullable
    public String getEmvAppName() {
        return emvAppName;
    }

    @Nullable
    public String getAid() {
        return aid;
    }

    @Nullable
    public String getTvr() {
        return tvr;
    }

    @Nullable
    public String getTsi() {
        return tsi;
    }

    @Nullable
    public String getAtc() {
        return atc;
    }

    @Nullable
    public String getArqc() {
        return arqc;
    }

    @Nullable
    public String getTc() {
        return tc;
    }

    @Nullable
    public String getIssuerName() {
        return issuerName;
    }

    public boolean isHasPin() {
        return hasPin;
    }

    public EntryMethod getEntryMethod() {
        return entryMethod;
    }

    public static final class Builder {
        @Nullable
        private String pan;
        @Nullable
        private String maskedPan;
        @Nullable
        private String track2;
        @Nullable
        private String expDate;
        @Nullable
        private String cardHolderName;
        @Nullable
        private String chipData;
        @Nullable
        private String pinBlock;
        @Nullable
        private String emvAppLabel;
        @Nullable
        private String emvAppName;
        @Nullable
        private String aid;
        @Nullable
        private String tvr;
        @Nullable
        private String tsi;
        @Nullable
        private String atc;
        @Nullable
        private String arqc;
        @Nullable
        private String tc;
        @Nullable
        private String issuerName;
        private boolean hasPin;
        private EntryMethod entryMethod = EntryMethod.CHIP;

        public Builder pan(@Nullable String pan) {
            this.pan = pan;
            return this;
        }

        public Builder maskedPan(@Nullable String maskedPan) {
            this.maskedPan = maskedPan;
            return this;
        }

        public Builder track2(@Nullable String track2) {
            this.track2 = track2;
            return this;
        }

        public Builder expDate(@Nullable String expDate) {
            this.expDate = expDate;
            return this;
        }

        public Builder cardHolderName(@Nullable String cardHolderName) {
            this.cardHolderName = cardHolderName;
            return this;
        }

        public Builder chipData(@Nullable String chipData) {
            this.chipData = chipData;
            return this;
        }

        public Builder pinBlock(@Nullable String pinBlock) {
            this.pinBlock = pinBlock;
            return this;
        }

        public Builder emvAppLabel(@Nullable String emvAppLabel) {
            this.emvAppLabel = emvAppLabel;
            return this;
        }

        public Builder emvAppName(@Nullable String emvAppName) {
            this.emvAppName = emvAppName;
            return this;
        }

        public Builder aid(@Nullable String aid) {
            this.aid = aid;
            return this;
        }

        public Builder tvr(@Nullable String tvr) {
            this.tvr = tvr;
            return this;
        }

        public Builder tsi(@Nullable String tsi) {
            this.tsi = tsi;
            return this;
        }

        public Builder atc(@Nullable String atc) {
            this.atc = atc;
            return this;
        }

        public Builder arqc(@Nullable String arqc) {
            this.arqc = arqc;
            return this;
        }

        public Builder tc(@Nullable String tc) {
            this.tc = tc;
            return this;
        }

        public Builder issuerName(@Nullable String issuerName) {
            this.issuerName = issuerName;
            return this;
        }

        public Builder hasPin(boolean hasPin) {
            this.hasPin = hasPin;
            return this;
        }

        public Builder entryMethod(EntryMethod entryMethod) {
            this.entryMethod = entryMethod;
            return this;
        }

        public EmvTransactionResult build() {
            return new EmvTransactionResult(this);
        }
    }
}
