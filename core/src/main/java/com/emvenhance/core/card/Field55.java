package com.emvenhance.core.card;

import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * Field 55 (ICC System Related Data) for the host: the tag list, the BER-TLV encoding and the
 * present/missing summary, the same for every vendor. A vendor only reads one tag's value from
 * its kernel ({@code AbstractEmvBehavior#readIccTag}); {@link #read} does the rest.
 */
public final class Field55 {

    /** One field 55 tag and its EMV name, for the log. */
    private static final class Tag {
        final int tag;
        final String name;

        Tag(int tag, String name) {
            this.tag = tag;
            this.name = name;
        }
    }

    /**
     * Field 55 tags, in order: the old app's list ({@code PaxUtils} → EasyLink
     * {@code TransRequest#setTagList}, sent as {@code chipData}), exactly as it sent it. The
     * old app's field 55 for a Mastercard offline-PIN chip sale, which the host approved, ends
     * with 9F09, 9F33, 9F34, 9F35 and 9F41 a second time, so they are sent twice here too. The
     * card's cryptogram data (9F26, 9F27, 9F10, 9F36, 95, 9F37, 82) is what the host needs to
     * validate the ARQC and compute the ARPC (tag 91). 9F66 (TTQ) only exists for Visa-type
     * kernels; Mastercard has none.
     */
    private static final Tag[] TAGS = {
            new Tag(0x5F2A, "Transaction Currency Code"),
            new Tag(0x5F34, "PAN Sequence Number"),
            new Tag(0x82, "Application Interchange Profile"),
            new Tag(0x84, "Dedicated File Name (AID)"),
            new Tag(0x95, "Terminal Verification Results"),
            new Tag(0x9A, "Transaction Date"),
            new Tag(0x9C, "Transaction Type"),
            new Tag(0x9F02, "Amount, Authorized"),
            new Tag(0x9F03, "Amount, Other"),
            new Tag(0x9F09, "Application Version Number"),
            new Tag(0x9F10, "Issuer Application Data"),
            new Tag(0x9F21, "Transaction Time"),
            new Tag(0x9F1A, "Terminal Country Code"),
            new Tag(0x9F26, "Application Cryptogram"),
            new Tag(0x9F27, "Cryptogram Information Data"),
            new Tag(0x9F33, "Terminal Capabilities"),
            new Tag(0x9F34, "CVM Results"),
            new Tag(0x9F35, "Terminal Type"),
            new Tag(0x9F36, "Application Transaction Counter"),
            new Tag(0x9F37, "Unpredictable Number"),
            new Tag(0x9F41, "Transaction Sequence Counter"),
            new Tag(0x9F42, "Application Currency Code"),
            new Tag(0x9F53, "Transaction Category Code"),
            new Tag(0x9F1E, "Interface Device Serial Number"),
            new Tag(0x9F63, "Scheme-proprietary 9F63"),
            new Tag(0x9F66, "Terminal Transaction Qualifiers"),
            new Tag(0x9F09, "Application Version Number"),
            new Tag(0x9F33, "Terminal Capabilities"),
            new Tag(0x9F34, "CVM Results"),
            new Tag(0x9F35, "Terminal Type"),
            new Tag(0x9F41, "Transaction Sequence Counter"),
//            new Tag(0x9F40, "Additional Terminal Capabilities"),
//            new Tag(0x9F1B, "Terminal Floor Limit"),
//            new Tag(0x9F15, "Merchant Category Code"),
//            new Tag(0x9F1C, "Terminal Identification"),
//            new Tag(0x9F49, "Default DDOL"),
//            new Tag(0x97, "Default TDOL"),
    };

    private final String hex;
    private final int length;
    private final String present;
    private final String missing;

    private Field55(String hex, int length, String present, String missing) {
        this.hex = hex;
        this.length = length;
        this.present = present;
        this.missing = missing;
    }

    /**
     * Reads every field 55 tag through {@code reader} (value bytes only; {@code null} or empty
     * means the kernel has no value) and encodes the ones present as BER-TLV. A missing tag is
     * left out, not padded.
     */
    public static Field55 read(IntFunction<byte[]> reader) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringBuilder present = new StringBuilder();
        StringBuilder missing = new StringBuilder();
        for (Tag t : TAGS) {
            byte[] value = reader.apply(t.tag);
            String tagHex = Integer.toHexString(t.tag).toUpperCase(Locale.US);
            if (value == null || value.length == 0) {
                missing.append(missing.length() == 0 ? "" : " ").append(tagHex);
                continue;
            }
            writeTlv(out, t.tag, value);
            present.append('\n').append(tagHex).append(' ').append(t.name)
                    .append(" = ").append(toHex(value));
        }
        byte[] field55 = out.toByteArray();
        return new Field55(toHex(field55), field55.length, present.toString(),
                missing.length() == 0 ? "none" : missing.toString());
    }

    /** Hex-encoded BER-TLV, as sent in {@code chipData}; empty when no tag was present. */
    public String hex() {
        return hex;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * The log/trace text: every tag sent with its name and value, the tags the kernel had no
     * value for, and the hex.
     */
    public String describe(@Nullable String source) {
        return "FIELD 55 (" + (source != null ? source + ", " : "") + length + " bytes)" + present
                + "\nmissing: " + missing
                + "\nhex: " + hex;
    }

    /** Appends one BER-TLV entry (tag + length + value). */
    private static void writeTlv(ByteArrayOutputStream out, int tag, byte[] value) {
        byte[] tagBytes = tagToBytes(tag);
        out.write(tagBytes, 0, tagBytes.length);
        writeBerLength(out, value.length);
        out.write(value, 0, value.length);
    }

    /**
     * BER-TLV tag bytes: one for a plain tag ({@code 0x9A}), two for a {@code 0x9F}/{@code 0x5F}
     * tag ({@code 0x9F02}), three for a {@code 0xDF}-class tag ({@code 0xDF8120}).
     */
    private static byte[] tagToBytes(int tag) {
        if (tag <= 0xFF) {
            return new byte[]{(byte) tag};
        }
        if (tag <= 0xFFFF) {
            return new byte[]{(byte) (tag >> 8), (byte) tag};
        }
        return new byte[]{(byte) (tag >> 16), (byte) (tag >> 8), (byte) tag};
    }

    /** BER-TLV length: short form under 128 bytes, one length-of-length byte above it. */
    private static void writeBerLength(ByteArrayOutputStream out, int length) {
        if (length < 0x80) {
            out.write(length);
        } else if (length <= 0xFF) {
            out.write(0x81);
            out.write(length);
        } else {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
