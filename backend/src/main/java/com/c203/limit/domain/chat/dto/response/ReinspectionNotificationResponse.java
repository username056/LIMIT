package com.c203.limit.domain.chat.dto.response;

import com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent;
import java.util.List;

public record ReinspectionNotificationResponse(
        String requestKey,
        int pendingRequestCount,
        String reason,
        List<Item> items,
        Action action) {

    public static ReinspectionNotificationResponse from(
            ReinspectionNotificationEvent event) {
        return new ReinspectionNotificationResponse(
                event.requestKey(),
                event.pendingRequestCount(),
                event.reason(),
                event.items().stream()
                        .map(item -> new Item(item.name(), item.requestContent()))
                        .toList(),
                event.type() == ReinspectionNotificationEvent.Type.REQUESTED
                        ? new Action("START_RECAPTURE", "바로 재촬영하기")
                        : null);
    }

    public record Item(String name, String requestContent) {}

    public record Action(String type, String label) {}
}
