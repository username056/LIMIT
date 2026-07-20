package com.c203.limit.global.api;

import java.util.Map;

import com.c203.limit.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HelloController {

    @GetMapping("/hello")
    public ApiResponse<Map<String, String>> hello() {
        return ApiResponse.ok(Map.of("message", "Spring Boot 백엔드와 연결되었습니다!"));
    }
}

