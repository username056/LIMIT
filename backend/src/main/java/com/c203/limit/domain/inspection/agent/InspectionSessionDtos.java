package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

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
}
