package com.c203.limit.domain.inspection.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockOcrClientTests {

    private static final String IMAGE_URL = "https://cdn.example.com/evidence/1.jpg";

    private final MockOcrClient client = new MockOcrClient();

    @Test
    void extractsSampleValuesOnlyForRequestedFieldTypes() {
        List<OcrFieldExtraction> extractions =
                client.extractFields(
                        IMAGE_URL,
                        "image/jpeg",
                        EnumSet.of(OcrFieldType.MODEL_NAME, OcrFieldType.RAM));

        assertThat(extractions)
                .extracting(OcrFieldExtraction::fieldType, OcrFieldExtraction::parsedValue)
                .containsExactlyInAnyOrder(
                        tuple(OcrFieldType.MODEL_NAME, "Galaxy Book4 Pro"),
                        tuple(OcrFieldType.RAM, "16 GB"));
    }

    @Test
    void reportsFixedConfidenceAndTrimmedParsedValue() {
        List<OcrFieldExtraction> extractions =
                client.extractFields(IMAGE_URL, "image/jpeg", Set.of(OcrFieldType.CPU));

        assertThat(extractions).hasSize(1);
        OcrFieldExtraction extraction = extractions.getFirst();
        assertThat(extraction.rawText()).isEqualTo("Intel Core Ultra 7");
        assertThat(extraction.parsedValue()).isEqualTo("Intel Core Ultra 7");
        assertThat(extraction.confidence()).isEqualByComparingTo("0.900");
    }

    @Test
    void skipsFieldTypesWithoutSampleData() {
        List<OcrFieldExtraction> extractions =
                client.extractFields(
                        IMAGE_URL, "image/jpeg", EnumSet.of(OcrFieldType.OTHER, OcrFieldType.GPU));

        assertThat(extractions)
                .extracting(OcrFieldExtraction::fieldType)
                .containsExactly(OcrFieldType.GPU);
    }

    @Test
    void returnsEmptyListWhenNoFieldTypeIsExpected() {
        assertThat(client.extractFields(IMAGE_URL, "image/jpeg", Set.of())).isEmpty();
    }

    @Test
    void returnsSampleValuesWithoutTouchingImageUrlOrMimeType() {
        List<OcrFieldExtraction> extractions =
                client.extractFields(null, null, EnumSet.of(OcrFieldType.OS_VERSION));

        assertThat(extractions)
                .extracting(OcrFieldExtraction::parsedValue)
                .containsExactly("Windows 11 Pro");
    }

    @Test
    void coversEveryKnownFieldTypeExceptOther() {
        List<OcrFieldExtraction> extractions =
                client.extractFields(IMAGE_URL, "image/jpeg", EnumSet.allOf(OcrFieldType.class));

        assertThat(extractions)
                .extracting(OcrFieldExtraction::fieldType)
                .containsExactlyInAnyOrder(
                        OcrFieldType.MODEL_NAME,
                        OcrFieldType.STORAGE_CAPACITY,
                        OcrFieldType.OS_VERSION,
                        OcrFieldType.CPU,
                        OcrFieldType.RAM,
                        OcrFieldType.GPU);
    }

    @Test
    void exposesMockModelVersion() {
        assertThat(client.getModelVersion()).isEqualTo("mock-v1");
    }
}
