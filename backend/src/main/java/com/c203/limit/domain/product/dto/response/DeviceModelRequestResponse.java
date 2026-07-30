package com.c203.limit.domain.product.dto.response;

import com.c203.limit.domain.product.entity.DeviceModelRequest;
import java.time.LocalDateTime;

public record DeviceModelRequestResponse(
        Long requestId,
        Long categoryId,
        String manufacturer,
        String modelName,
        String modelCode,
        String osFamily,
        String status,
        LocalDateTime createdAt,
        Long requestedByMemberId,
        Long resolvedCategoryId,
        Long reviewedByAdminId,
        String reviewNote,
        LocalDateTime updatedAt) {

    public static DeviceModelRequestResponse from(DeviceModelRequest request) {
        return new DeviceModelRequestResponse(
                request.getId(),
                request.getParentCategoryId(),
                request.getManufacturer(),
                request.getModelName(),
                request.getModelCode(),
                request.getOsFamily().name(),
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getRequestedByMemberId(),
                request.getResolvedCategoryId(),
                request.getReviewedByAdminId(),
                request.getReviewNote(),
                request.getUpdatedAt());
    }
}
