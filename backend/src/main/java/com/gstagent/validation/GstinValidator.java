package com.gstagent.validation;

import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * GSTIN structure: 2-digit state code + 10-char PAN + entity number + 'Z' + checksum.
 * The checksum is the GSTN's mod-36 Luhn variant over the first 14 characters.
 */
public final class GstinValidator {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static final Map<String, String> STATES = Map.ofEntries(
            entry("01", "Jammu and Kashmir"), entry("02", "Himachal Pradesh"), entry("03", "Punjab"),
            entry("04", "Chandigarh"), entry("05", "Uttarakhand"), entry("06", "Haryana"), entry("07", "Delhi"),
            entry("08", "Rajasthan"), entry("09", "Uttar Pradesh"), entry("10", "Bihar"), entry("11", "Sikkim"),
            entry("12", "Arunachal Pradesh"), entry("13", "Nagaland"), entry("14", "Manipur"), entry("15", "Mizoram"),
            entry("16", "Tripura"), entry("17", "Meghalaya"), entry("18", "Assam"), entry("19", "West Bengal"),
            entry("20", "Jharkhand"), entry("21", "Odisha"), entry("22", "Chhattisgarh"), entry("23", "Madhya Pradesh"),
            entry("24", "Gujarat"), entry("26", "Dadra and Nagar Haveli and Daman and Diu"), entry("27", "Maharashtra"),
            entry("29", "Karnataka"), entry("30", "Goa"), entry("31", "Lakshadweep"), entry("32", "Kerala"),
            entry("33", "Tamil Nadu"), entry("34", "Puducherry"), entry("35", "Andaman and Nicobar Islands"),
            entry("36", "Telangana"), entry("37", "Andhra Pradesh"), entry("38", "Ladakh"),
            entry("97", "Other Territory"), entry("99", "Centre Jurisdiction"));

    private GstinValidator() {
    }

    public static boolean isValidFormat(String gstin) {
        return gstin != null && FORMAT.matcher(gstin).matches();
    }

    public static boolean isValidStateCode(String gstin) {
        return gstin != null && gstin.length() >= 2 && STATES.containsKey(gstin.substring(0, 2));
    }

    public static boolean isValidChecksum(String gstin) {
        return isValidFormat(gstin) && checksumChar(gstin.substring(0, 14)) == gstin.charAt(14);
    }

    public static boolean isValid(String gstin) {
        return isValidFormat(gstin) && isValidStateCode(gstin) && isValidChecksum(gstin);
    }

    public static String stateName(String stateCode) {
        return STATES.get(stateCode);
    }

    public static char checksumChar(String first14) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int value = CHARSET.indexOf(first14.charAt(i));
            int product = value * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return CHARSET.charAt((36 - sum % 36) % 36);
    }

    /** Uppercases and strips whitespace/punctuation that OCR commonly introduces. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.toUpperCase().replaceAll("[^0-9A-Z]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
