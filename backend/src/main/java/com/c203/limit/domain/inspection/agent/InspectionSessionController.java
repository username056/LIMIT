package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CompleteAgentUploadRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CreateAgentUploadRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.CreateSessionRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.PairRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SubmitTestResultRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.TestResultApiResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.TestResultListApiResponse;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.TestResultSubmission;
import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.TestResultResponse;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
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

    @PostMapping("/api/v1/inspection-agent/sessions/{sessionKey}/test-results")
    @Operation(
            summary = "Windows 선택검사 결과 제출",
            description =
                    "clientResultId로 멱등 처리하며 최초 저장은 201, 동일 payload 재전송은 200을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "검사 결과 최초 저장",
                content = @Content(schema = @Schema(implementation = TestResultApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "동일한 검사 결과 재전송",
                content = @Content(schema = @Schema(implementation = TestResultApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "INSPECTION_TEST_RESULT_INVALID"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "INSPECTION_AGENT_UNAUTHORIZED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description =
                        "INSPECTION_SESSION_INVALID_STATE / INSPECTION_TEST_RESULT_IDEMPOTENCY_CONFLICT")
    })
    public ResponseEntity<ApiResponse<TestResultResponse>> submitTestResult(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sessionKey,
            @Valid @RequestBody SubmitTestResultRequest request) {
        TestResultSubmission submission =
                service.submitTestResult(authorization, sessionKey, request);
        HttpStatus status = submission.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(submission.response()));
    }

    @GetMapping("/api/v1/inspection-sessions/{sessionKey}/test-results")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Windows 선택검사 결과 이력 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "저장된 검사 결과 이력",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        TestResultListApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "세션 소유권 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "INSPECTION_SESSION_NOT_FOUND")
    })
    public ResponseEntity<ApiResponse<List<TestResultResponse>>> testResults(
            @PathVariable String sessionKey) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.listTestResults(currentUser.memberId(), sessionKey)));
    }
}
