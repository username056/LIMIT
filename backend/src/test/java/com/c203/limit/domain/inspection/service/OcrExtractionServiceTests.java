package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.client.OcrClient;
import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.response.OcrResultResponse;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.OcrExtractionStatus;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader.ListingOwnerInfo;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcrExtractionServiceTests {

    private static final Long EVIDENCE_ID = 9003L;
    private static final Long LISTING_ID = 100L;
    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Mock EvidenceRepository evidenceRepository;
    @Mock OcrResultRepository ocrResultRepository;
    @Mock ListingOwnerReader listingOwnerReader;
    @Mock OcrClient ocrClient;

    OcrExtractionService service;

    @BeforeEach
    void setUp() {
        service = new OcrExtractionService(evidenceRepository, ocrResultRepository, listingOwnerReader, ocrClient);
    }

    private Evidence readyEvidence() {
        Evidence evidence =
                Evidence.upload(
                        LISTING_ID, null, EvidenceType.PHOTO, "s3/key.jpg", "image/jpeg", LocalDateTime.now());
        evidence.markReady("https://cdn.example.com/evidence/9003.jpg");
        return evidence;
    }

    private void stubEvidenceAndOwner(Evidence evidence) {
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerInfo(LISTING_ID, SELLER_ID)));
    }

    private void stubSaveEcho() {
        when(ocrResultRepository.save(any(OcrResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private OcrFieldExtraction extraction(OcrFieldType fieldType, String text) {
        return new OcrFieldExtraction(fieldType, text, text, new BigDecimal("0.900"));
    }

    @Test
    void returnsSuccessWhenAllExpectedFieldsAreDetected() {
        stubEvidenceAndOwner(readyEvidence());
        stubSaveEcho();
        when(ocrClient.getModelVersion()).thenReturn("mock-v1");
        when(ocrClient.extractFields(any(), any(), anySet()))
                .thenReturn(
                        List.of(
                                extraction(OcrFieldType.MODEL_NAME, "Galaxy Book4 Pro"),
                                extraction(OcrFieldType.CPU, "13th Gen Intel(R) Core(TM) i7-13700H"),
                                extraction(OcrFieldType.RAM, "32.0GB"),
                                extraction(OcrFieldType.GPU, "6 GB"),
                                extraction(OcrFieldType.STORAGE_CAPACITY, "512 GB"),
                                extraction(OcrFieldType.OS_VERSION, "Windows 11 Enterprise 25H2")));

        OcrResultResponse response = service.extractAndStructure(EVIDENCE_ID, SELLER_ID);

        assertThat(response.getEvidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(response.getStatus()).isEqualTo(OcrExtractionStatus.SUCCESS);
        assertThat(response.getMissingFieldTypes()).isEmpty();
        assertThat(response.getResults()).hasSize(6);
        assertThat(response.getResults())
                .filteredOn(item -> item.getFieldType() == OcrFieldType.STORAGE_CAPACITY)
                .extracting("parsedValue")
                .containsExactly("512GB");
    }

    @Test
    void returnsPartialWhenSomeExpectedFieldsAreMissing() {
        stubEvidenceAndOwner(readyEvidence());
        stubSaveEcho();
        when(ocrClient.getModelVersion()).thenReturn("mock-v1");
        when(ocrClient.extractFields(any(), any(), anySet()))
                .thenReturn(List.of(extraction(OcrFieldType.MODEL_NAME, "Galaxy Book4 Pro")));

        OcrResultResponse response = service.extractAndStructure(EVIDENCE_ID, SELLER_ID);

        assertThat(response.getStatus()).isEqualTo(OcrExtractionStatus.PARTIAL);
        assertThat(response.getMissingFieldTypes())
                .containsExactlyInAnyOrder("CPU", "RAM", "GPU", "STORAGE_CAPACITY", "OS_VERSION");
        assertThat(response.getResults()).hasSize(1);
    }

    @Test
    void returnsFailedWhenNoFieldIsDetected() {
        stubEvidenceAndOwner(readyEvidence());
        when(ocrClient.extractFields(any(), any(), anySet())).thenReturn(List.of());

        OcrResultResponse response = service.extractAndStructure(EVIDENCE_ID, SELLER_ID);

        assertThat(response.getStatus()).isEqualTo(OcrExtractionStatus.FAILED);
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getMissingFieldTypes())
                .containsExactlyInAnyOrder("MODEL_NAME", "CPU", "RAM", "GPU", "STORAGE_CAPACITY", "OS_VERSION");
        verifyNoInteractions(ocrResultRepository);
    }

    @Test
    void throwsForbiddenWhenCallerIsNotTheListingOwner() {
        stubEvidenceAndOwner(readyEvidence());

        assertThatThrownBy(() -> service.extractAndStructure(EVIDENCE_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(ocrClient, ocrResultRepository);
    }

    @Test
    void throwsEvidenceNotFoundWhenEvidenceDoesNotExist() {
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extractAndStructure(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_NOT_FOUND));
        verifyNoInteractions(ocrClient, ocrResultRepository);
    }

    @Test
    void throwsEvidenceNotReadyWhenEvidenceIsNotProcessed() {
        Evidence evidence =
                Evidence.upload(
                        LISTING_ID, null, EvidenceType.PHOTO, "s3/key.jpg", "image/jpeg", LocalDateTime.now());
        stubEvidenceAndOwner(evidence);

        assertThatThrownBy(() -> service.extractAndStructure(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_NOT_READY));
        verifyNoInteractions(ocrClient, ocrResultRepository);
    }

    @Test
    void throwsParsingFailedWhenOcrClientErrors() {
        stubEvidenceAndOwner(readyEvidence());
        when(ocrClient.extractFields(any(), any(), anySet())).thenThrow(new RuntimeException("upstream error"));

        assertThatThrownBy(() -> service.extractAndStructure(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PARSING_FAILED));
        verifyNoInteractions(ocrResultRepository);
    }

    @Test
    void propagatesSpecificBusinessExceptionFromOcrClientInsteadOfWrappingIt() {
        stubEvidenceAndOwner(readyEvidence());
        when(ocrClient.extractFields(any(), any(), anySet()))
                .thenThrow(new BusinessException(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT));

        assertThatThrownBy(() -> service.extractAndStructure(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT));
        verifyNoInteractions(ocrResultRepository);
    }
}
