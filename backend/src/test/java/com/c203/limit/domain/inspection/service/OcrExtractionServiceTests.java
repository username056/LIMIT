package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.client.NaverClovaOcrClient;
import com.c203.limit.domain.inspection.client.NaverClovaOcrResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcrExtractionServiceTests {

    private static final Long EVIDENCE_ID = 9003L;

    @Mock EvidenceRepository evidenceRepository;
    @Mock OcrResultRepository ocrResultRepository;
    @Mock NaverClovaOcrClient naverClovaOcrClient;

    OcrExtractionService service;

    @BeforeEach
    void setUp() {
        service = new OcrExtractionService(evidenceRepository, ocrResultRepository, naverClovaOcrClient);
    }

    private Evidence readyEvidence(String mimeType) {
        Evidence evidence =
                Evidence.upload(1L, null, EvidenceType.PHOTO, "s3/key.jpg", mimeType, LocalDateTime.now());
        evidence.markReady("https://cdn.example.com/evidence/9003.jpg");
        return evidence;
    }

    @Test
    void extractTextSavesOcrResultWhenEvidenceIsReady() {
        Evidence evidence = readyEvidence("image/jpeg");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        when(naverClovaOcrClient.recognize(evidence.getCdnUrl(), "jpg"))
                .thenReturn(new NaverClovaOcrResult("Galaxy Book4 Pro", new BigDecimal("0.965"), "V2"));
        when(ocrResultRepository.save(any(OcrResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OcrResult result = service.extractText(EVIDENCE_ID, OcrFieldType.MODEL_NAME);

        assertThat(result.getEvidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(result.getFieldType()).isEqualTo(OcrFieldType.MODEL_NAME);
        assertThat(result.getRawText()).isEqualTo("Galaxy Book4 Pro");
        assertThat(result.getParsedValue()).isEqualTo("Galaxy Book4 Pro");
        assertThat(result.getConfidence()).isEqualByComparingTo("0.965");
        assertThat(result.getOcrModelVersion()).isEqualTo("V2");
    }

    @Test
    void extractTextThrowsWhenEvidenceNotFound() {
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extractText(EVIDENCE_ID, OcrFieldType.MODEL_NAME))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EVIDENCE_NOT_FOUND));
        verifyNoInteractions(naverClovaOcrClient, ocrResultRepository);
    }

    @Test
    void extractTextThrowsWhenEvidenceNotReady() {
        Evidence evidence =
                Evidence.upload(
                        1L, null, EvidenceType.PHOTO, "s3/key.jpg", "image/jpeg", LocalDateTime.now());
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.extractText(EVIDENCE_ID, OcrFieldType.MODEL_NAME))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EVIDENCE_NOT_READY));
        verifyNoInteractions(naverClovaOcrClient, ocrResultRepository);
    }

    @Test
    void extractTextThrowsWhenMimeTypeUnsupported() {
        Evidence evidence = readyEvidence("application/pdf");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.extractText(EVIDENCE_ID, OcrFieldType.MODEL_NAME))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT));
        verifyNoInteractions(naverClovaOcrClient, ocrResultRepository);
    }
}
