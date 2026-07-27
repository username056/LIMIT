package com.c203.limit.domain.inspection.util;

import com.c203.limit.domain.inspection.enums.OcrFieldType;
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
}
