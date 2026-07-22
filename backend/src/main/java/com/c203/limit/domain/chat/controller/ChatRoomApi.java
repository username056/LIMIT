package com.c203.limit.domain.chat.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.c203.limit.domain.chat.dto.response.ChatRoomResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "03. 채팅")
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
}
