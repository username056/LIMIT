package com.c203.limit.notification.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "14. 알림")
public interface NotificationApi {

    @Operation(operationId = "notification01", summary = "알림 수신 설정 조회", description = "요청\n권한: MEMBER\n\n응답\n200 OK\n{\"data\":{\"dropNotificationEnabled\":true,\"purchaseResultNotificationEnabled\":true,\"orderNotificationEnabled\":true,\"shippingNotificationEnabled\":true,\"emailNotificationEnabled\":true,\"webNotificationEnabled\":true}}", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/NotificationSettingsResponse")))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/notification-settings", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> notification01();
    @Operation(operationId = "notification02", summary = "알림 수신 설정 수정", description = "요청\n권한: MEMBER\nBody:\n{\"dropNotificationEnabled\":true,\"purchaseResultNotificationEnabled\":true,\"orderNotificationEnabled\":false,\"shippingNotificationEnabled\":true,\"emailNotificationEnabled\":false,\"webNotificationEnabled\":true}\n처리: 전달된 필드만 변경\n\n응답\n200 OK\n{\"data\":{\"dropNotificationEnabled\":true,\"purchaseResultNotificationEnabled\":true,\"orderNotificationEnabled\":false,\"shippingNotificationEnabled\":true,\"emailNotificationEnabled\":false,\"webNotificationEnabled\":true,\"updatedAt\":\"2026-07-16T11:30:00+09:00\"}}\n오류: 400 INVALID_INPUT", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/NotificationSettingsResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/members/me/notification-settings", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> notification02(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateNotificationSettingsRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
}
