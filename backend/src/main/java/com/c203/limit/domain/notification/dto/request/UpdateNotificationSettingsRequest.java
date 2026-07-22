package com.c203.limit.domain.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateNotificationSettingsRequest", description = "알림 설정 부분 수정 요청")
public class UpdateNotificationSettingsRequest {

    @Schema(description = "드롭 알림 변경값", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Boolean isDropNotificationEnabled;

    @Schema(description = "구매 결과 알림 변경값", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Boolean isPurchaseResultNotificationEnabled;

    @Schema(description = "주문 알림 변경값", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Boolean isOrderNotificationEnabled;

    @Schema(description = "배송 알림 변경값", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Boolean isShippingNotificationEnabled;

    @Schema(description = "이메일 알림 변경값", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Boolean isEmailNotificationEnabled;

    @Schema(description = "웹 알림 변경값", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Boolean isWebNotificationEnabled;
}
