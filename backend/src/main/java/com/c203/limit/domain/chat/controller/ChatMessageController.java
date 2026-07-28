package com.c203.limit.domain.chat.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.c203.limit.domain.chat.dto.request.ChatMessageSendRequest;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.domain.chat.service.ChatRoomService.ChatMessageSendResult;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "04. 채팅")
public class ChatMessageController {
    private final ChatRoomService chatRoomService;
    private final CurrentUser currentUser;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageController(
            ChatRoomService chatRoomService,
            CurrentUser currentUser,
            SimpMessagingTemplate messagingTemplate) {
        this.chatRoomService = chatRoomService;
        this.currentUser = currentUser;
        this.messagingTemplate = messagingTemplate;
    }

    @Operation(
            summary = "채팅 메시지 전송",
            description = "clientMessageId가 같은 재요청은 기존 메시지를 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201", description = "메시지 저장 및 실시간 발행"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "중복 clientMessageId의 기존 메시지 반환"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "CHAT_ROOM_ACCESS_DENIED")
    })
    @PostMapping("/api/v1/chat-rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> send(
            @PathVariable Long roomId, @Valid @RequestBody ChatMessageSendRequest request) {
        ChatMessageSendResult result =
                chatRoomService.sendMessage(roomId, currentUser.memberId(), request);
        if (result.created()) {
            messagingTemplate.convertAndSend(
                    "/sub/chat-rooms/" + roomId,
                    ChatEventResponse.message(roomId, result.message()));
        }
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.message()));
    }
}
