package com.c203.limit.domain.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.c203.limit.domain.member.dto.request.ChangePasswordRequest;
import com.c203.limit.domain.member.dto.request.UpdateMemberRequest;
import com.c203.limit.domain.member.service.MemberService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class MemberController implements MemberApi {
    private final MemberService memberService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    public MemberController(MemberService memberService, CurrentUser currentUser, ObjectMapper objectMapper) {
        this.memberService = memberService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }
    @Override public ResponseEntity<Void> member01() {
        return response(ResponseEntity.ok(ApiResponse.ok(memberService.profile(currentUser.memberId()))));
    }
    @Override public ResponseEntity<Void> member02(Object body) {
        JsonNode json = json(body);
        var request = new UpdateMemberRequest(optional(json, "nickname"), optional(json, "phone"));
        return response(ResponseEntity.ok(ApiResponse.ok(memberService.update(currentUser.memberId(), request))));
    }
    @Override public ResponseEntity<Void> member03(Object body) {
        JsonNode json = json(body);
        memberService.changePassword(currentUser.memberId(),
                new ChangePasswordRequest(required(json, "currentPassword"), required(json, "newPassword")));
        return ResponseEntity.noContent().build();
    }
    private JsonNode json(Object body) {
        if (body == null) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return objectMapper.valueToTree(body);
    }
    private String required(JsonNode json, String field) {
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
