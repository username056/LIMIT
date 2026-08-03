package com.c203.limit.domain.chat.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.c203.limit.domain.chat.dto.response.ChatRoomResponse;
import com.c203.limit.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.service.ChatRoomCreateResult;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.global.security.CurrentUser;
import com.c203.limit.global.response.CursorResponse;

@ExtendWith(MockitoExtension.class)
class ChatRoomControllerTests {

    private static final Long LISTING_ID = 10L;
    private static final Long BUYER_ID = 20L;
    private static final Long SELLER_ID = 30L;
    private static final Long ROOM_ID = 40L;

    @Mock ChatRoomService chatRoomService;
    @Mock CurrentUser currentUser;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatRoomController(chatRoomService, currentUser))
                .build();
    }

    @Test
    void returns201WhenChatRoomIsCreated() throws Exception {
        ChatRoomResponse response = response();
        when(currentUser.memberId()).thenReturn(BUYER_ID);
        when(chatRoomService.createOrGet(LISTING_ID, BUYER_ID))
                .thenReturn(ChatRoomCreateResult.created(response));

        mockMvc.perform(post("/api/v1/listings/{listingId}/chat-rooms", LISTING_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.data.listingId").value(LISTING_ID))
                .andExpect(jsonPath("$.data.buyerId").value(BUYER_ID))
                .andExpect(jsonPath("$.data.sellerId").value(SELLER_ID))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void returns200WhenChatRoomAlreadyExists() throws Exception {
        ChatRoomResponse response = response();
        when(currentUser.memberId()).thenReturn(BUYER_ID);
        when(chatRoomService.createOrGet(LISTING_ID, BUYER_ID))
                .thenReturn(ChatRoomCreateResult.existing(response));

        mockMvc.perform(post("/api/v1/listings/{listingId}/chat-rooms", LISTING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.data.listingId").value(LISTING_ID));
    }

    @Test
    void returnsCurrentMembersChatRooms() throws Exception {
        ChatRoomSummaryResponse summary = new ChatRoomSummaryResponse(
                ROOM_ID, LISTING_ID, SELLER_ID, "판매자", "상품", "https://cdn/image.jpg",
                "오늘 오후에 가능하실까요?", "ACTIVE", 50L, 7L, null, 2L, 5L, null);
        when(currentUser.memberId()).thenReturn(BUYER_ID);
        when(chatRoomService.findRooms(BUYER_ID, 100L, 10))
                .thenReturn(new CursorResponse<>(List.of(summary), "40", true));

        mockMvc.perform(get("/api/v1/chat-rooms")
                        .param("cursor", "100")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.data.content[0].counterpartId").value(SELLER_ID))
                .andExpect(jsonPath("$.data.content[0].counterpartNickname").value("판매자"))
                .andExpect(jsonPath("$.data.content[0].listingTitle").value("상품"))
                .andExpect(jsonPath("$.data.content[0].lastMessagePreview").value("오늘 오후에 가능하실까요?"))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(2))
                .andExpect(jsonPath("$.data.content[0].counterpartLastReadSequence").value(5))
                .andExpect(jsonPath("$.data.nextCursor").value("40"))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void returnsMessagesAfterLastReceivedSequence() throws Exception {
        ChatMessageResponse message = new ChatMessageResponse(
                101L, 8L, SELLER_ID, new UUID(0L, 1L), "TEXT", "안녕하세요", "SENT",
                LocalDateTime.of(2026, 7, 23, 12, 0), List.of(), null, null);
        when(currentUser.memberId()).thenReturn(BUYER_ID);
        when(chatRoomService.findMessages(ROOM_ID, BUYER_ID, null, 7L, 10))
                .thenReturn(new CursorResponse<>(List.of(message), null, false));

        mockMvc.perform(get("/api/v1/chat-rooms/{roomId}/messages", ROOM_ID)
                        .param("afterSeq", "7")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].messageId").value(101L))
                .andExpect(jsonPath("$.data.content[0].roomSequence").value(8L))
                .andExpect(jsonPath("$.data.content[0].type").value("TEXT"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void leavesChatRoom() throws Exception {
        when(currentUser.memberId()).thenReturn(BUYER_ID);

        mockMvc.perform(delete("/api/v1/chat-rooms/{roomId}", ROOM_ID))
                .andExpect(status().isNoContent());

        verify(chatRoomService).leaveRoom(ROOM_ID, BUYER_ID);
    }

    private ChatRoomResponse response() {
        return new ChatRoomResponse(ROOM_ID, LISTING_ID, BUYER_ID, SELLER_ID, "ACTIVE", null);
    }
}
