package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.DxdiagParseException;
import com.c203.limit.domain.inspection.parser.DxdiagParseResult;
import com.c203.limit.domain.inspection.parser.DxdiagXmlParser;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DxdiagParsingServiceTests {

    private static final Long EVIDENCE_ID = 9004L;
    private static final String CDN_URL = "https://cdn.example.com/evidence/9004.xml";

    @Mock EvidenceRepository evidenceRepository;
    @Mock DxdiagResultRepository dxdiagResultRepository;
    @Mock DxdiagFileFetcher dxdiagFileFetcher;
    @Mock DxdiagXmlParser dxdiagXmlParser;

    DxdiagParsingService service;

    @BeforeEach
    void setUp() {
        service =
                new DxdiagParsingService(
                        evidenceRepository, dxdiagResultRepository, dxdiagFileFetcher, dxdiagXmlParser);
    }

    private Evidence readyEvidence(EvidenceType evidenceType) {
        Evidence evidence =
                Evidence.upload(1L, null, evidenceType, "s3/key.xml", "text/xml", LocalDateTime.now());
        evidence.markReady(CDN_URL);
        return evidence;
    }

    @Test
    void parseSavesSuccessResultWhenAllFieldsExtracted() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        DxdiagParseResult parsed =
                new DxdiagParseResult(
                        "SAMSUNG", "950XDB", "Windows 10", "i7-1165G7", "16384MB RAM",
                        "Iris Xe", "8156 MB", "27.20.100.9415", "Realtek Audio");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        when(dxdiagFileFetcher.fetch(CDN_URL)).thenReturn("<DxDiag/>".getBytes());
        when(dxdiagXmlParser.parse(any())).thenReturn(parsed);
        when(dxdiagResultRepository.save(any(DxdiagResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DxdiagParsingService.DxdiagParsingResult result = service.parse(EVIDENCE_ID);

        assertThat(result.entity().getEvidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(result.entity().getParseStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(result.entity().getCpu()).isEqualTo("i7-1165G7");
        assertThat(result.entity().getGpu()).isEqualTo("Iris Xe");
        assertThat(result.parsed().manufacturer()).isEqualTo("SAMSUNG");
        assertThat(result.parsed().model()).isEqualTo("950XDB");
        assertThat(result.parsed().osVersion()).isEqualTo("Windows 10");
    }

    @Test
    void parseSavesPartialResultWhenSomeFieldsMissing() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        DxdiagParseResult parsed =
                new DxdiagParseResult(
                        "SAMSUNG", "950XDB", "Windows 10", "i7-1165G7", "16384MB RAM",
                        null, null, null, "Realtek Audio");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        when(dxdiagFileFetcher.fetch(CDN_URL)).thenReturn("<DxDiag/>".getBytes());
        when(dxdiagXmlParser.parse(any())).thenReturn(parsed);
        when(dxdiagResultRepository.save(any(DxdiagResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DxdiagParsingService.DxdiagParsingResult result = service.parse(EVIDENCE_ID);

        assertThat(result.entity().getParseStatus()).isEqualTo(ParseStatus.PARTIAL);
        assertThat(result.entity().getGpu()).isNull();
    }

    @Test
    void parseSavesFailedResultWhenXmlCannotBeParsed() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        when(dxdiagFileFetcher.fetch(CDN_URL)).thenReturn("not-xml".getBytes());
        when(dxdiagXmlParser.parse(any())).thenThrow(new DxdiagParseException("broken"));
        when(dxdiagResultRepository.save(any(DxdiagResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DxdiagParsingService.DxdiagParsingResult result = service.parse(EVIDENCE_ID);

        assertThat(result.entity().getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(result.entity().getEvidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(result.parsed()).isNull();
    }

    @Test
    void parseThrowsWhenEvidenceNotFound() {
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EVIDENCE_NOT_FOUND));
        verifyNoInteractions(dxdiagFileFetcher, dxdiagXmlParser, dxdiagResultRepository);
    }

    @Test
    void parseThrowsWhenEvidenceNotReady() {
        Evidence evidence =
                Evidence.upload(
                        1L, null, EvidenceType.DIAGNOSTIC_FILE, "s3/key.xml", "text/xml", LocalDateTime.now());
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EVIDENCE_NOT_READY));
        verifyNoInteractions(dxdiagFileFetcher, dxdiagXmlParser, dxdiagResultRepository);
    }

    @Test
    void parseThrowsWhenEvidenceTypeIsNotDiagnosticFile() {
        Evidence evidence = readyEvidence(EvidenceType.PHOTO);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_EVIDENCE_TYPE));
        verifyNoInteractions(dxdiagFileFetcher, dxdiagXmlParser, dxdiagResultRepository);
    }
}
