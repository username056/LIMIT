package com.c203.limit.domain.call.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record CallAppointmentUpdatedNotificationEvent(
        UUID eventId,
        Long appointmentId,
        Long chatRoomId,
        Long actorId,
        LocalDateTime scheduledAt,
        String memo) {}
