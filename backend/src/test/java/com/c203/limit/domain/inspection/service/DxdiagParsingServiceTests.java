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
import com.c203.limit.domain.inspection.parser.DxdiagParser;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
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
    private static final Long LISTING_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 200L;
    private static final String CDN_URL = "https://cdn.example.com/evidence/9004.xml";

    @Mock EvidenceRepository evidenceRepository;
    @Mock DxdiagResultRepository dxdiagResultRepository;
    @Mock ListingOwnerReader listingOwnerReader;
    @Mock DxdiagFileFetcher dxdiagFileFetcher;
    @Mock DxdiagParser dxdiagParser;
    @Mock DeviceInfoCompletionService deviceInfoCompletionService;

    DxdiagParsingService service;

    @BeforeEach
    void setUp() {
        service =
                new DxdiagParsingService(
                        evidenceRepository,
                        dxdiagResultRepository,
                        listingOwnerReader,
                        dxdiagFileFetcher,
                        dxdiagParser,
                        deviceInfoCompletionService);
    }

    private Evidence readyEvidence(EvidenceType evidenceType) {
        Evidence evidence =
                Evidence.upload(LISTING_ID, null, evidenceType, "s3/key.xml", "text/xml", LocalDateTime.now());
        evidence.markReady(CDN_URL);
        return evidence;
    }

    private void stubOwnedListing() {
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerReader.ListingOwnerInfo(LISTING_ID, SELLER_ID)));
    }

    @Test
    void parseSavesSuccessResultWhenAllFieldsExtracted() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        DxdiagParseResult parsed =
                new DxdiagParseResult(
                        "950XDB", "Windows 11 Pro", "475.8 GB",
                        "i7-1165G7", "16384 MB RAM", "Iris Xe", "8156 MB", "27.20.100.9415", "Realtek Audio");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(dxdiagFileFetcher.fetch(CDN_URL)).thenReturn("<DxDiag/>".getBytes());
        when(dxdiagParser.parse(any(), any(), any())).thenReturn(parsed);
        when(dxdiagResultRepository.save(any(DxdiagResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DxdiagResult result = service.parse(EVIDENCE_ID, SELLER_ID);

        assertThat(result.getEvidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(result.getParseStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(result.getCpu()).isEqualTo("i7-1165G7");
        assertThat(result.getGpu()).isEqualTo("Iris Xe");
        assertThat(result.getModelName()).isEqualTo("950XDB");
    }

    @Test
    void parseSavesPartialResultWhenSomeFieldsMissing() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        DxdiagParseResult parsed =
                new DxdiagParseResult(
                        "950XDB", "Windows 11 Pro", "475.8 GB",
                        "i7-1165G7", "16384 MB RAM", null, null, null, "Realtek Audio");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(dxdiagFileFetcher.fetch(CDN_URL)).thenReturn("<DxDiag/>".getBytes());
        when(dxdiagParser.parse(any(), any(), any())).thenReturn(parsed);
        when(dxdiagResultRepository.save(any(DxdiagResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DxdiagResult result = service.parse(EVIDENCE_ID, SELLER_ID);

        assertThat(result.getParseStatus()).isEqualTo(ParseStatus.PARTIAL);
        assertThat(result.getGpu()).isNull();
    }

    @Test
    void parseSavesFailedResultWhenNoFieldsAreFound() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        DxdiagParseResult parsed = new DxdiagParseResult(null, null, null, null, null, null, null, null, null);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(dxdiagFileFetcher.fetch(CDN_URL)).thenReturn("garbage".getBytes());
        when(dxdiagParser.parse(any(), any(), any())).thenReturn(parsed);
        when(dxdiagResultRepository.save(any(DxdiagResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DxdiagResult result = service.parse(EVIDENCE_ID, SELLER_ID);

        assertThat(result.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(result.getEvidenceId()).isEqualTo(EVIDENCE_ID);
    }

    @Test
    void parseThrowsParsingFailedWhenFileCannotBeParsed() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(dxdiagFileFetcher.fetch(CDN_URL)).thenReturn("not-xml".getBytes());
        when(dxdiagParser.parse(any(), any(), any())).thenThrow(new DxdiagParseException("broken"));

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PARSING_FAILED));
    }

    @Test
    void parseThrowsWhenEvidenceNotFound() {
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EVIDENCE_NOT_FOUND));
        verifyNoInteractions(dxdiagFileFetcher, dxdiagParser, dxdiagResultRepository);
    }

    @Test
    void parseThrowsForbiddenWhenSellerDoesNotOwnListing() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(dxdiagFileFetcher, dxdiagParser, dxdiagResultRepository);
    }

    @Test
    void parseThrowsWhenEvidenceNotReady() {
        Evidence evidence =
                Evidence.upload(
                        LISTING_ID, null, EvidenceType.DIAGNOSTIC_FILE, "s3/key.xml", "text/xml", LocalDateTime.now());
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EVIDENCE_NOT_READY));
        verifyNoInteractions(dxdiagFileFetcher, dxdiagParser, dxdiagResultRepository);
    }

    @Test
    void parseThrowsAlreadyParsedWhenResultAlreadyExists() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(dxdiagResultRepository.existsByEvidenceId(EVIDENCE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_PARSED));
        verifyNoInteractions(dxdiagFileFetcher, dxdiagParser);
    }

    @Test
    void parseThrowsWhenEvidenceTypeIsNotDiagnosticFile() {
        Evidence evidence = readyEvidence(EvidenceType.PHOTO);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_FILE_FORMAT));
        verifyNoInteractions(dxdiagFileFetcher, dxdiagParser, dxdiagResultRepository);
    }
}
