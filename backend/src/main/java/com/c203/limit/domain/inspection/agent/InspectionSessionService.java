package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.AgentUploadResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CreateAgentUploadRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.PairResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SessionResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SessionStatusResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SubmitTestResultRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.TestResultResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.TestResultSubmission;
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
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.service.EvidenceUploadService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionSessionService {
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<TestType, String> CHECKLIST_ITEM_CODES = Map.of(
            TestType.CAMERA, "LAP-FTR-CAM",
            TestType.MICROPHONE, "LAP-FTR-MIC",
            TestType.KEYBOARD, "LAP-KBD-005",
            TestType.TOUCHPAD, "LAP-PAD-006",
            TestType.SPEAKER, "LAP-FTR-SPK",
            TestType.DISPLAY, "LAP-DSP-003",
            TestType.CHARGING, "LAP-CHG-007");

    private final InspectionSessionRepository sessionRepository;
    private final InspectionSessionTestResultRepository testResultRepository;
    private final ListingRepository listingRepository;
    private final ListingChecklistItemRepository checklistItemRepository;
    private final EvidenceUploadService evidenceUploadService;
    private final DxdiagParsingService dxdiagParsingService;
    private final BatteryReportParsingService batteryReportParsingService;
    private final Clock clock;

    public InspectionSessionService(
            InspectionSessionRepository sessionRepository,
            InspectionSessionTestResultRepository testResultRepository,
            ListingRepository listingRepository,
            ListingChecklistItemRepository checklistItemRepository,
            EvidenceUploadService evidenceUploadService,
            DxdiagParsingService dxdiagParsingService,
            BatteryReportParsingService batteryReportParsingService,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.testResultRepository = testResultRepository;
        this.listingRepository = listingRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.evidenceUploadService = evidenceUploadService;
        this.dxdiagParsingService = dxdiagParsingService;
        this.batteryReportParsingService = batteryReportParsingService;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse create(Long sellerId, Long listingId) {
        listingRepository
                .findByIdAndSellerIdAndDeletedAtIsNull(listingId, sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        requireTarget(listingId, "DXDIAG");

        LocalDateTime now = now();
        String code = uniquePairingCode(now);
        InspectionSession session = InspectionSession.create(
                UUID.randomUUID().toString(),
                hash(code),
                sellerId,
                listingId,
                now.plus(SESSION_TTL),
                now);
        sessionRepository.save(session);
        return response(session, code);
    }

    @Transactional
    public SessionStatusResponse status(Long sellerId, String sessionKey) {
        InspectionSession session = requireSession(sessionKey);
        if (!Objects.equals(session.getSellerId(), sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        expireIfNeeded(session);
        return statusResponse(session);
    }

    @Transactional
    public PairResponse pair(String pairingCode, String collectorVersion) {
        LocalDateTime now = now();
        InspectionSession session = sessionRepository
                .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        hash(pairingCode), InspectionSessionStatus.CREATED, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSPECTION_PAIRING_INVALID));

        String token = UUID.randomUUID() + "." + HexFormat.of().formatHex(randomBytes(24));
        session.pair(hash(token), collectorVersion, now);
        return new PairResponse(
                session.getSessionKey(), token, offset(session.getExpiresAt()));
    }

    @Transactional
    public AgentUploadResponse createUpload(
            String authorization, String sessionKey, CreateAgentUploadRequest request) {
        InspectionSession session = authorize(authorization, sessionKey);
        if (session.getStatus() != InspectionSessionStatus.PAIRED
                && session.getStatus() != InspectionSessionStatus.UPLOADING) {
            throw new BusinessException(ErrorCode.INSPECTION_SESSION_INVALID_STATE);
        }
        ListingChecklistItem target = requireTarget(session.getListingId(), request.parserType());
        var response = evidenceUploadService.createUploadUrl(
                session.getSellerId(),
                session.getListingId(),
                target.getId(),
                new CreateEvidenceUploadUrlRequest(
                        request.filename(), request.contentType(), request.fileSize(), null));
        session.markUploading();
        return AgentUploadResponse.from(response);
    }

    @Transactional
    public void completeUpload(
            String authorization,
            String sessionKey,
            String uploadId,
            String parserType) {
        InspectionSession session = authorize(authorization, sessionKey);
        if (session.getStatus() != InspectionSessionStatus.UPLOADING) {
            throw new BusinessException(ErrorCode.INSPECTION_SESSION_INVALID_STATE);
        }
        ListingChecklistItem target = requireTarget(session.getListingId(), parserType);
        EvidenceResponse evidence = evidenceUploadService.complete(
                session.getSellerId(),
                session.getListingId(),
                target.getId(),
                new CompleteEvidenceRequest(uploadId, null));

        if ("DXDIAG".equals(normalizeParserType(parserType))) {
            dxdiagParsingService.parse(evidence.getEvidenceId(), session.getSellerId());
        } else {
            batteryReportParsingService.parse(evidence.getEvidenceId(), session.getSellerId());
        }
    }

    @Transactional
    public SessionStatusResponse complete(String authorization, String sessionKey) {
        InspectionSession session = authorize(authorization, sessionKey);
        if (session.getStatus() != InspectionSessionStatus.UPLOADING) {
            throw new BusinessException(ErrorCode.INSPECTION_SESSION_INVALID_STATE);
        }
        session.complete(now());
        return statusResponse(session);
    }

    @Transactional
    public TestResultSubmission submitTestResult(
            String authorization, String sessionKey, SubmitTestResultRequest request) {
        InspectionSession session = authorizeForUpdate(authorization, sessionKey);
        if (session.getStatus() != InspectionSessionStatus.PAIRED
                && session.getStatus() != InspectionSessionStatus.UPLOADING) {
            throw new BusinessException(ErrorCode.INSPECTION_SESSION_INVALID_STATE);
        }
        validateTestResult(request);

        Optional<InspectionSessionTestResult> existing = testResultRepository
                .findBySessionKeyAndClientResultId(sessionKey, request.clientResultId());
        if (existing.isPresent()) {
            if (!existing.get().hasSamePayload(request)) {
                throw new BusinessException(
                        ErrorCode.INSPECTION_TEST_RESULT_IDEMPOTENCY_CONFLICT);
            }
            return new TestResultSubmission(
                    testResultResponse(existing.get(), session.getListingId()), false);
        }

        Optional<ListingChecklistItem> checklistItem = checklistItemRepository
                .findByListingIdAndItemCode(
                        session.getListingId(), CHECKLIST_ITEM_CODES.get(request.testType()));
        int attemptNo = testResultRepository
                .findTopBySessionKeyAndTestTypeOrderByAttemptNoDesc(
                        sessionKey, request.testType())
                .map(result -> result.getAttemptNo() + 1)
                .orElse(1);
        InspectionSessionTestResult result = InspectionSessionTestResult.create(
                sessionKey,
                checklistItem.map(ListingChecklistItem::getId).orElse(null),
                request,
                attemptNo,
                now());
        testResultRepository.save(result);
        checklistItem.ifPresent(item -> item.applyDeviceCheckResult(deviceCheckResult(request)));

        return new TestResultSubmission(testResultResponse(result, session.getListingId()), true);
    }

    @Transactional(readOnly = true)
    public List<TestResultResponse> listTestResults(Long sellerId, String sessionKey) {
        InspectionSession session = requireSession(sessionKey);
        if (!Objects.equals(session.getSellerId(), sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return testResultRepository.findAllBySessionKeyOrderByCreatedAtAscIdAsc(sessionKey).stream()
                .map(result -> testResultResponse(result, session.getListingId()))
                .toList();
    }

    private void validateTestResult(SubmitTestResultRequest request) {
        MeasurementStatus measurementStatus = request.measurementStatus();
        InspectionUserResult userResult = request.userResult();

        if (measurementStatus == MeasurementStatus.NOT_EXECUTED) {
            if (userResult != InspectionUserResult.SKIPPED) {
                throw invalidTestResult();
            }
            return;
        }
        if (userResult == InspectionUserResult.SKIPPED) {
            throw invalidTestResult();
        }
        if (userResult == null) {
            if (request.testType() != TestType.CHARGING) {
                throw invalidTestResult();
            }
            return;
        }
        if (userResult == InspectionUserResult.USER_CONFIRMED
                && measurementStatus != MeasurementStatus.DETECTED) {
            throw invalidTestResult();
        }
    }

    private DeviceCheckResult deviceCheckResult(SubmitTestResultRequest request) {
        if (request.userResult() == InspectionUserResult.SKIPPED) {
            return DeviceCheckResult.SKIPPED;
        }
        if (request.userResult() == InspectionUserResult.USER_REPORTED_ISSUE) {
            return DeviceCheckResult.FAILED;
        }
        if (request.userResult() == InspectionUserResult.USER_CONFIRMED) {
            return DeviceCheckResult.SUCCESS;
        }
        return request.measurementStatus() == MeasurementStatus.DETECTED
                ? DeviceCheckResult.SUCCESS
                : DeviceCheckResult.FAILED;
    }

    private BusinessException invalidTestResult() {
        return new BusinessException(ErrorCode.INSPECTION_TEST_RESULT_INVALID);
    }

    private TestResultResponse testResultResponse(
            InspectionSessionTestResult result, Long listingId) {
        return new TestResultResponse(
                result.getClientResultId(),
                listingId,
                result.getChecklistItemId(),
                result.getTestType(),
                result.getMeasurementStatus(),
                result.getUserResult(),
                result.getMeasuredValues(),
                result.getAttemptNo(),
                result.isRawDataSaved(),
                offset(result.getTestedAt()),
                offset(result.getCreatedAt()),
                result.getErrorCode());
    }

    private InspectionSession authorize(String authorization, String sessionKey) {
        return authorize(authorization, requireSession(sessionKey));
    }

    private InspectionSession authorizeForUpdate(String authorization, String sessionKey) {
        InspectionSession session = sessionRepository
                .findBySessionKeyForUpdate(sessionKey)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INSPECTION_SESSION_NOT_FOUND));
        return authorize(authorization, session);
    }

    private InspectionSession authorize(
            String authorization, InspectionSession session) {
        expireIfNeeded(session);
        if (session.getStatus() == InspectionSessionStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.INSPECTION_SESSION_EXPIRED);
        }
        String token = bearerToken(authorization);
        if (session.getAgentTokenHash() == null
                || !MessageDigest.isEqual(session.getAgentTokenHash(), hash(token))) {
            throw new BusinessException(ErrorCode.INSPECTION_AGENT_UNAUTHORIZED);
        }
        return session;
    }

    private ListingChecklistItem requireTarget(Long listingId, String parserType) {
        String normalized = normalizeParserType(parserType);
        return checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(listingId).stream()
                .filter(item -> item.getAutomationType() == AutomationType.FILE_PARSE)
                .filter(item -> normalized.equals(item.getParserType()))
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INSPECTION_TARGET_NOT_FOUND));
    }

    private String uniquePairingCode(LocalDateTime now) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
            if (!sessionRepository.existsByPairingCodeHashAndStatusAndExpiresAtAfter(
                    hash(code), InspectionSessionStatus.CREATED, now)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    private String normalizeParserType(String parserType) {
        String normalized =
                parserType == null ? "" : parserType.trim().toUpperCase(Locale.ROOT);
        if (!"DXDIAG".equals(normalized) && !"BATTERY_REPORT".equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private InspectionSession requireSession(String sessionKey) {
        return sessionRepository
                .findById(sessionKey)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INSPECTION_SESSION_NOT_FOUND));
    }

    private void expireIfNeeded(InspectionSession session) {
        if (session.getStatus() != InspectionSessionStatus.COMPLETED
                && !session.getExpiresAt().isAfter(now())) {
            session.expire();
        }
    }

    private SessionResponse response(InspectionSession session, String code) {
        return new SessionResponse(
                session.getSessionKey(),
                code,
                session.getStatus(),
                offset(session.getExpiresAt()));
    }

    private SessionStatusResponse statusResponse(InspectionSession session) {
        return new SessionStatusResponse(
                session.getSessionKey(),
                session.getStatus(),
                offset(session.getExpiresAt()));
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.INSPECTION_AGENT_UNAUTHORIZED);
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.INSPECTION_AGENT_UNAUTHORIZED);
        }
        return token;
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
