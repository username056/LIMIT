package com.c203.limit.domain.chat.websocket;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import com.c203.limit.domain.chat.dto.request.ChatMessageSendRequest;
import com.c203.limit.domain.chat.dto.request.ChatReadRequest;
import com.c203.limit.domain.chat.dto.response.ChatAckResponse;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.service.ChatRoomService.ChatMessageSendResult;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.AuthenticatedUser;

import jakarta.validation.Valid;

@Controller
@Validated
public class ChatMessageWebSocketController {
    private static final String ACK_DESTINATION = "/queue/chat-acks";

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatWebSocketErrorPublisher errorPublisher;

    public ChatMessageWebSocketController(
            ChatRoomService chatRoomService,
            SimpMessagingTemplate messagingTemplate,
            ChatWebSocketErrorPublisher errorPublisher) {
        this.chatRoomService = chatRoomService;
        this.messagingTemplate = messagingTemplate;
        this.errorPublisher = errorPublisher;
    }

    @MessageMapping("/chat-rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Valid @Payload ChatMessageSendRequest request,
            Principal principal) {
        AuthenticatedUser user = authenticatedUser(principal);
        try {
            ChatMessageSendResult result =
                    chatRoomService.sendMessage(roomId, user.id(), request);
            ChatMessageResponse message = result.message();
            messagingTemplate.convertAndSendToUser(
                    user.getName(), ACK_DESTINATION, ChatAckResponse.sent(message));
            if (result.created()) {
                messagingTemplate.convertAndSend(
                        "/sub/chat-rooms/" + roomId, ChatEventResponse.message(roomId, message));
            }
        } catch (BusinessException exception) {
            errorPublisher.publish(user, exception);
        }
    }

    @MessageMapping("/chat-rooms/{roomId}/read")
    public void readMessages(
            @DestinationVariable Long roomId,
            @Valid @Payload ChatReadRequest request,
            Principal principal) {
        AuthenticatedUser user = authenticatedUser(principal);
        try {
            Long lastReadSeq = chatRoomService.readMessages(roomId, user.id(), request);
            messagingTemplate.convertAndSend(
                    "/sub/chat-rooms/" + roomId,
                    ChatEventResponse.read(roomId, user.id(), lastReadSeq));
        } catch (BusinessException exception) {
            errorPublisher.publish(user, exception);
        }
    }

    private AuthenticatedUser authenticatedUser(Principal principal) {
        if (principal instanceof AuthenticatedUser user && "MEMBER".equals(user.accountType())) {
            return user;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
