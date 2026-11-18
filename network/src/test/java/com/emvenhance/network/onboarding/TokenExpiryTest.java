package com.emvenhance.network.onboarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Random;

/**
 * {@link TokenExpiry} must not use java.time (API 26+, crashes on API 24/25 terminals), but this
 * test runs on the host JVM, so it uses java.time as the reference the old implementation used.
 */
public class TokenExpiryTest {

    private static final int UTC_PLUS_3 = 3 * 3600;

    @Test
    public void zuluIsUtc() {
        assertEquals(0L, parse("1970-01-01T00:00:00Z"));
        assertEquals(1_714_564_800_000L, parse("2024-05-01T12:00:00Z"));
    }

    @Test
    public void explicitOffsetWinsOverDefault() {
        assertEquals(0L, parse("1970-01-01T03:00:00+03:00"));
        assertEquals(parse("2024-05-01T12:00:00Z"), parse("2024-05-01T14:00:00+02:00"));
        assertEquals(parse("2024-05-01T12:00:00Z"), parse("2024-05-01T07:00:00-05:00"));
    }

    @Test
    public void missingOffsetIsReadInDefaultOffset() {
        assertEquals(0L, parse("1970-01-01T03:00:00"));
        assertEquals(parse("2024-05-01T12:00:00Z"), parse("2024-05-01T15:00:00"));
    }

    @Test
    public void secondsAndFractionAreOptional() {
        assertEquals(parse("2024-05-01T12:30:00Z"), parse("2024-05-01T12:30Z"));
        assertEquals(500L, parse("1970-01-01T00:00:00.5Z"));
        assertEquals(123L, parse("1970-01-01T00:00:00.123456789Z"));
        assertEquals(-1L, parse("1969-12-31T23:59:59.999Z"));
    }

    @Test
    public void leapDays() {
        assertEquals(parse("2024-03-01T00:00:00Z") - 86_400_000L, parse("2024-02-29T00:00:00Z"));
        assertInvalid("2023-02-29T00:00:00Z");
        assertInvalid("1900-02-29T00:00:00Z");
        parse("2000-02-29T00:00:00Z");
    }

    @Test
    public void rejectsMalformedAndOutOfRange() {
        assertInvalid("");
        assertInvalid("not a date");
        assertInvalid("2024-05-01");
        assertInvalid("2024-05-01 12:00:00Z");
        assertInvalid("2024-13-01T00:00:00Z");
        assertInvalid("2024-00-10T00:00:00Z");
        assertInvalid("2024-04-31T00:00:00Z");
        assertInvalid("2024-05-01T24:00:00Z");
        assertInvalid("2024-05-01T12:60:00Z");
        assertInvalid("2024-05-01T12:00:60Z");
        assertInvalid("2024-05-01T12:00:00+19:00");
        assertInvalid("2024-05-01T12:00:00+03");
        assertInvalid("2024-05-01T12:00:00+0300");
        assertInvalid("2024-05-01T12:00:00+03:60");
        assertInvalid("2024-05-01T12:00:00Zjunk");
    }

    @Test
    public void rejectsSignedAndExtendedYears() {
        assertInvalid("-2024-05-01T12:00:00Z");
        assertInvalid("+2024-05-01T12:00:00Z");
        assertInvalid("12024-05-01T12:00:00Z");
    }

    @Test
    public void matchesJavaTimeOnGeneratedValidInput() {
        Random random = new Random(42);
        String[] offsets = {"", "Z", "+00:00", "+03:00", "-05:00", "+05:30", "+14:00", "-12:00",
                "+18:00", "-18:00", "+03:00:30"};
        for (int i = 0; i < 20_000; i++) {
            int year = 1 + random.nextInt(9999);
            int month = 1 + random.nextInt(12);
            int day = 1 + random.nextInt(28 + random.nextInt(4));
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                    "%04d-%02d-%02dT%02d:%02d", year, month, day, random.nextInt(24), random.nextInt(60)));
            if (random.nextBoolean()) {
                sb.append(String.format(Locale.ROOT, ":%02d", random.nextInt(60)));
                if (random.nextBoolean()) {
                    int digits = 1 + random.nextInt(9);
                    sb.append('.');
                    for (int d = 0; d < digits; d++) {
                        sb.append((char) ('0' + random.nextInt(10)));
                    }
                }
            }
            sb.append(offsets[random.nextInt(offsets.length)]);
            assertSameAsJavaTime(sb.toString());
        }
    }

    @Test
    public void agreesWithJavaTimeOnAcceptingAndRejectingMutatedInput() {
        Random random = new Random(7);
        String alphabet = "0123456789-:+.TZz ";
        String seed = "2024-05-01T12:34:56.789+03:00";
        for (int i = 0; i < 30_000; i++) {
            StringBuilder sb = new StringBuilder(seed);
            int edits = 1 + random.nextInt(3);
            for (int e = 0; e < edits && sb.length() > 0; e++) {
                int pos = random.nextInt(sb.length());
                switch (random.nextInt(3)) {
                    case 0:
                        sb.deleteCharAt(pos);
                        break;
                    case 1:
                        sb.setCharAt(pos, alphabet.charAt(random.nextInt(alphabet.length())));
                        break;
                    default:
                        sb.insert(pos, alphabet.charAt(random.nextInt(alphabet.length())));
                        break;
                }
            }
            assertSameAsJavaTime(sb.toString());
        }
    }

    private static long parse(String text) {
        return TokenExpiry.parseEpochMillis(text, UTC_PLUS_3);
    }

    private static void assertInvalid(String text) {
        try {
            parse(text);
            fail("expected IllegalArgumentException for: " + text);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    /** The previous implementation, kept only as the reference. Returns null when it rejects. */
    private static Long referenceMillis(String expiration) {
        String normalized = expiration;
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "+00:00";
        }
        try {
            try {
                return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .toInstant(ZoneOffset.ofHours(3)).toEpochMilli();
            }
        } catch (DateTimeParseException rejected) {
            return null;
        }
    }

    /**
     * java.time also accepts signed and 5+ digit years ("-2024-...", "+12024-..."). No host sends
     * those and TokenExpiry intentionally rejects them, so they are excluded from the comparison
     * and pinned by {@link #rejectsSignedAndExtendedYears()} instead.
     */
    private static void assertSameAsJavaTime(String text) {
        if (!text.matches("(?s)\\d{4}-.*")) {
            return;
        }
        Long expected = referenceMillis(text);
        Long actual;
        try {
            actual = parse(text);
        } catch (IllegalArgumentException rejected) {
            actual = null;
        }
        assertEquals("input: [" + text + "]", expected, actual);
    }
}
