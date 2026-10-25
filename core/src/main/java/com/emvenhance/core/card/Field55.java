package com.emvenhance.core.card;

import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.function.IntFunction;

public final class Field55 {

    private static final class Tag {
        final int tag;
        final String name;

        Tag(int tag, String name) {
            this.tag = tag;
            this.name = name;
        }
    }

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

    public String hex() {
        return hex;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public String describe(@Nullable String source) {
        return "FIELD 55 (" + (source != null ? source + ", " : "") + length + " bytes)" + present
                + "\nmissing: " + missing
                + "\nhex: " + hex;
    }

    private static void writeTlv(ByteArrayOutputStream out, int tag, byte[] value) {
        byte[] tagBytes = tagToBytes(tag);
        out.write(tagBytes, 0, tagBytes.length);
        writeBerLength(out, value.length);
        out.write(value, 0, value.length);
    }

    private static byte[] tagToBytes(int tag) {
        if (tag <= 0xFF) {
            return new byte[]{(byte) tag};
        }
        if (tag <= 0xFFFF) {
            return new byte[]{(byte) (tag >> 8), (byte) tag};
        }
        return new byte[]{(byte) (tag >> 16), (byte) (tag >> 8), (byte) tag};
    }

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
