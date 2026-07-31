package com.c203.limit.domain.chat.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.c203.limit.domain.chat.dto.response.ChatRoomResponse;
import com.c203.limit.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "04. 채팅")
public interface ChatRoomApi {
    @Operation(operationId = "chatBe01", summary = "1:1 채팅방 생성 또는 기존 방 반환",
            description = "권한: MEMBER\n구매자는 JWT, 판매자는 매물 정보에서 결정",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "채팅방 생성",
                    content = @Content(schema = @Schema(implementation = ChatRoomResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기존 채팅방 반환",
                    content = @Content(schema = @Schema(implementation = ChatRoomResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "SELF_CHAT_NOT_ALLOWED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CHAT_ROOM_CREATION_NOT_ALLOWED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/listings/{listingId}/chat-rooms",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ChatRoomResponse>> createOrGet(@PathVariable Long listingId);

    @Operation(operationId = "chatBe07", summary = "내 채팅방 목록 조회",
            description = "회원이 참여 중인 채팅방을 최신 생성 순으로 커서 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "채팅방 목록 반환",
            content = @Content(schema = @Schema(implementation = CursorResponse.class)))
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/chat-rooms",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<CursorResponse<ChatRoomSummaryResponse>>> findRooms(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size);

    @Operation(operationId = "chatBe08", summary = "채팅방 나가기",
            description = "현재 회원의 채팅 목록에서 방을 제거합니다. 상대방의 대화는 유지됩니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/chat-rooms/{roomId}")
    ResponseEntity<Void> leave(@PathVariable Long roomId);

    @Operation(operationId = "chatBe06", summary = "이전 메시지 조회 및 누락 복구",
            description = "beforeSeq는 과거 메시지를, afterSeq는 재접속 이후 누락 메시지를 조회하며 동시에 사용할 수 없습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메시지 목록 반환",
                    content = @Content(schema = @Schema(implementation = CursorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "CHAT_ROOM_ACCESS_DENIED")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/chat-rooms/{roomId}/messages",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<CursorResponse<ChatMessageResponse>>> findMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(defaultValue = "20") int size);
}
