package com.c203.limit.domain.rtc.controller;

import com.c203.limit.domain.rtc.dto.request.CreateCallRequest;
import com.c203.limit.domain.rtc.dto.request.EndRtcSessionRequest;
import com.c203.limit.domain.rtc.dto.request.MarkRtcConnectedRequest;
import com.c203.limit.domain.rtc.dto.request.RespondCallRequest;
import com.c203.limit.domain.rtc.dto.response.CallApiResponse;
import com.c203.limit.domain.rtc.dto.response.CallListApiResponse;
import com.c203.limit.domain.rtc.dto.response.CallResponse;
import com.c203.limit.domain.rtc.dto.response.RtcJoinApiResponse;
import com.c203.limit.domain.rtc.dto.response.RtcJoinResponse;
import com.c203.limit.domain.rtc.dto.response.RtcSessionApiResponse;
import com.c203.limit.domain.rtc.dto.response.RtcSessionResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Tag(name = "06. 선택적 1:1 실시간 확인")
@SecurityRequirement(name = "bearerAuth")
public interface RtcCallApi {
    @Operation(summary = "채팅방 상대에게 영상 확인 요청")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    content = @Content(schema = @Schema(implementation = CallApiResponse.class))))
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/chat-rooms/{roomId}/calls")
    ResponseEntity<ApiResponse<CallResponse>> request(
            @PathVariable Long roomId, @Valid @RequestBody CreateCallRequest request);

    @Operation(summary = "내 영상 확인 요청 목록")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content =
                            @Content(schema = @Schema(implementation = CallListApiResponse.class))))
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/calls")
    ResponseEntity<ApiResponse<List<CallResponse>>> findMine();

    @Operation(summary = "영상 확인 요청 상세")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @Content(schema = @Schema(implementation = CallApiResponse.class))))
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/calls/{callId}")
    ResponseEntity<ApiResponse<CallResponse>> findCall(@PathVariable Long callId);

    @Operation(summary = "영상 확인 요청 수락 또는 거절")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @Content(schema = @Schema(implementation = CallApiResponse.class))))
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/calls/{callId}/response")
    ResponseEntity<ApiResponse<CallResponse>> respond(
            @PathVariable Long callId, @Valid @RequestBody RespondCallRequest request);

    @Operation(summary = "통화 화면 재입장을 포함한 단기 시그널링 토큰 발급")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content =
                            @Content(schema = @Schema(implementation = RtcJoinApiResponse.class))))
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/rtc-sessions/{sessionId}/join")
    ResponseEntity<ApiResponse<RtcJoinResponse>> join(@PathVariable Long sessionId);

    @Operation(summary = "통화 세션과 상품 체크리스트 조회")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content =
                            @Content(
                                    schema =
                                            @Schema(implementation = RtcSessionApiResponse.class))))
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/rtc-sessions/{sessionId}")
    ResponseEntity<ApiResponse<RtcSessionResponse>> findSession(@PathVariable Long sessionId);

    @Operation(summary = "WebRTC 연결 성공 기록")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content =
                            @Content(
                                    schema =
                                            @Schema(implementation = RtcSessionApiResponse.class))))
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/rtc-sessions/{sessionId}/connected")
    ResponseEntity<ApiResponse<RtcSessionResponse>> connected(
            @PathVariable Long sessionId, @Valid @RequestBody MarkRtcConnectedRequest request);

    @Operation(summary = "통화 종료 및 확인 체크리스트·메모 저장")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content =
                            @Content(
                                    schema =
                                            @Schema(implementation = RtcSessionApiResponse.class))))
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/rtc-sessions/{sessionId}/end")
    ResponseEntity<ApiResponse<RtcSessionResponse>> end(
            @PathVariable Long sessionId, @Valid @RequestBody EndRtcSessionRequest request);
}
