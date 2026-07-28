package com.c203.limit.domain.inspection.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.parser.SystemInfoScreenshotParser;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NaverClovaSystemInfoOcrClientTests {

    private static final String IMAGE_URL = "https://cdn.example.com/evidence/9003.jpg";
    private static final byte[] ORIGINAL_BYTES = {1, 2, 3};
    private static final byte[] PREPROCESSED_BYTES = {4, 5, 6};
    private static final Set<OcrFieldType> SCREENSHOT_FIELD_TYPES =
            Set.of(
                    OcrFieldType.MODEL_NAME,
                    OcrFieldType.CPU,
                    OcrFieldType.RAM,
                    OcrFieldType.GPU,
                    OcrFieldType.STORAGE_CAPACITY,
                    OcrFieldType.OS_VERSION);

    @Mock NaverClovaOcrClient naverClovaOcrClient;
    @Mock SystemInfoScreenshotParser parser;
    @Mock OcrImagePreprocessor imagePreprocessor;

    NaverClovaSystemInfoOcrClient client;

    @BeforeEach
    void setUp() {
        client = new NaverClovaSystemInfoOcrClient(naverClovaOcrClient, parser, imagePreprocessor);
    }

    private OcrFieldExtraction extraction(OcrFieldType fieldType, String value) {
        return new OcrFieldExtraction(fieldType, value, value, new BigDecimal("0.900"));
    }

    @Test
    void resolvesJpegFormatAndDelegatesToParser() {
        List<OcrToken> tokens = List.of(new OcrToken("저장소", new BigDecimal("0.9"), 0, 0, 50, 20));
        List<OcrFieldExtraction> parsed = List.of(extraction(OcrFieldType.STORAGE_CAPACITY, "954GB"));
        when(naverClovaOcrClient.fetchImage(IMAGE_URL)).thenReturn(ORIGINAL_BYTES);
        when(naverClovaOcrClient.recognizeFields(ORIGINAL_BYTES, "jpg")).thenReturn(tokens);
        when(parser.parse(tokens, Set.of(OcrFieldType.STORAGE_CAPACITY))).thenReturn(parsed);

        List<OcrFieldExtraction> result =
                client.extractFields(IMAGE_URL, "image/jpeg", Set.of(OcrFieldType.STORAGE_CAPACITY));

        assertThat(result).isEqualTo(parsed);
        verifyNoInteractions(imagePreprocessor);
    }

    @Test
    void resolvesPngFormat() {
        when(naverClovaOcrClient.fetchImage(IMAGE_URL)).thenReturn(ORIGINAL_BYTES);
        when(naverClovaOcrClient.recognizeFields(ORIGINAL_BYTES, "png")).thenReturn(List.of());
        when(parser.parse(List.of(), Set.of(OcrFieldType.CPU))).thenReturn(List.of(extraction(OcrFieldType.CPU, "i7")));

        client.extractFields(IMAGE_URL, "image/png", Set.of(OcrFieldType.CPU));

        verify(naverClovaOcrClient).recognizeFields(ORIGINAL_BYTES, "png");
    }

    @Test
    void throwsUnsupportedImageFormatForUnknownMimeType() {
        assertThatThrownBy(() -> client.extractFields(IMAGE_URL, "application/pdf", Set.of(OcrFieldType.CPU)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT));
        verifyNoInteractions(naverClovaOcrClient, parser, imagePreprocessor);
    }

    @Test
    void throwsUnsupportedImageFormatWhenMimeTypeIsNull() {
        assertThatThrownBy(() -> client.extractFields(IMAGE_URL, null, Set.of(OcrFieldType.CPU)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT));
        verifyNoInteractions(naverClovaOcrClient, parser, imagePreprocessor);
    }

    @Test
    void modelVersionIsAFixedConstant() {
        assertThat(client.getModelVersion()).isEqualTo("naver-clova-general-v2");
    }

    @Test
    void doesNotRetryWhenFewerThanThreeFieldsAreMissing() {
        // 6개 기대 필드 중 4개 찾음(2개만 누락, 임계값 3 미만) -> 재시도 안 함
        List<OcrFieldExtraction> firstPass =
                List.of(
                        extraction(OcrFieldType.MODEL_NAME, "Galaxy Book4"),
                        extraction(OcrFieldType.CPU, "i7"),
                        extraction(OcrFieldType.RAM, "16GB"),
                        extraction(OcrFieldType.GPU, "RTX 4050"));
        when(naverClovaOcrClient.fetchImage(IMAGE_URL)).thenReturn(ORIGINAL_BYTES);
        when(naverClovaOcrClient.recognizeFields(ORIGINAL_BYTES, "jpg")).thenReturn(List.of());
        when(parser.parse(List.of(), SCREENSHOT_FIELD_TYPES)).thenReturn(firstPass);

        List<OcrFieldExtraction> result = client.extractFields(IMAGE_URL, "image/jpeg", SCREENSHOT_FIELD_TYPES);

        assertThat(result).isEqualTo(firstPass);
        verifyNoInteractions(imagePreprocessor);
        verify(naverClovaOcrClient, times(1)).recognizeFields(any(byte[].class), eq("jpg"));
    }

    @Test
    void retriesWithPreprocessingAndFillsOnlyMissingFieldsWhenThreeOrMoreAreMissing() {
        // 6개 중 3개만 찾음(3개 누락, 임계값 경계) -> 전처리 재시도, 1차에서 찾은 값은 유지하고 빠진 것만 채움
        List<OcrFieldExtraction> firstPass =
                List.of(
                        extraction(OcrFieldType.CPU, "i7 (1차)"),
                        extraction(OcrFieldType.RAM, "16GB"),
                        extraction(OcrFieldType.OS_VERSION, "Windows 11"));
        List<OcrFieldExtraction> retryPass =
                List.of(
                        extraction(OcrFieldType.CPU, "i7 (재시도, 무시되어야 함)"),
                        extraction(OcrFieldType.GPU, "RTX 4050"),
                        extraction(OcrFieldType.MODEL_NAME, "Galaxy Book4"));

        when(naverClovaOcrClient.fetchImage(IMAGE_URL)).thenReturn(ORIGINAL_BYTES);
        when(naverClovaOcrClient.recognizeFields(ORIGINAL_BYTES, "jpg")).thenReturn(List.of());
        when(imagePreprocessor.enhanceContrast(ORIGINAL_BYTES)).thenReturn(PREPROCESSED_BYTES);
        when(naverClovaOcrClient.recognizeFields(PREPROCESSED_BYTES, "png")).thenReturn(List.of());
        // parser.parse는 두 번(1차, 재시도) 같은 인자로 호출되므로 호출 순서대로 다른 값을 반환하게 한다.
        when(parser.parse(List.of(), SCREENSHOT_FIELD_TYPES)).thenReturn(firstPass).thenReturn(retryPass);

        List<OcrFieldExtraction> result = client.extractFields(IMAGE_URL, "image/jpeg", SCREENSHOT_FIELD_TYPES);

        assertThat(result)
                .extracting(OcrFieldExtraction::fieldType, OcrFieldExtraction::parsedValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(OcrFieldType.CPU, "i7 (1차)"),
                        org.assertj.core.groups.Tuple.tuple(OcrFieldType.RAM, "16GB"),
                        org.assertj.core.groups.Tuple.tuple(OcrFieldType.OS_VERSION, "Windows 11"),
                        org.assertj.core.groups.Tuple.tuple(OcrFieldType.GPU, "RTX 4050"),
                        org.assertj.core.groups.Tuple.tuple(OcrFieldType.MODEL_NAME, "Galaxy Book4"));
        verify(naverClovaOcrClient).recognizeFields(PREPROCESSED_BYTES, "png");
    }

    @Test
    void fallsBackToFirstPassResultWhenPreprocessingRetryFails() {
        List<OcrFieldExtraction> firstPass = List.of(extraction(OcrFieldType.CPU, "i7"));
        when(naverClovaOcrClient.fetchImage(IMAGE_URL)).thenReturn(ORIGINAL_BYTES);
        when(naverClovaOcrClient.recognizeFields(ORIGINAL_BYTES, "jpg")).thenReturn(List.of());
        when(parser.parse(List.of(), SCREENSHOT_FIELD_TYPES)).thenReturn(firstPass);
        when(imagePreprocessor.enhanceContrast(ORIGINAL_BYTES))
                .thenThrow(new IllegalArgumentException("cannot decode"));

        List<OcrFieldExtraction> result = client.extractFields(IMAGE_URL, "image/jpeg", SCREENSHOT_FIELD_TYPES);

        assertThat(result).isEqualTo(firstPass);
        verify(naverClovaOcrClient, never()).recognizeFields(any(byte[].class), eq("png"));
    }
}
