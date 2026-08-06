package com.c203.limit.domain.inspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CreateAgentUploadRequest;
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
import com.c203.limit.domain.product.dto.request.CompleteEvidenceRequest;
import com.c203.limit.domain.product.dto.request.CreateEvidenceUploadUrlRequest;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionSessionServiceTests {
    private InspectionSessionService service;
    private InspectionSessionRepository sessionRepository;
    private InspectionSessionTestResultRepository testResultRepository;
    private ListingChecklistItemRepository checklistItemRepository;
    private ListingChecklistItem dxdiagItem;
    private EvidenceUploadService evidenceUploadService;
    private DxdiagParsingService dxdiagParsingService;
    private BatteryReportParsingService batteryReportParsingService;

    @BeforeEach
    void setUp() {
        ListingRepository listingRepository = mock(ListingRepository.class);
        checklistItemRepository = mock(ListingChecklistItemRepository.class);
        Listing listing = mock(Listing.class);
        dxdiagItem = mock(ListingChecklistItem.class);
        evidenceUploadService = mock(EvidenceUploadService.class);
        dxdiagParsingService = mock(DxdiagParsingService.class);
        batteryReportParsingService = mock(BatteryReportParsingService.class);

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
                evidenceUploadService,
                dxdiagParsingService,
                batteryReportParsingService,
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
        assertThat(created.expiresAt())
                .isEqualTo(OffsetDateTime.parse("2026-08-03T01:10:00Z"));
        assertThat(paired.expiresAt())
                .isEqualTo(OffsetDateTime.parse("2026-08-03T02:00:00Z"));
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

    @Test
    void createRejectsListingThatBelongsToAnotherSeller() {
        assertThatThrownBy(() -> service.create(99L, 1001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        verify(sessionRepository, never()).save(any(InspectionSession.class));
    }

    @Test
    void createRequiresDxdiagChecklistTarget() {
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(10L, 1001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_TARGET_NOT_FOUND));
    }

    @Test
    void createFailsWhenEveryGeneratedPairingCodeCollides() {
        when(sessionRepository.existsByPairingCodeHashAndStatusAndExpiresAtAfter(
                        any(byte[].class), eq(InspectionSessionStatus.CREATED), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(10L, 1001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_ERROR));
        verify(sessionRepository, never()).save(any(InspectionSession.class));
    }

    @Test
    void statusRejectsSellerWhoDoesNotOwnTheSession() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.status(99L, created.sessionKey()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void statusExpiresSessionThatPassedItsDeadline() {
        when(sessionRepository.findById("sess-expired"))
                .thenReturn(Optional.of(expiredSession()));

        assertThat(service.status(10L, "sess-expired").status())
                .isEqualTo(InspectionSessionStatus.EXPIRED);
    }

    @Test
    void statusThrowsWhenSessionIsUnknown() {
        assertThatThrownBy(() -> service.status(10L, "sess-unknown"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_SESSION_NOT_FOUND));
    }

    @Test
    void agentRequestOnExpiredSessionIsRejected() {
        when(sessionRepository.findById("sess-expired"))
                .thenReturn(Optional.of(expiredSession()));

        assertThatThrownBy(() -> service.complete("Bearer any-token", "sess-expired"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_SESSION_EXPIRED));
    }

    @Test
    void agentRequestWithoutUsableBearerTokenIsRejected() {
        PairedSession paired = pairedSession();

        for (String authorization : new String[] {null, "token-without-scheme", "Bearer    "}) {
            assertThatThrownBy(() -> service.complete(authorization, paired.sessionKey()))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INSPECTION_AGENT_UNAUTHORIZED));
        }
    }

    @Test
    void createUploadRejectsCompletedSession() {
        PairedSession paired = pairedSession();
        paired.session().complete(LocalDateTime.parse("2026-08-03T01:01:00"));

        assertThatThrownBy(() -> service.createUpload(
                        paired.authorization(),
                        paired.sessionKey(),
                        new CreateAgentUploadRequest(
                                "DXDIAG", "DxDiag.txt", "text/plain", 2048L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_SESSION_INVALID_STATE));
    }

    @Test
    void createUploadIssuesPresignedUrlAndMarksSessionUploading() {
        PairedSession paired = pairedSession();
        when(dxdiagItem.getId()).thenReturn(7002L);
        when(evidenceUploadService.createUploadUrl(
                        eq(10L), eq(1001L), eq(7002L), any(CreateEvidenceUploadUrlRequest.class)))
                .thenReturn(new EvidenceUploadUrlResponse(
                        "upl_01",
                        "products/1001/checklist/7002/attempt-1.txt",
                        "https://storage.example.com/presigned",
                        OffsetDateTime.parse("2026-08-03T01:15:00Z"),
                        Map.of("Content-Type", "text/plain")));

        var response = service.createUpload(
                paired.authorization(),
                paired.sessionKey(),
                new CreateAgentUploadRequest("dxdiag", "DxDiag.txt", "text/plain", 2048L));

        assertThat(response.uploadId()).isEqualTo("upl_01");
        assertThat(response.presignedUrl()).isEqualTo("https://storage.example.com/presigned");
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "text/plain");
        assertThat(service.status(10L, paired.sessionKey()).status())
                .isEqualTo(InspectionSessionStatus.UPLOADING);
    }

    @Test
    void createUploadRejectsUnsupportedParserType() {
        PairedSession paired = pairedSession();

        assertThatThrownBy(() -> service.createUpload(
                        paired.authorization(),
                        paired.sessionKey(),
                        new CreateAgentUploadRequest(
                                "MEMORY_DUMP", "dump.bin", "application/octet-stream", 10L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(evidenceUploadService, never()).createUploadUrl(
                any(), any(), any(), any(CreateEvidenceUploadUrlRequest.class));
    }

    @Test
    void completeUploadHandsDxdiagEvidenceToTheDxdiagParser() {
        PairedSession paired = pairedSession();
        paired.session().markUploading();
        when(dxdiagItem.getId()).thenReturn(7002L);
        when(evidenceUploadService.complete(
                        eq(10L), eq(1001L), eq(7002L), any(CompleteEvidenceRequest.class)))
                .thenReturn(evidence(9003L));

        service.completeUpload(paired.authorization(), paired.sessionKey(), "upl_01", "DXDIAG");

        verify(dxdiagParsingService).parse(9003L, 10L);
        verify(batteryReportParsingService, never()).parse(any(), any());
    }

    @Test
    void completeUploadHandsBatteryReportEvidenceToTheBatteryParser() {
        ListingChecklistItem batteryItem = mock(ListingChecklistItem.class);
        when(batteryItem.getAutomationType()).thenReturn(AutomationType.FILE_PARSE);
        when(batteryItem.getParserType()).thenReturn("BATTERY_REPORT");
        when(batteryItem.getId()).thenReturn(7003L);
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(dxdiagItem, batteryItem));
        PairedSession paired = pairedSession();
        paired.session().markUploading();
        when(evidenceUploadService.complete(
                        eq(10L), eq(1001L), eq(7003L), any(CompleteEvidenceRequest.class)))
                .thenReturn(evidence(9004L));

        service.completeUpload(
                paired.authorization(), paired.sessionKey(), "upl_02", " battery_report ");

        verify(batteryReportParsingService).parse(9004L, 10L);
        verify(dxdiagParsingService, never()).parse(any(), any());
    }

    @Test
    void completeRejectsSessionThatIsNotUploading() {
        PairedSession paired = pairedSession();

        assertThatThrownBy(() -> service.complete(paired.authorization(), paired.sessionKey()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_SESSION_INVALID_STATE));
    }

    @Test
    void completeMarksUploadingSessionAsCompleted() {
        PairedSession paired = pairedSession();
        paired.session().markUploading();

        var status = service.complete(paired.authorization(), paired.sessionKey());

        assertThat(status.status()).isEqualTo(InspectionSessionStatus.COMPLETED);
        assertThat(status.sessionKey()).isEqualTo(paired.sessionKey());
    }

    @Test
    void submitTestResultRejectsSkippedResultWithMeasurement() {
        PairedSession paired = pairedSession();

        assertThatThrownBy(() -> service.submitTestResult(
                        paired.authorization(),
                        paired.sessionKey(),
                        testResult(
                                TestType.CAMERA,
                                MeasurementStatus.DETECTED,
                                InspectionUserResult.SKIPPED)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_TEST_RESULT_INVALID));
    }

    @Test
    void submitTestResultRejectsUserConfirmationWithoutDetection() {
        PairedSession paired = pairedSession();

        assertThatThrownBy(() -> service.submitTestResult(
                        paired.authorization(),
                        paired.sessionKey(),
                        testResult(
                                TestType.CAMERA,
                                MeasurementStatus.NOT_DETECTED,
                                InspectionUserResult.USER_CONFIRMED)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_TEST_RESULT_INVALID));
    }

    @Test
    void submitTestResultRejectsNotExecutedMeasurementWithConfirmedResult() {
        PairedSession paired = pairedSession();

        assertThatThrownBy(() -> service.submitTestResult(
                        paired.authorization(),
                        paired.sessionKey(),
                        testResult(
                                TestType.CAMERA,
                                MeasurementStatus.NOT_EXECUTED,
                                InspectionUserResult.USER_CONFIRMED)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_TEST_RESULT_INVALID));
    }

    @Test
    void submitTestResultMapsUserReportedIssueToFailedDeviceCheck() {
        PairedSession paired = pairedSession();
        ListingChecklistItem cameraItem = mock(ListingChecklistItem.class);
        when(checklistItemRepository.findByListingIdAndItemCode(1001L, "LAP-FTR-CAM"))
                .thenReturn(Optional.of(cameraItem));

        service.submitTestResult(
                paired.authorization(),
                paired.sessionKey(),
                testResult(
                        TestType.CAMERA,
                        MeasurementStatus.NOT_DETECTED,
                        InspectionUserResult.USER_REPORTED_ISSUE));

        verify(cameraItem).applyDeviceCheckResult(DeviceCheckResult.FAILED);
    }

    @Test
    void submitTestResultMapsSkippedToSkippedDeviceCheck() {
        PairedSession paired = pairedSession();
        ListingChecklistItem cameraItem = mock(ListingChecklistItem.class);
        when(cameraItem.getId()).thenReturn(7001L);
        when(checklistItemRepository.findByListingIdAndItemCode(1001L, "LAP-FTR-CAM"))
                .thenReturn(Optional.of(cameraItem));

        var submission = service.submitTestResult(
                paired.authorization(),
                paired.sessionKey(),
                testResult(
                        TestType.CAMERA,
                        MeasurementStatus.NOT_EXECUTED,
                        InspectionUserResult.SKIPPED));

        assertThat(submission.created()).isTrue();
        assertThat(submission.response().checklistItemId()).isEqualTo(7001L);
        assertThat(submission.response().userResult())
                .isEqualTo(InspectionUserResult.SKIPPED);
        verify(cameraItem).applyDeviceCheckResult(DeviceCheckResult.SKIPPED);
    }

    @Test
    void submitTestResultWithoutUserResultFallsBackToMeasurementForCharging() {
        PairedSession paired = pairedSession();
        ListingChecklistItem chargingItem = mock(ListingChecklistItem.class);
        when(checklistItemRepository.findByListingIdAndItemCode(1001L, "LAP-CHG-007"))
                .thenReturn(Optional.of(chargingItem));

        service.submitTestResult(
                paired.authorization(),
                paired.sessionKey(),
                testResult(TestType.CHARGING, MeasurementStatus.NOT_DETECTED, null));

        verify(chargingItem).applyDeviceCheckResult(DeviceCheckResult.FAILED);
    }

    @Test
    void submitTestResultThrowsWhenSessionRowIsMissing() {
        assertThatThrownBy(() -> service.submitTestResult(
                        "Bearer any-token",
                        "sess-unknown",
                        testResult(
                                TestType.CAMERA,
                                MeasurementStatus.DETECTED,
                                InspectionUserResult.USER_CONFIRMED)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_SESSION_NOT_FOUND));
    }

    @Test
    void listTestResultsThrowsWhenSessionIsUnknown() {
        assertThatThrownBy(() -> service.listTestResults(10L, "sess-unknown"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_SESSION_NOT_FOUND));
    }

    private PairedSession pairedSession() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));
        when(sessionRepository.findBySessionKeyForUpdate(created.sessionKey()))
                .thenReturn(Optional.of(stored));
        var paired = service.pair(created.pairingCode(), "0.1.0");
        return new PairedSession(created.sessionKey(), stored, "Bearer " + paired.agentToken());
    }

    private InspectionSession expiredSession() {
        return InspectionSession.create(
                "sess-expired",
                new byte[32],
                10L,
                1001L,
                LocalDateTime.parse("2026-08-03T00:59:00"),
                LocalDateTime.parse("2026-08-03T00:00:00"));
    }

    private EvidenceResponse evidence(Long evidenceId) {
        return new EvidenceResponse(
                evidenceId,
                7002L,
                "DIAGNOSTIC_FILE",
                1,
                true,
                "https://cdn.example.com/evidence.txt",
                "READY",
                "NONE",
                OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                OffsetDateTime.parse("2026-08-03T01:01:00Z"));
    }

    private SubmitTestResultRequest testResult(
            TestType testType,
            MeasurementStatus measurementStatus,
            InspectionUserResult userResult) {
        return new SubmitTestResultRequest(
                UUID.randomUUID(),
                testType,
                measurementStatus,
                userResult,
                null,
                OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                null);
    }

    private record PairedSession(
            String sessionKey, InspectionSession session, String authorization) {}

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
