package com.c203.limit.domain.chat.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.c203.limit.domain.chat.dto.response.ChatRoomResponse;
import com.c203.limit.domain.chat.service.ChatRoomCreateResult;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.global.security.CurrentUser;

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

    private ChatRoomResponse response() {
        return new ChatRoomResponse(ROOM_ID, LISTING_ID, BUYER_ID, SELLER_ID, "ACTIVE", null);
    }
}
