package com.c203.limit.domain.rtc.controller;

import com.c203.limit.domain.rtc.dto.request.CreateCallRequest;
import com.c203.limit.domain.rtc.dto.request.EndRtcSessionRequest;
import com.c203.limit.domain.rtc.dto.request.MarkRtcConnectedRequest;
import com.c203.limit.domain.rtc.dto.request.RespondCallRequest;
import com.c203.limit.domain.rtc.dto.response.CallResponse;
import com.c203.limit.domain.rtc.dto.response.RtcJoinResponse;
import com.c203.limit.domain.rtc.dto.response.RtcSessionResponse;
import com.c203.limit.domain.rtc.service.RtcCallService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RtcCallController implements RtcCallApi {
    private final RtcCallService service;
    private final CurrentUser currentUser;

    public RtcCallController(RtcCallService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<CallResponse>> request(
            Long roomId, CreateCallRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.request(roomId, currentUser.memberId(), request)));
    }

    @Override
    public ResponseEntity<ApiResponse<List<CallResponse>>> findMine() {
        return ResponseEntity.ok(ApiResponse.ok(service.findMine(currentUser.memberId())));
    }

    @Override
    public ResponseEntity<ApiResponse<CallResponse>> findCall(Long callId) {
        return ResponseEntity.ok(ApiResponse.ok(service.findCall(callId, currentUser.memberId())));
    }

    @Override
    public ResponseEntity<ApiResponse<CallResponse>> respond(
            Long callId, RespondCallRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.respond(callId, currentUser.memberId(), request)));
    }

    @Override
    public ResponseEntity<ApiResponse<RtcJoinResponse>> join(Long sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(service.join(sessionId, currentUser.memberId())));
    }

    @Override
    public ResponseEntity<ApiResponse<RtcSessionResponse>> findSession(Long sessionId) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.findSession(sessionId, currentUser.memberId())));
    }

    @Override
    public ResponseEntity<ApiResponse<RtcSessionResponse>> connected(
            Long sessionId, MarkRtcConnectedRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.connected(sessionId, currentUser.memberId(), request)));
    }

    @Override
    public ResponseEntity<ApiResponse<RtcSessionResponse>> end(
            Long sessionId, EndRtcSessionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.end(sessionId, currentUser.memberId(), request)));
    }
}
