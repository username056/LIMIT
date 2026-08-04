package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.enums.InspectionUserResult;
import com.c203.limit.domain.inspection.enums.MeasurementStatus;
import com.c203.limit.domain.inspection.enums.TestType;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
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
            @Schema(nullable = true) InspectionUserResult userResult,
            @Schema(nullable = true) Map<String, Object> measuredValues,
            @NotNull OffsetDateTime testedAt,
            @Schema(nullable = true) @Size(max = 100) String errorCode) {}

    public record TestResultResponse(
            UUID clientResultId,
            Long listingId,
            @Schema(nullable = true) Long checklistItemId,
            TestType testType,
            MeasurementStatus measurementStatus,
            @Schema(nullable = true) InspectionUserResult userResult,
            @Schema(nullable = true) Map<String, Object> measuredValues,
            int attemptNo,
            boolean rawDataSaved,
            OffsetDateTime testedAt,
            OffsetDateTime createdAt,
            @Schema(nullable = true) String errorCode) {}

    public record TestResultSubmission(TestResultResponse response, boolean created) {}

    @Schema(name = "TestResultApiResponse", description = "Windows 선택검사 단건 공통 응답")
    public record TestResultApiResponse(
            TestResultResponse data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "TestResultListApiResponse", description = "Windows 선택검사 이력 공통 응답")
    public record TestResultListApiResponse(
            List<TestResultResponse> data, @Schema(nullable = true) Object meta) {}
}
