package com.c203.limit.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.domain.chat.service.ChatRoomService.ChatMessageSendResult;
import com.c203.limit.global.security.CurrentUser;

@ExtendWith(MockitoExtension.class)
class ChatMessageControllerTests {
    @Mock ChatRoomService chatRoomService;
    @Mock CurrentUser currentUser;
    @Mock SimpMessagingTemplate messagingTemplate;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ChatMessageController(chatRoomService, currentUser, messagingTemplate))
                .build();
    }

    @Test
    void storesAndBroadcastsNewMessage() throws Exception {
        UUID clientMessageId = UUID.randomUUID();
        ChatMessageResponse message = new ChatMessageResponse(
                50L, 3L, 20L, clientMessageId, "TEXT", "안녕하세요", "SENT",
                LocalDateTime.of(2026, 7, 28, 9, 0), List.of(), null, null);
        when(currentUser.memberId()).thenReturn(20L);
        when(chatRoomService.sendMessage(eq(10L), eq(20L), any()))
                .thenReturn(new ChatMessageSendResult(message, true));

        mockMvc.perform(post("/api/v1/chat-rooms/10/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientMessageId":"%s","type":"TEXT","content":"안녕하세요","mediaIds":[]}
                                """.formatted(clientMessageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messageId").value(50L))
                .andExpect(jsonPath("$.data.roomSequence").value(3L));

        verify(messagingTemplate).convertAndSend(eq("/sub/chat-rooms/10"), any(Object.class));
    }

    @Test
    void returnsExistingMessageWithoutBroadcastingAgain() throws Exception {
        UUID clientMessageId = UUID.randomUUID();
        ChatMessageResponse message = new ChatMessageResponse(
                50L, 3L, 20L, clientMessageId, "TEXT", "안녕하세요", "SENT",
                LocalDateTime.of(2026, 7, 28, 9, 0), List.of(), null, null);
        when(currentUser.memberId()).thenReturn(20L);
        when(chatRoomService.sendMessage(eq(10L), eq(20L), any()))
                .thenReturn(new ChatMessageSendResult(message, false));

        mockMvc.perform(post("/api/v1/chat-rooms/10/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientMessageId":"%s","type":"TEXT","content":"안녕하세요","mediaIds":[]}
                                """.formatted(clientMessageId)))
                .andExpect(status().isOk());
    }
}
