package com.c203.limit.domain.chat.dto.response;

public record ChatEventResponse(
        String type,
        Long roomId,
        ChatMessageResponse message,
        Long readerId,
        Long lastReadSeq,
        ReinspectionNotificationResponse reinspection) {

    public static ChatEventResponse message(Long roomId, ChatMessageResponse message) {
        return new ChatEventResponse("MESSAGE", roomId, message, null, null, null);
    }

    public static ChatEventResponse read(Long roomId, Long readerId, Long lastReadSeq) {
        return new ChatEventResponse("READ", roomId, null, readerId, lastReadSeq, null);
    }

    public static ChatEventResponse callAppointmentUpdated(Long roomId) {
        return new ChatEventResponse(
                "CALL_APPOINTMENT_UPDATED", roomId, null, null, null, null);
    }

    public static ChatEventResponse reinspectionRequested(
            Long roomId,
            ChatMessageResponse message,
            ReinspectionNotificationResponse reinspection) {
        return new ChatEventResponse(
                "REINSPECTION_REQUESTED", roomId, message, null, null, reinspection);
    }

    public static ChatEventResponse reinspectionCompleted(
            Long roomId,
            ChatMessageResponse message,
            ReinspectionNotificationResponse reinspection) {
        return new ChatEventResponse(
                "REINSPECTION_COMPLETED", roomId, message, null, null, reinspection);
    }
}
