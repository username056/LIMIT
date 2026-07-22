package com.c203.limit.global.api;

import java.util.Map;

import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "00. 헬스체크")
@RequestMapping("/api/v1")
public interface HealthApi {
    @Operation(summary = "애플리케이션 상태 확인")
    @GetMapping("/health")
    ApiResponse<Map<String, String>> health();
}
