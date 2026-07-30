package com.c203.limit.domain.inspection.event;

import java.util.List;
import java.util.UUID;

public record ReinspectionNotificationEvent(
        UUID eventId,
        Type type,
        Long reinspectionRequestId,
        String requestKey,
        Long listingId,
        Long chatRoomId,
        Long actorId,
        Long recipientId,
        int pendingRequestCount,
        String reason,
        List<Item> items) {

    public enum Type {
        REQUESTED,
        COMPLETED
    }

    public record Item(String name, String requestContent) {}
}
