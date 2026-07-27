package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.BatteryReportHtmlParser;
import com.c203.limit.domain.inspection.parser.BatteryReportParseException;
import com.c203.limit.domain.inspection.parser.BatteryReportParseResult;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
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
class BatteryReportParsingServiceTests {

    private static final Long EVIDENCE_ID = 9005L;
    private static final Long LISTING_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 200L;
    private static final String CDN_URL = "https://cdn.example.com/evidence/9005.html";

    @Mock EvidenceRepository evidenceRepository;
    @Mock BatteryReportResultRepository batteryReportResultRepository;
    @Mock ListingOwnerReader listingOwnerReader;
    @Mock BatteryReportFileFetcher batteryReportFileFetcher;
    @Mock BatteryReportHtmlParser batteryReportHtmlParser;

    BatteryReportParsingService service;

    @BeforeEach
    void setUp() {
        service =
                new BatteryReportParsingService(
                        evidenceRepository,
                        batteryReportResultRepository,
                        listingOwnerReader,
                        batteryReportFileFetcher,
                        batteryReportHtmlParser);
    }

    private Evidence readyEvidence(EvidenceType evidenceType, String mimeType) {
        Evidence evidence =
                Evidence.upload(LISTING_ID, null, evidenceType, "s3/key.html", mimeType, LocalDateTime.now());
        evidence.markReady(CDN_URL);
        return evidence;
    }

    private void stubOwnedListing() {
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerReader.ListingOwnerInfo(LISTING_ID, SELLER_ID)));
    }

    @Test
    void parseSavesSuccessResultWhenAllFieldsExtracted() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE, "text/html");
        BatteryReportParseResult parsed =
                new BatteryReportParseResult(
                        "SAMSUNG Electronics", "67,010 mWh", "55,584 mWh", 418, new BigDecimal("82.95"));
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(batteryReportFileFetcher.fetch(CDN_URL)).thenReturn("<html/>".getBytes());
        when(batteryReportHtmlParser.parse(any())).thenReturn(parsed);
        when(batteryReportResultRepository.save(any(BatteryReportResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BatteryReportResult result = service.parse(EVIDENCE_ID, SELLER_ID);

        assertThat(result.getEvidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(result.getParseStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(result.getBatteryManufacturer()).isEqualTo("SAMSUNG Electronics");
        assertThat(result.getCycleCount()).isEqualTo(418);
        assertThat(result.getCapacityRatio()).isEqualByComparingTo("82.95");
    }

    @Test
    void parseSavesPartialResultWhenSomeFieldsMissing() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE, "text/html");
        BatteryReportParseResult parsed =
                new BatteryReportParseResult("SAMSUNG Electronics", "67,010 mWh", "55,584 mWh", null, null);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(batteryReportFileFetcher.fetch(CDN_URL)).thenReturn("<html/>".getBytes());
        when(batteryReportHtmlParser.parse(any())).thenReturn(parsed);
        when(batteryReportResultRepository.save(any(BatteryReportResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BatteryReportResult result = service.parse(EVIDENCE_ID, SELLER_ID);

        assertThat(result.getParseStatus()).isEqualTo(ParseStatus.PARTIAL);
        assertThat(result.getCycleCount()).isNull();
    }

    @Test
    void parseSavesFailedResultWhenNoFieldsAreFound() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE, "text/html");
        BatteryReportParseResult parsed = new BatteryReportParseResult(null, null, null, null, null);
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(batteryReportFileFetcher.fetch(CDN_URL)).thenReturn("<html/>".getBytes());
        when(batteryReportHtmlParser.parse(any())).thenReturn(parsed);
        when(batteryReportResultRepository.save(any(BatteryReportResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BatteryReportResult result = service.parse(EVIDENCE_ID, SELLER_ID);

        assertThat(result.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(result.getEvidenceId()).isEqualTo(EVIDENCE_ID);
    }

    @Test
    void parseThrowsParsingFailedWhenHtmlCannotBeParsed() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE, "text/html");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();
        when(batteryReportFileFetcher.fetch(CDN_URL)).thenReturn("not-html".getBytes());
        when(batteryReportHtmlParser.parse(any()))
                .thenThrow(new BatteryReportParseException("broken", new RuntimeException()));

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
        verifyNoInteractions(batteryReportFileFetcher, batteryReportHtmlParser, batteryReportResultRepository);
    }

    @Test
    void parseThrowsForbiddenWhenSellerDoesNotOwnListing() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE, "text/html");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(batteryReportFileFetcher, batteryReportHtmlParser, batteryReportResultRepository);
    }

    @Test
    void parseThrowsWhenEvidenceNotReady() {
        Evidence evidence =
                Evidence.upload(
                        LISTING_ID,
                        null,
                        EvidenceType.DIAGNOSTIC_FILE,
                        "s3/key.html",
                        "text/html",
                        LocalDateTime.now());
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EVIDENCE_NOT_READY));
        verifyNoInteractions(batteryReportFileFetcher, batteryReportHtmlParser, batteryReportResultRepository);
    }

    @Test
    void parseThrowsWhenEvidenceTypeIsNotDiagnosticFile() {
        Evidence evidence = readyEvidence(EvidenceType.PHOTO, "image/png");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_FILE_FORMAT));
        verifyNoInteractions(batteryReportFileFetcher, batteryReportHtmlParser, batteryReportResultRepository);
    }

    @Test
    void parseThrowsWhenMimeTypeIsNotHtml() {
        Evidence evidence = readyEvidence(EvidenceType.DIAGNOSTIC_FILE, "application/octet-stream");
        when(evidenceRepository.findById(EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        stubOwnedListing();

        assertThatThrownBy(() -> service.parse(EVIDENCE_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_FILE_FORMAT));
        verifyNoInteractions(batteryReportFileFetcher, batteryReportHtmlParser, batteryReportResultRepository);
    }
}
