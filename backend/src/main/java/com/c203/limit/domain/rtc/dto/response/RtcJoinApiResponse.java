package com.c203.limit.domain.rtc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RtcJoinApiResponse", description = "WebRTC 입장 정보 응답")
public record RtcJoinApiResponse(RtcJoinResponse data, Object meta) {}
