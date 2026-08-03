package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.enums.InspectionUserResult;
import com.c203.limit.domain.inspection.enums.MeasurementStatus;
import com.c203.limit.domain.inspection.enums.TestType;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class InspectionSessionDtos {
    private InspectionSessionDtos() {}

    public record CreateSessionRequest(@NotNull @Positive Long listingId) {}

    public record SessionResponse(
            String sessionKey,
            String pairingCode,
            InspectionSessionStatus status,
            OffsetDateTime expiresAt) {}

    public record SessionStatusResponse(
            String sessionKey, InspectionSessionStatus status, OffsetDateTime expiresAt) {}

    public record PairRequest(
            @NotBlank @Pattern(regexp = "\\d{6}") String pairingCode,
            @NotBlank String collectorVersion) {}

    public record PairResponse(
            String sessionKey, String agentToken, OffsetDateTime expiresAt) {}

    public record CreateAgentUploadRequest(
            @NotBlank String parserType,
            @NotBlank String filename,
            @NotBlank String contentType,
            @NotNull @Positive Long fileSize) {}

    public record AgentUploadResponse(
            String uploadId,
            String presignedUrl,
            java.util.Map<String, String> requiredHeaders) {
        public static AgentUploadResponse from(EvidenceUploadUrlResponse response) {
            return new AgentUploadResponse(
                    response.getUploadId(),
                    response.getPresignedUrl(),
                    response.getRequiredHeaders());
        }
    }

    public record CompleteAgentUploadRequest(@NotBlank String parserType) {}

    /**
     * attemptNo, checklistItemId, listingId, rawDataSaved는 서버가 세션·이력 기준으로 직접
     * 결정하므로 요청에 두지 않는다.
     */
    public record SubmitTestResultRequest(
            @NotNull UUID clientResultId,
            @NotNull TestType testType,
            @NotNull MeasurementStatus measurementStatus,
            InspectionUserResult userResult,
            Map<String, Object> measuredValues,
            @NotNull OffsetDateTime testedAt,
            String errorCode) {}

    public record TestResultResponse(
            UUID clientResultId,
            TestType testType,
            MeasurementStatus measurementStatus,
            InspectionUserResult userResult,
            Map<String, Object> measuredValues,
            int attemptNo,
            boolean rawDataSaved,
            OffsetDateTime testedAt,
            OffsetDateTime createdAt,
            String errorCode) {}
}
