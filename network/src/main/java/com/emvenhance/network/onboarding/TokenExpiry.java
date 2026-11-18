package com.emvenhance.network.onboarding;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the host's token expiration timestamp into epoch milliseconds.
 *
 * <p>This deliberately avoids {@code java.time}. The app's minSdk is 24, but {@code java.time}
 * only exists from API 26 and the project does not enable core library desugaring, so touching
 * {@code ZoneOffset} on an API 24/25 terminal (PAX Android 7) throws
 * {@code ClassNotFoundException: java.time.ZoneOffset}. Keep this class free of Android and
 * {@code java.time} types so it also stays unit-testable on the plain JVM.
 *
 * <p>Accepted input is the same as {@code ISO_OFFSET_DATE_TIME} with a fallback to
 * {@code ISO_LOCAL_DATE_TIME}: {@code yyyy-MM-dd'T'HH:mm[:ss[.f{1,9}]]} followed by an optional
 * {@code Z} or {@code +HH:MM[:ss]} offset. A timestamp without an offset is read in
 * {@code defaultOffsetSeconds}. Unlike {@code java.time}, the year must be exactly four digits
 * (signed and 5+ digit years are rejected); no host sends those.
 */
final class TokenExpiry {

    /** Same range as {@code java.time.ZoneOffset}: +/-18:00. */
    private static final int MAX_OFFSET_SECONDS = 18 * 60 * 60;

    private static final Pattern ISO_DATE_TIME = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,9}))?)?"
                    + "(?:(Z)|([+-])(\\d{2}):(\\d{2})(?::(\\d{2}))?)?",
            Pattern.CASE_INSENSITIVE);

    private TokenExpiry() {
    }

    /**
     * @param text                 ISO-8601 date-time, already trimmed
     * @param defaultOffsetSeconds offset applied when {@code text} carries none
     * @return milliseconds since 1970-01-01T00:00:00Z (sub-millisecond digits are dropped)
     * @throws IllegalArgumentException if {@code text} is not a valid date-time
     */
    static long parseEpochMillis(String text, int defaultOffsetSeconds) {
        Matcher m = ISO_DATE_TIME.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("Not an ISO-8601 date-time: " + text);
        }

        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        int hour = Integer.parseInt(m.group(4));
        int minute = Integer.parseInt(m.group(5));
        int second = m.group(6) == null ? 0 : Integer.parseInt(m.group(6));
        if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)
                || hour > 23 || minute > 59 || second > 59) {
            throw new IllegalArgumentException("Date-time field out of range: " + text);
        }

        int offsetSeconds;
        if (m.group(8) != null) {
            offsetSeconds = 0;
        } else if (m.group(9) != null) {
            int offHours = Integer.parseInt(m.group(10));
            int offMinutes = Integer.parseInt(m.group(11));
            int offSeconds = m.group(12) == null ? 0 : Integer.parseInt(m.group(12));
            offsetSeconds = offHours * 3600 + offMinutes * 60 + offSeconds;
            if (offMinutes > 59 || offSeconds > 59 || offsetSeconds > MAX_OFFSET_SECONDS) {
                throw new IllegalArgumentException("Offset out of range: " + text);
            }
            if ("-".equals(m.group(9))) {
                offsetSeconds = -offsetSeconds;
            }
        } else {
            offsetSeconds = defaultOffsetSeconds;
        }

        long epochSeconds = epochDay(year, month, day) * 86_400L
                + hour * 3600L + minute * 60L + second - offsetSeconds;
        long millis = m.group(7) == null ? 0 : Integer.parseInt((m.group(7) + "00").substring(0, 3));
        return epochSeconds * 1000L + millis;
    }

    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    private static int daysInMonth(int year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /** Days since 1970-01-01 in the proleptic Gregorian calendar (Hinnant's days_from_civil). */
    private static long epochDay(int year, int month, int day) {
        long y = month <= 2 ? year - 1L : year;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yearOfEra = y - era * 400;
        long dayOfYear = (153L * (month > 2 ? month - 3 : month + 9) + 2) / 5 + day - 1;
        long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * 146_097 + dayOfEra - 719_468;
    }
}
