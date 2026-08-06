package com.c203.limit.domain.inspection.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiagnosisFieldNameTests {

    private static final Set<DiagnosisFieldName> OCR_BACKED_FIELDS = EnumSet.of(
            DiagnosisFieldName.CPU,
            DiagnosisFieldName.RAM,
            DiagnosisFieldName.GPU,
            DiagnosisFieldName.MODEL_NAME,
            DiagnosisFieldName.OS_VERSION,
            DiagnosisFieldName.STORAGE_CAPACITY);

    @Test
    void mapsEveryOcrFieldTypeExceptOtherToMatchingDiagnosisField() {
        assertThat(DiagnosisFieldName.fromOcrFieldType(OcrFieldType.CPU))
                .isEqualTo(DiagnosisFieldName.CPU);
        assertThat(DiagnosisFieldName.fromOcrFieldType(OcrFieldType.RAM))
                .isEqualTo(DiagnosisFieldName.RAM);
        assertThat(DiagnosisFieldName.fromOcrFieldType(OcrFieldType.GPU))
                .isEqualTo(DiagnosisFieldName.GPU);
        assertThat(DiagnosisFieldName.fromOcrFieldType(OcrFieldType.MODEL_NAME))
                .isEqualTo(DiagnosisFieldName.MODEL_NAME);
        assertThat(DiagnosisFieldName.fromOcrFieldType(OcrFieldType.OS_VERSION))
                .isEqualTo(DiagnosisFieldName.OS_VERSION);
        assertThat(DiagnosisFieldName.fromOcrFieldType(OcrFieldType.STORAGE_CAPACITY))
                .isEqualTo(DiagnosisFieldName.STORAGE_CAPACITY);
    }

    @Test
    void mapsUnclassifiedOcrFieldTypeToNull() {
        assertThat(DiagnosisFieldName.fromOcrFieldType(OcrFieldType.OTHER)).isNull();
    }

    @Test
    void reverseMappingIsNullForFieldsThatExistOnlyInDiagnosisFiles() {
        for (DiagnosisFieldName fieldName : DiagnosisFieldName.values()) {
            if (OCR_BACKED_FIELDS.contains(fieldName)) {
                assertThat(fieldName.toOcrFieldType()).isNotNull();
            } else {
                assertThat(fieldName.toOcrFieldType()).isNull();
            }
        }
    }

    @Test
    void reverseMappingRoundTripsForOcrBackedFields() {
        for (DiagnosisFieldName fieldName : OCR_BACKED_FIELDS) {
            OcrFieldType ocrFieldType = fieldName.toOcrFieldType();

            assertThat(ocrFieldType.name()).isEqualTo(fieldName.name());
            assertThat(DiagnosisFieldName.fromOcrFieldType(ocrFieldType)).isEqualTo(fieldName);
        }
    }
}
