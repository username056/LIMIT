package com.c203.limit.domain.rtc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CallApiResponse", description = "실시간 확인 요청 응답")
public record CallApiResponse(CallResponse data, Object meta) {}
