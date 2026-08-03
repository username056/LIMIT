package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CompleteAgentUploadRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CreateAgentUploadRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CreateSessionRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.PairRequest;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionSessionController {
    private final InspectionSessionService service;
    private final CurrentUser currentUser;

    public InspectionSessionController(InspectionSessionService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/v1/inspection-sessions")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(currentUser.memberId(), request.listingId())));
    }

    @GetMapping("/api/v1/inspection-sessions/{sessionKey}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<?>> status(@PathVariable String sessionKey) {
        return ResponseEntity.ok(ApiResponse.ok(service.status(currentUser.memberId(), sessionKey)));
    }

    @PostMapping("/api/v1/inspection-agent/sessions/pair")
    public ResponseEntity<ApiResponse<?>> pair(@Valid @RequestBody PairRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.pair(request.pairingCode(), request.collectorVersion())));
    }

    @PostMapping("/api/v1/inspection-agent/sessions/{sessionKey}/uploads")
    public ResponseEntity<ApiResponse<?>> createUpload(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sessionKey,
            @Valid @RequestBody CreateAgentUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createUpload(authorization, sessionKey, request)));
    }

    @PostMapping("/api/v1/inspection-agent/sessions/{sessionKey}/uploads/{uploadId}/complete")
    public ResponseEntity<ApiResponse<?>> completeUpload(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sessionKey,
            @PathVariable String uploadId,
            @Valid @RequestBody CompleteAgentUploadRequest request) {
        service.completeUpload(authorization, sessionKey, uploadId, request.parserType());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/api/v1/inspection-agent/sessions/{sessionKey}/complete")
    public ResponseEntity<ApiResponse<?>> complete(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sessionKey) {
        return ResponseEntity.ok(ApiResponse.ok(service.complete(authorization, sessionKey)));
    }
}
