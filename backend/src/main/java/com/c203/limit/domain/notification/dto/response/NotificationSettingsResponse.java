package com.c203.limit.domain.notification.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "NotificationSettingsResponse", description = "알림 수신 설정")
public class NotificationSettingsResponse {

    @Schema(description = "드롭 시작 알림 수신 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isDropNotificationEnabled;

    @Schema(description = "당첨·낙찰·순번 결과 알림 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isPurchaseResultNotificationEnabled;

    @Schema(description = "주문·결제 알림 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isOrderNotificationEnabled;

    @Schema(description = "배송 알림 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isShippingNotificationEnabled;

    @Schema(description = "이메일 채널 알림 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isEmailNotificationEnabled;

    @Schema(description = "웹 실시간 알림 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isWebNotificationEnabled;

    @Schema(description = "수정 시각", example = "2026-07-16T11:30:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime updatedAt;
}
