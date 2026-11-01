package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.pax.commonlib.utils.ConvertUtils;
import java.nio.charset.StandardCharsets;

final class PaxCardData {

    private PaxCardData() { }

    static String panFromTrack2(String track2) {
        int sep = track2.indexOf('=');
        if (sep <= 0) {
            sep = track2.indexOf('D');
        }
        return sep > 0 ? track2.substring(0, sep) : track2;
    }

    static String panFromTag(@Nullable byte[] pan5A) {
        String pan = ConvertUtils.bcd2Str(pan5A);
        int indexF = pan.indexOf('F');
        return pan.substring(0, indexF != -1 ? indexF : pan.length());
    }

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

    static String asciiText(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII).trim();
    }
}
