package com.c203.limit.domain.inspection.util;

import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OCR로 인식한 parsedValue의 단위·표기를 필드 타입별로 정규화한다. */
public final class UnitNormalizer {

    private static final Pattern STORAGE_PATTERN =
            Pattern.compile("(?i)^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(GB|TB|MB)\\s*$");

    private UnitNormalizer() {}

    public static String normalize(OcrFieldType fieldType, String rawParsedValue) {
        if (rawParsedValue == null) {
            return null;
        }
        String trimmed = rawParsedValue.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return switch (fieldType) {
            case STORAGE_CAPACITY -> normalizeStorageCapacity(trimmed);
            default -> trimmed;
        };
    }

    private static String normalizeStorageCapacity(String value) {
        Matcher matcher = STORAGE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        return matcher.group(1) + matcher.group(2).toUpperCase();
    }

    /** "67,010 mWh" 같은 표기에서 단위·구분자를 제거하고 mWh 정수값만 추출한다. */
    public static BigDecimal extractMilliwattHours(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String digits = rawValue.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : new BigDecimal(digits);
    }

    /** "16384MB RAM", "8156  MB" 처럼 숫자·단위 사이 표기가 들쭉날쭉한 값을 "16384 MB RAM"처럼 통일한다. */
    public static String normalizeUnitSpacing(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String collapsed = rawValue.trim().replaceAll("\\s+", " ");
        return collapsed.replaceAll("(?i)(\\d)(GB|TB|MB|KB)\\b", "$1 $2");
    }
}
