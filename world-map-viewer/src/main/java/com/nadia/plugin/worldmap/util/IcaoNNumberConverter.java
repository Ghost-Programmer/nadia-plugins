package com.nadia.plugin.worldmap.util;

import java.util.Locale;

/**
 * High-performance algorithmic converter between United States civil aircraft
 * 24-bit ICAO
 * Mode S transponder hexadecimal addresses and FAA N-Numbers (tail numbers).
 *
 * <p>
 * United States aircraft registrations (N-numbers) are assigned by the Federal
 * Aviation Administration
 * (FAA) in a deterministic sequential mapping to 24-bit Mode S transponder
 * addresses within the allocated
 * block {@code 0xA00001} (corresponding to {@code N1}) through {@code 0xADF7C7}
 * (corresponding to {@code N99999}).
 *
 * <p>
 * <b>Thread Safety &amp; Lifecycle:</b>
 * This utility consists purely of stateless static methods and immutable lookup
 * tables. It is inherently
 * thread-safe and can be invoked concurrently across arbitrary threads and
 * background scheduler workers.
 */
public final class IcaoNNumberConverter {

    private static final int ICAO_SIZE = 6;
    private static final int NNUMBER_MAX_SIZE = 6;

    /** Characters permitted in FAA suffix assignments (excluding 'I' and 'O'). */
    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** Decimal digit characters permitted in FAA registration numbers. */
    private static final String DIGITSET = "0123456789";

    /** Hexadecimal character set for ICAO Mode S validation. */
    private static final String HEXSET = "0123456789ABCDEF";

    /** Combined valid character set for tail number parsing. */
    private static final String ALLCHARS = CHARSET + DIGITSET;

    /** Suffix block permutation size: 1 + 24 * (1 + 24) = 601. */
    private static final int SUFFIX_SIZE = 1 + CHARSET.length() * (1 + CHARSET.length());

    /** Bucket 4 size: 1 + 24 + 10 = 35. */
    private static final int BUCKET4_SIZE = 1 + CHARSET.length() + DIGITSET.length();

    /** Bucket 3 size: 10 * 35 + 601 = 951. */
    private static final int BUCKET3_SIZE = DIGITSET.length() * BUCKET4_SIZE + SUFFIX_SIZE;

    /** Bucket 2 size: 10 * 951 + 601 = 10,111. */
    private static final int BUCKET2_SIZE = DIGITSET.length() * BUCKET3_SIZE + SUFFIX_SIZE;

    /** Bucket 1 size: 10 * 10,111 + 601 = 101,711. */
    private static final int BUCKET1_SIZE = DIGITSET.length() * BUCKET2_SIZE + SUFFIX_SIZE;

    /**
     * Private constructor to prevent instantiation of static utility class.
     */
    private IcaoNNumberConverter() {
        // Utility class
    }

    /**
     * Converts a 6-character hexadecimal ICAO 24-bit Mode S transponder address
     * into its corresponding
     * US FAA tail number (N-Number).
     *
     * @param icao the 6-character hexadecimal ICAO address (e.g. {@code "AC6F2D"}
     *             or {@code "a00001"})
     * @return the uppercase FAA N-number (e.g. {@code "N900AA"}), or {@code null}
     *         if the input is invalid
     *         or outside the US Mode S allocation range (A00001 - ADF7C7)
     */
    public static String icaoToNNumber(String icao) {
        if (icao == null) {
            return null;
        }

        String trimmed = icao.trim().toUpperCase(Locale.ROOT);
        if (trimmed.startsWith("0X")) {
            trimmed = trimmed.substring(2);
        }

        if (trimmed.length() != ICAO_SIZE || trimmed.charAt(0) != 'A') {
            return null;
        }

        for (int i = 0; i < trimmed.length(); i++) {
            if (HEXSET.indexOf(trimmed.charAt(i)) < 0) {
                return null;
            }
        }

        int val;
        try {
            val = Integer.parseInt(trimmed.substring(1), 16) - 1;
        } catch (NumberFormatException e) {
            return null;
        }

        if (val < 0) {
            return "N";
        }

        StringBuilder output = new StringBuilder("N");

        int dig1 = (val / BUCKET1_SIZE) + 1;
        int rem1 = val % BUCKET1_SIZE;
        output.append(dig1);

        if (rem1 < SUFFIX_SIZE) {
            return output.append(getSuffix(rem1)).toString();
        }

        rem1 -= SUFFIX_SIZE;
        int dig2 = rem1 / BUCKET2_SIZE;
        int rem2 = rem1 % BUCKET2_SIZE;
        output.append(dig2);

        if (rem2 < SUFFIX_SIZE) {
            return output.append(getSuffix(rem2)).toString();
        }

        rem2 -= SUFFIX_SIZE;
        int dig3 = rem2 / BUCKET3_SIZE;
        int rem3 = rem2 % BUCKET3_SIZE;
        output.append(dig3);

        if (rem3 < SUFFIX_SIZE) {
            return output.append(getSuffix(rem3)).toString();
        }

        rem3 -= SUFFIX_SIZE;
        int dig4 = rem3 / BUCKET4_SIZE;
        int rem4 = rem3 % BUCKET4_SIZE;
        output.append(dig4);

        if (rem4 == 0) {
            return output.toString();
        }

        if (rem4 - 1 >= 0 && rem4 - 1 < ALLCHARS.length()) {
            output.append(ALLCHARS.charAt(rem4 - 1));
        }

        return output.toString();
    }

