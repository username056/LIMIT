package com.c203.limit.domain.inspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SubmitTestResultRequest;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.InspectionUserResult;
import com.c203.limit.domain.inspection.enums.MeasurementStatus;
import com.c203.limit.domain.inspection.enums.TestType;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.service.BatteryReportParsingService;
import com.c203.limit.domain.inspection.service.DxdiagParsingService;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.service.EvidenceUploadService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionSessionServiceTests {
    private InspectionSessionService service;
    private InspectionSessionRepository sessionRepository;
    private InspectionSessionTestResultRepository testResultRepository;
    private ListingChecklistItemRepository checklistItemRepository;

    @BeforeEach
    void setUp() {
        ListingRepository listingRepository = mock(ListingRepository.class);
        checklistItemRepository = mock(ListingChecklistItemRepository.class);
        Listing listing = mock(Listing.class);
        ListingChecklistItem dxdiagItem = mock(ListingChecklistItem.class);

        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 10L))
                .thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(dxdiagItem));
        when(dxdiagItem.getAutomationType()).thenReturn(AutomationType.FILE_PARSE);
        when(dxdiagItem.getParserType()).thenReturn("DXDIAG");

        sessionRepository = mock(InspectionSessionRepository.class);
        testResultRepository = mock(InspectionSessionTestResultRepository.class);
        when(sessionRepository.save(any(InspectionSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(testResultRepository.save(any(InspectionSessionTestResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.existsByPairingCodeHashAndStatusAndExpiresAtAfter(
                        any(byte[].class), eq(InspectionSessionStatus.CREATED), any()))
                .thenReturn(false);

        service = new InspectionSessionService(
                sessionRepository,
                testResultRepository,
                listingRepository,
                checklistItemRepository,
                mock(EvidenceUploadService.class),
                mock(DxdiagParsingService.class),
                mock(BatteryReportParsingService.class),
                Clock.fixed(Instant.parse("2026-08-03T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 판매자가_만든_연결_코드를_에이전트_토큰으로_교환한다() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));

        var paired = service.pair(created.pairingCode(), "0.1.0");

        assertThat(paired.sessionKey()).isEqualTo(created.sessionKey());
        assertThat(paired.agentToken()).isNotBlank();
        assertThat(service.status(10L, created.sessionKey()).status())
                .isEqualTo(InspectionSessionStatus.PAIRED);
    }

    @Test
    void 잘못된_연결_코드는_거부한다() {
        service.create(10L, 1001L);
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pair("999999", "0.1.0"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INSPECTION_PAIRING_INVALID));
    }

    @Test
    void completedSessionCannotCompleteAnotherUpload() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));

        var paired = service.pair(created.pairingCode(), "0.1.0");
        stored.complete(java.time.LocalDateTime.parse("2026-08-03T01:01:00"));
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.completeUpload(
                        "Bearer " + paired.agentToken(),
                        created.sessionKey(),
                        "upload-id",
                        "DXDIAG"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_SESSION_INVALID_STATE));
    }

    @Test
    void completedSessionCannotSubmitTestResult() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));

        var paired = service.pair(created.pairingCode(), "0.1.0");
        stored.complete(java.time.LocalDateTime.parse("2026-08-03T01:01:00"));
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));

        var request = new SubmitTestResultRequest(
                UUID.randomUUID(),
                TestType.CAMERA,
                MeasurementStatus.DETECTED,
                null,
                null,
                OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                null);

        assertThatThrownBy(() -> service.submitTestResult(
                        "Bearer " + paired.agentToken(), created.sessionKey(), request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_SESSION_INVALID_STATE));
    }

    @Test
    void submitTestResultPersistsMappedResultForPairedSession() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));

        var paired = service.pair(created.pairingCode(), "0.1.0");
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));
        ListingChecklistItem cameraItem = mock(ListingChecklistItem.class);
        when(cameraItem.getId()).thenReturn(7001L);
        when(checklistItemRepository.findByListingIdAndItemCode(1001L, "LAP-FTR-CAM"))
                .thenReturn(Optional.of(cameraItem));

        UUID clientResultId = UUID.randomUUID();
        OffsetDateTime testedAt = OffsetDateTime.parse("2026-08-03T01:00:00Z");
        var request = new SubmitTestResultRequest(
                clientResultId,
                TestType.CAMERA,
                MeasurementStatus.DETECTED,
                InspectionUserResult.USER_CONFIRMED,
                java.util.Map.of("width", 1280, "height", 720),
                testedAt,
                null);

        var submission = service.submitTestResult(
                "Bearer " + paired.agentToken(), created.sessionKey(), request);
        var response = submission.response();

        assertThat(submission.created()).isTrue();
        assertThat(response.clientResultId()).isEqualTo(clientResultId);
        assertThat(response.listingId()).isEqualTo(1001L);
        assertThat(response.checklistItemId()).isEqualTo(7001L);
        assertThat(response.testType()).isEqualTo(TestType.CAMERA);
        assertThat(response.measurementStatus()).isEqualTo(MeasurementStatus.DETECTED);
        assertThat(response.attemptNo()).isEqualTo(1);
        assertThat(response.rawDataSaved()).isFalse();
        assertThat(response.testedAt()).isEqualTo(testedAt);
        verify(cameraItem).applyDeviceCheckResult(DeviceCheckResult.SUCCESS);
    }

    @Test
    void sameClientResultIdAndPayloadReturnsExistingResult() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        var paired = service.pair(created.pairingCode(), "0.1.0");
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));

        var request = confirmedCameraRequest(UUID.randomUUID());
        InspectionSessionTestResult existing = InspectionSessionTestResult.create(
                created.sessionKey(), null, request, 2, LocalDateTime.parse("2026-08-03T01:00:00"));
        when(testResultRepository.findBySessionKeyAndClientResultId(
                        created.sessionKey(), request.clientResultId()))
                .thenReturn(Optional.of(existing));

        var submission = service.submitTestResult(
                "Bearer " + paired.agentToken(), created.sessionKey(), request);

        assertThat(submission.created()).isFalse();
        assertThat(submission.response().attemptNo()).isEqualTo(2);
        verify(testResultRepository, never()).save(any());
    }

    @Test
    void sameClientResultIdWithDifferentPayloadIsRejected() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        var paired = service.pair(created.pairingCode(), "0.1.0");
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));

        UUID clientResultId = UUID.randomUUID();
        var original = confirmedCameraRequest(clientResultId);
        InspectionSessionTestResult existing = InspectionSessionTestResult.create(
                created.sessionKey(), null, original, 1, LocalDateTime.parse("2026-08-03T01:00:00"));
        when(testResultRepository.findBySessionKeyAndClientResultId(
                        created.sessionKey(), clientResultId))
                .thenReturn(Optional.of(existing));
        var changed = new SubmitTestResultRequest(
                clientResultId,
                TestType.CAMERA,
                MeasurementStatus.DETECTED,
                InspectionUserResult.USER_CONFIRMED,
                java.util.Map.of("width", 640),
                original.testedAt(),
                null);

        assertThatThrownBy(() -> service.submitTestResult(
                        "Bearer " + paired.agentToken(), created.sessionKey(), changed))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.INSPECTION_TEST_RESULT_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void retestWithNewClientResultIdIncrementsAttemptNo() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        var paired = service.pair(created.pairingCode(), "0.1.0");
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));

        var previousRequest = confirmedCameraRequest(UUID.randomUUID());
        InspectionSessionTestResult previous = InspectionSessionTestResult.create(
                created.sessionKey(), null, previousRequest, 2, LocalDateTime.parse("2026-08-03T00:59:00"));
        when(testResultRepository.findTopBySessionKeyAndTestTypeOrderByAttemptNoDesc(
                        created.sessionKey(), TestType.CAMERA))
                .thenReturn(Optional.of(previous));

        var submission = service.submitTestResult(
                "Bearer " + paired.agentToken(),
                created.sessionKey(),
                confirmedCameraRequest(UUID.randomUUID()));

        assertThat(submission.response().attemptNo()).isEqualTo(3);
    }

    @Test
    void notExecutedRequiresSkippedAndNullUserResultIsAllowedOnlyForCharging() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        var paired = service.pair(created.pairingCode(), "0.1.0");
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));

        var invalidCamera = new SubmitTestResultRequest(
                UUID.randomUUID(),
                TestType.CAMERA,
                MeasurementStatus.DETECTED,
                null,
                null,
                OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                null);
        assertThatThrownBy(() -> service.submitTestResult(
                        "Bearer " + paired.agentToken(), created.sessionKey(), invalidCamera))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_TEST_RESULT_INVALID));

        var skipped = new SubmitTestResultRequest(
                UUID.randomUUID(),
                TestType.CAMERA,
                MeasurementStatus.NOT_EXECUTED,
                InspectionUserResult.SKIPPED,
                null,
                OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                null);
        assertThat(service.submitTestResult(
                                "Bearer " + paired.agentToken(), created.sessionKey(), skipped)
                        .response()
                        .measurementStatus())
                .isEqualTo(MeasurementStatus.NOT_EXECUTED);

        var charging = new SubmitTestResultRequest(
                UUID.randomUUID(),
                TestType.CHARGING,
                MeasurementStatus.DETECTED,
                null,
                java.util.Map.of("acConnected", true),
                OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                null);
        assertThat(service.submitTestResult(
                                "Bearer " + paired.agentToken(), created.sessionKey(), charging)
                        .created())
                .isTrue();
    }

    @Test
    void tokenFromAnotherSessionCannotSubmitResult() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        service.pair(created.pairingCode(), "0.1.0");
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.submitTestResult(
                        "Bearer token-from-another-session",
                        created.sessionKey(),
                        confirmedCameraRequest(UUID.randomUUID())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_AGENT_UNAUTHORIZED));
        verify(testResultRepository, never()).save(any());
    }

    @Test
    void sellerWithoutOwnershipCannotListTestResults() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.listTestResults(99L, created.sessionKey()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void listTestResultsReturnsEmptyForOwner() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));

        assertThat(service.listTestResults(10L, created.sessionKey())).isEmpty();
    }

    private InspectionSession captureCreatedSession(String sessionKey) {
        var captor = org.mockito.ArgumentCaptor.forClass(InspectionSession.class);
        org.mockito.Mockito.verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getSessionKey()).isEqualTo(sessionKey);
        return captor.getValue();
    }

    private SubmitTestResultRequest confirmedCameraRequest(UUID clientResultId) {
        return new SubmitTestResultRequest(
                clientResultId,
                TestType.CAMERA,
                MeasurementStatus.DETECTED,
                InspectionUserResult.USER_CONFIRMED,
                java.util.Map.of("width", 1280),
                OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                null);
    }
}
