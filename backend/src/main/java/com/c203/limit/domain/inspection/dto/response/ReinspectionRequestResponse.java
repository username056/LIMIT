package com.c203.limit.domain.inspection.dto.response;

import com.c203.limit.domain.inspection.entity.ReinspectionRequest;
import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import java.time.LocalDateTime;
import java.util.List;

public record ReinspectionRequestResponse(
        String requestKey,
        String status,
        String reason,
        List<Item> items,
        LocalDateTime requestedAt,
        LocalDateTime completedAt) {

    public static ReinspectionRequestResponse from(
            ReinspectionRequest request, List<ReinspectionRequestItem> items) {
        return new ReinspectionRequestResponse(
                request.getRequestKey(),
                request.getStatus().name(),
                request.getReason(),
                items.stream().map(Item::from).toList(),
                request.getRequestedAt(),
                request.getCompletedAt());
    }

    public record Item(
            Long checklistItemId,
            String itemName,
            String requestContent,
            int displayOrder) {
        private static Item from(ReinspectionRequestItem item) {
            return new Item(
                    item.getListingChecklistItem().getId(),
                    item.getItemNameSnapshot(),
                    item.getRequestContent(),
                    item.getDisplayOrder());
        }
    }
}