    /**
     * Converts a US FAA N-Number (tail number) into its corresponding 6-character
     * hexadecimal
     * ICAO 24-bit Mode S transponder address.
     *
     * @param nNumber the FAA N-number (e.g. {@code "N900AA"} or {@code "N1"})
     * @return the lowercase 6-character hexadecimal ICAO address (e.g.
     *         {@code "ac6f2d"}), or {@code null}
     *         if invalid or outside the valid FAA alphanumeric syntax
     */
    public static String nNumberToIcao(String nNumber) {
        if (nNumber == null) {
            return null;
        }

        String upper = nNumber.trim().toUpperCase(Locale.ROOT);
        if (upper.isEmpty() || upper.length() > NNUMBER_MAX_SIZE || upper.charAt(0) != 'N') {
            return null;
        }

        for (int i = 0; i < upper.length(); i++) {
            if (ALLCHARS.indexOf(upper.charAt(i)) < 0) {
                return null;
            }
        }

        if (upper.length() > 3) {
            for (int i = 1; i < upper.length() - 2; i++) {
                if (CHARSET.indexOf(upper.charAt(i)) >= 0) {
                    return null;
                }
            }
        }

        int count = 0;
        if (upper.length() > 1) {
            String body = upper.substring(1);
            count += 1;
            for (int i = 0; i < body.length(); i++) {
                char ch = body.charAt(i);
                if (i == NNUMBER_MAX_SIZE - 2) {
                    count += ALLCHARS.indexOf(ch) + 1;
                } else if (CHARSET.indexOf(ch) >= 0) {
                    Integer offset = suffixOffset(body.substring(i));
                    if (offset == null) {
                        return null;
                    }
                    count += offset;
                    break;
                } else {
                    int digit = ch - '0';
                    if (i == 0) {
                        count += (digit - 1) * BUCKET1_SIZE;
                    } else if (i == 1) {
                        count += digit * BUCKET2_SIZE + SUFFIX_SIZE;
                    } else if (i == 2) {
                        count += digit * BUCKET3_SIZE + SUFFIX_SIZE;
                    } else if (i == 3) {
                        count += digit * BUCKET4_SIZE + SUFFIX_SIZE;
                    }
                }
            }
        }

        return createIcao("a", count);
    }

    /**
     * Resolves the most accurate N-Number identifier from an aircraft's callsign
     * and Mode S hex address.
     *
     * @param callsign the flight callsign or identifier (e.g. {@code "N900AA"} or
     *                 {@code "AAL123"})
     * @param icaoHex  the Mode S 24-bit ICAO address hex string (e.g.
     *                 {@code "AC6F2D"})
     * @return the resolved US N-number with 'N' prefix, or {@code null} if no US
     *         registration matches
     */
    private static final java.util.regex.Pattern N_NUMBER_PATTERN = java.util.regex.Pattern.compile("^N[1-9][0-9A-Z]{0,4}$");

    public static String extractNNumber(String callsign, String icaoHex) {
        if (callsign != null) {
            String trimmedCallsign = callsign.trim().toUpperCase(Locale.ROOT);
            if (N_NUMBER_PATTERN.matcher(trimmedCallsign).matches()) {
                return trimmedCallsign;
            }
        }

        if (icaoHex != null) {
            String derived = icaoToNNumber(icaoHex);
            if (derived != null && !derived.equals("N")) {
                return derived;
            }
        }

        return null;
    }

    private static String getSuffix(int offset) {
        if (offset == 0) {
            return "";
        }
        int divisor = CHARSET.length() + 1;
        char char0 = CHARSET.charAt((offset - 1) / divisor);
        int rem = (offset - 1) % divisor;
        if (rem == 0) {
            return String.valueOf(char0);
        }
        return "" + char0 + CHARSET.charAt(rem - 1);
    }

    private static Integer suffixOffset(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        if (s.length() > 2) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (CHARSET.indexOf(s.charAt(i)) < 0) {
                return null;
            }
        }
        int count = (CHARSET.length() + 1) * CHARSET.indexOf(s.charAt(0)) + 1;
        if (s.length() == 2) {
            count += CHARSET.indexOf(s.charAt(1)) + 1;
        }
        return count;
    }

    private static String createIcao(String prefix, int val) {
        String hex = Integer.toHexString(val).toLowerCase(Locale.ROOT);
        int len = prefix.length() + hex.length();
        if (len > ICAO_SIZE) {
            return null;
        }
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < ICAO_SIZE - len; i++) {
            sb.append('0');
        }
        sb.append(hex);
        return sb.toString();
    }
}
