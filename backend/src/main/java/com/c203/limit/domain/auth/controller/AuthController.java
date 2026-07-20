package com.c203.limit.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.c203.limit.domain.auth.dto.request.LoginRequest;
import com.c203.limit.domain.auth.dto.request.LogoutRequest;
import com.c203.limit.domain.auth.dto.request.SignupRequest;
import com.c203.limit.domain.auth.dto.request.TokenRefreshRequest;
import com.c203.limit.domain.auth.service.AuthService;
import com.c203.limit.domain.auth.service.SocialAuthService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class AuthController implements AuthApi {
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final SocialAuthService socialAuthService;
    private final com.c203.limit.global.security.CurrentUser currentUser;
    public AuthController(AuthService authService, SocialAuthService socialAuthService,
            com.c203.limit.global.security.CurrentUser currentUser, ObjectMapper objectMapper) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }
    @Override public ResponseEntity<Void> auth01(String email) {
        return response(ResponseEntity.ok(ApiResponse.ok(authService.emailAvailability(email))));
    }
    @Override public ResponseEntity<Void> auth02(String nickname) {
        return response(ResponseEntity.ok(ApiResponse.ok(authService.nicknameAvailability(nickname))));
    }
    @Override public ResponseEntity<Void> auth03(Object body) {
        JsonNode json = json(body);
        var request = new SignupRequest(text(json, "email"), text(json, "password"), text(json, "nickname"), optional(json, "phone"));
        return response(ResponseEntity.status(201).body(ApiResponse.ok(authService.signup(request))));
    }
    @Override public ResponseEntity<Void> auth04(Object body) {
        JsonNode json = json(body);
        return response(ResponseEntity.ok(ApiResponse.ok(authService.login(new LoginRequest(text(json, "email"), text(json, "password"))))));
    }
    @Override public ResponseEntity<Void> auth05(Object body) {
        JsonNode json = json(body);
        return response(ResponseEntity.ok(ApiResponse.ok(authService.refresh(new TokenRefreshRequest(text(json, "refreshToken")).getRefreshToken()))));
    }
    @Override public ResponseEntity<Void> auth06(Object body) {
        JsonNode json = json(body);
        authService.logout(new LogoutRequest(text(json, "refreshToken")).getRefreshToken());
        return ResponseEntity.noContent().build();
    }
    @Override public ResponseEntity<Void> auth07(String provider, Object body) {
        JsonNode json = json(body);
        return response(ResponseEntity.ok(ApiResponse.ok(socialAuthService.login(provider,
                text(json, "authorizationCode"), text(json, "redirectUri")))));
    }
    @Override public ResponseEntity<Void> auth08() {
        return response(ResponseEntity.ok(ApiResponse.ok(socialAuthService.accounts(currentUser.memberId()))));
    }
    @Override public ResponseEntity<Void> auth09(Long socialAccountId) {
        socialAuthService.unlink(currentUser.memberId(), socialAccountId);
        return ResponseEntity.noContent().build();
    }
    private JsonNode json(Object body) {
        if (body == null) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return objectMapper.valueToTree(body);
    }
    private String text(JsonNode json, String field) {
        String value = optional(json, field);
        if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return value;
    }
    private String optional(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Void> response(ResponseEntity<?> source) { return (ResponseEntity) source; }
}
