package com.c203.limit.global.api;

import java.util.Map;

import com.c203.limit.global.response.ApiResponse;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController implements HealthApi {

    @Override
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
