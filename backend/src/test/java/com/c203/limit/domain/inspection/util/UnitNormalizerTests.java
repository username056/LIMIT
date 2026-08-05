package com.c203.limit.domain.inspection.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** OCR 파싱값 정규화의 각 분기(null/blank 처리, 저장용량 단위 정규화, mWh 추출, 단위 간격 통일)를 확인한다. */
class UnitNormalizerTests {

    @Test
    void normalizeReturnsNullForNullInput() {
        assertThat(UnitNormalizer.normalize(OcrFieldType.STORAGE_CAPACITY, null)).isNull();
    }

    @Test
    void normalizeReturnsEmptyStringForBlankInputWithoutNormalizing() {
        assertThat(UnitNormalizer.normalize(OcrFieldType.STORAGE_CAPACITY, "   ")).isEmpty();
    }

    @Test
    void normalizeStorageCapacityUppercasesUnitAndStripsWhitespace() {
        assertThat(UnitNormalizer.normalize(OcrFieldType.STORAGE_CAPACITY, " 512 gb ")).isEqualTo("512GB");
        assertThat(UnitNormalizer.normalize(OcrFieldType.STORAGE_CAPACITY, "1.5TB")).isEqualTo("1.5TB");
    }

    @Test
    void normalizeStorageCapacityReturnsOriginalTrimmedValueWhenPatternDoesNotMatch() {
        // 패턴에 맞지 않는 값(단위 없음 등)은 원본을 trim만 해서 그대로 돌려준다.
        assertThat(UnitNormalizer.normalize(OcrFieldType.STORAGE_CAPACITY, " 512 gigabytes "))
                .isEqualTo("512 gigabytes");
    }

    @Test
    void normalizeReturnsTrimmedValueUnchangedForNonStorageFieldTypes() {
        assertThat(UnitNormalizer.normalize(OcrFieldType.CPU, " i7-1165G7 ")).isEqualTo("i7-1165G7");
        assertThat(UnitNormalizer.normalize(OcrFieldType.OTHER, " some value ")).isEqualTo("some value");
    }

    @Test
    void extractMilliwattHoursReturnsNullForNullInput() {
        assertThat(UnitNormalizer.extractMilliwattHours(null)).isNull();
    }

    @Test
    void extractMilliwattHoursStripsSeparatorsAndUnitLabel() {
        assertThat(UnitNormalizer.extractMilliwattHours("67,010 mWh")).isEqualByComparingTo(new BigDecimal("67010"));
    }

    @Test
    void extractMilliwattHoursReturnsNullWhenNoDigitsArePresent() {
        assertThat(UnitNormalizer.extractMilliwattHours("mWh only")).isNull();
    }

    @Test
    void normalizeUnitSpacingReturnsNullForNullInput() {
        assertThat(UnitNormalizer.normalizeUnitSpacing(null)).isNull();
    }

    @Test
    void normalizeUnitSpacingInsertsSpaceBetweenDigitAndUnitAndCollapsesWhitespace() {
        assertThat(UnitNormalizer.normalizeUnitSpacing("16384MB   RAM")).isEqualTo("16384 MB RAM");
        assertThat(UnitNormalizer.normalizeUnitSpacing("8156  MB")).isEqualTo("8156 MB");
    }

    @Test
    void normalizeUnitSpacingIsCaseInsensitiveForUnitLabels() {
        assertThat(UnitNormalizer.normalizeUnitSpacing("512gb")).isEqualTo("512 gb");
    }
}
