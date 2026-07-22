package com.c203.limit.domain.chat.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.c203.limit.domain.chat.dto.response.ChatRoomResponse;
import com.c203.limit.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.c203.limit.domain.chat.service.ChatRoomCreateResult;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.CursorResponse;
import com.c203.limit.global.security.CurrentUser;

@RestController
public class ChatRoomController implements ChatRoomApi {
    private final ChatRoomService chatRoomService;
    private final CurrentUser currentUser;

    public ChatRoomController(ChatRoomService chatRoomService, CurrentUser currentUser) {
        this.chatRoomService = chatRoomService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createOrGet(Long listingId) {
        ChatRoomCreateResult result = chatRoomService.createOrGet(listingId, currentUser.memberId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.response()));
    }

    @Override
    public ResponseEntity<ApiResponse<CursorResponse<ChatRoomSummaryResponse>>> findRooms(Long cursor, int size) {
        CursorResponse<ChatRoomSummaryResponse> response = chatRoomService.findRooms(
                currentUser.memberId(), cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
