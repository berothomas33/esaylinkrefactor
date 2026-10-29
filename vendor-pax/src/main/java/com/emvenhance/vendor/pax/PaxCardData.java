package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.pax.commonlib.utils.ConvertUtils;
import java.nio.charset.StandardCharsets;

/**
 * The card's data as text: the PAN from track 2 or tag 5A, the masked PAN, ASCII tags (5F20,
 * 50, 9F12). Pure functions over what {@link PaxEmvBehavior} has read — no kernel call, no state —
 * so they are tested on their own ({@code PaxCardDataTest}).
 */
final class PaxCardData {

    private PaxCardData() { }

    /**
     * The PAN of a magstripe track 2: what comes before the separator ({@code '='}, or
     * {@code 'D'} as the chip's tag 57 writes it); the whole track if there is none.
     */
    static String panFromTrack2(String track2) {
        int sep = track2.indexOf('=');
        if (sep <= 0) {
            sep = track2.indexOf('D');
        }
        return sep > 0 ? track2.substring(0, sep) : track2;
    }

    /** The PAN of tag 5A (BCD), without its {@code F} padding — for a card with no track 2. */
    static String panFromTag(@Nullable byte[] pan5A) {
        String pan = ConvertUtils.bcd2Str(pan5A);
        int indexF = pan.indexOf('F');
        return pan.substring(0, indexF != -1 ? indexF : pan.length());
    }

    /**
     * The PAN with only its first 6 and last 4 digits shown; a PAN of 10 digits or fewer, or
     * {@code null} (the kernel hasn't read one), as it is.
     */
    @Nullable
    static String maskPan(@Nullable String pan) {
        if (pan == null || pan.length() <= 10) {
            return pan;
        }
        int visibleTail = 4;
        int visibleHead = 6;
        StringBuilder masked = new StringBuilder(pan.length());
        masked.append(pan, 0, visibleHead);
        for (int i = visibleHead; i < pan.length() - visibleTail; i++) {
            masked.append('*');
        }
        masked.append(pan.substring(pan.length() - visibleTail));
        return masked.toString();
    }

    /** An ASCII tag (cardholder name 5F20, application label 50…) as trimmed text. */
    static String asciiText(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII).trim();
    }
}
