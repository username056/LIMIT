package com.c203.limit.domain.inspection.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock NaverClovaOcrClient naverClovaOcrClient;
    @Mock SystemInfoScreenshotParser parser;

    NaverClovaSystemInfoOcrClient client;

    @BeforeEach
    void setUp() {
        client = new NaverClovaSystemInfoOcrClient(naverClovaOcrClient, parser);
    }

    @Test
    void resolvesJpegFormatAndDelegatesToParser() {
        List<OcrToken> tokens = List.of(new OcrToken("저장소", new BigDecimal("0.9"), 0, 0, 50, 20));
        List<OcrFieldExtraction> parsed =
                List.of(new OcrFieldExtraction(OcrFieldType.STORAGE_CAPACITY, "954 GB", "954GB", new BigDecimal("0.9")));
        when(naverClovaOcrClient.recognizeFields(IMAGE_URL, "jpg")).thenReturn(tokens);
        when(parser.parse(tokens, Set.of(OcrFieldType.STORAGE_CAPACITY))).thenReturn(parsed);

        List<OcrFieldExtraction> result =
                client.extractFields(IMAGE_URL, "image/jpeg", Set.of(OcrFieldType.STORAGE_CAPACITY));

        assertThat(result).isEqualTo(parsed);
    }

    @Test
    void resolvesPngFormat() {
        when(naverClovaOcrClient.recognizeFields(IMAGE_URL, "png")).thenReturn(List.of());
        when(parser.parse(List.of(), Set.of(OcrFieldType.CPU))).thenReturn(List.of());

        client.extractFields(IMAGE_URL, "image/png", Set.of(OcrFieldType.CPU));

        org.mockito.Mockito.verify(naverClovaOcrClient).recognizeFields(IMAGE_URL, "png");
    }

    @Test
    void throwsUnsupportedImageFormatForUnknownMimeType() {
        assertThatThrownBy(() -> client.extractFields(IMAGE_URL, "application/pdf", Set.of(OcrFieldType.CPU)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT));
        verifyNoInteractions(naverClovaOcrClient, parser);
    }

    @Test
    void throwsUnsupportedImageFormatWhenMimeTypeIsNull() {
        assertThatThrownBy(() -> client.extractFields(IMAGE_URL, null, Set.of(OcrFieldType.CPU)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT));
        verifyNoInteractions(naverClovaOcrClient, parser);
    }

    @Test
    void modelVersionIsAFixedConstant() {
        assertThat(client.getModelVersion()).isEqualTo("naver-clova-general-v2");
    }
}
