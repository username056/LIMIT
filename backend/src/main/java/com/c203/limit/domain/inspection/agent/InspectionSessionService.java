package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.AgentUploadResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CreateAgentUploadRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.PairResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SessionResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SessionStatusResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SubmitTestResultRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.TestResultResponse;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
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
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionSessionService {
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InspectionSessionRepository sessionRepository;
    private final ListingRepository listingRepository;
    private final ListingChecklistItemRepository checklistItemRepository;
    private final EvidenceUploadService evidenceUploadService;
    private final DxdiagParsingService dxdiagParsingService;
    private final BatteryReportParsingService batteryReportParsingService;
    private final Clock clock;

    public InspectionSessionService(
            InspectionSessionRepository sessionRepository,
            ListingRepository listingRepository,
            ListingChecklistItemRepository checklistItemRepository,
            EvidenceUploadService evidenceUploadService,
            DxdiagParsingService dxdiagParsingService,
            BatteryReportParsingService batteryReportParsingService,
            Clock clock) {
        this.sessionRepository = sessionRepository;
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

    /**
     * TODO(다음 세션): inspection_session_test_result 저장, UNIQUE(session_id, client_result_id)
     * 멱등성, UNIQUE(session_id, test_type, attempt_no) + TransactionTemplate 기반 재시도,
     * ListingChecklistItem.applyDeviceCheckResult() 연동을 구현한다. 지금은 세션 상태만 검증하고
     * 요청을 그대로 echo하는 스텁이다(결과 미저장, attemptNo 항상 1, rawDataSaved 항상 false).
     */
    @Transactional
    public TestResultResponse submitTestResult(
            String authorization, String sessionKey, SubmitTestResultRequest request) {
        InspectionSession session = authorize(authorization, sessionKey);
        if (session.getStatus() != InspectionSessionStatus.PAIRED
                && session.getStatus() != InspectionSessionStatus.UPLOADING) {
            throw new BusinessException(ErrorCode.INSPECTION_SESSION_INVALID_STATE);
        }
        return new TestResultResponse(
                request.clientResultId(),
                request.testType(),
                request.measurementStatus(),
                request.userResult(),
                request.measuredValues(),
                1,
                false,
                request.testedAt(),
                offset(now()),
                request.errorCode());
    }

    /** TODO(다음 세션): inspection_session_test_result에서 실제 이력을 조회한다. 지금은 빈 목록만 반환한다. */
    @Transactional(readOnly = true)
    public List<TestResultResponse> listTestResults(Long sellerId, String sessionKey) {
        InspectionSession session = requireSession(sessionKey);
        if (!Objects.equals(session.getSellerId(), sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return List.of();
    }

    private InspectionSession authorize(String authorization, String sessionKey) {
        InspectionSession session = requireSession(sessionKey);
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
