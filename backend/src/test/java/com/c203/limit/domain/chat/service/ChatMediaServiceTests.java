package com.c203.limit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.c203.limit.domain.chat.entity.ChatMedia;
import com.c203.limit.domain.chat.repository.ChatMediaRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ChatMediaServiceTests {
    @Mock ChatMediaRepository mediaRepository;
    @Mock ChatRoomParticipantRepository participantRepository;
    @TempDir Path tempDirectory;

    @Test
    void uploadsImageForParticipant() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);
        when(mediaRepository.save(any(ChatMedia.class))).thenAnswer(invocation -> {
            ChatMedia media = invocation.getArgument(0);
            ReflectionTestUtils.setField(media, "id", 30L);
            return media;
        });

        var response = service.upload(
                10L,
                20L,
                new MockMultipartFile("file", "phone.png", "image/png", new byte[] {1, 2, 3}));

        assertThat(response.mediaId()).isEqualTo(30L);
        assertThat(response.type()).isEqualTo("IMAGE");
        assertThat(response.contentUrl()).isEqualTo("/api/v1/chat-media/30/content");
        assertThat(tempDirectory.toFile().listFiles()).hasSize(1);
    }

    @Test
    void rejectsUnsupportedFileType() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.upload(
                        10L,
                        20L,
                        new MockMultipartFile("file", "document.pdf", "application/pdf", new byte[] {1})))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MEDIA_INVALID));
    }

    @Test
    void rejectsUploadByNonParticipant() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(false);

        assertThatThrownBy(() -> service.upload(
                        10L,
                        20L,
                        new MockMultipartFile("file", "phone.png", "image/png", new byte[] {1})))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    private ChatMediaService service() {
        return new ChatMediaService(mediaRepository, participantRepository, tempDirectory.toString());
    }
}
