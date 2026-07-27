package com.c203.limit.domain.rtc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "CallListApiResponse", description = "실시간 확인 요청 목록 응답")
public record CallListApiResponse(List<CallResponse> data, Object meta) {}
