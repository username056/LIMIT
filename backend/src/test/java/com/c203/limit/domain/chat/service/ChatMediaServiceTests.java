package com.c203.limit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.c203.limit.domain.chat.domain.MediaType;
import com.c203.limit.domain.chat.entity.ChatMedia;
import com.c203.limit.domain.chat.repository.ChatMediaRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class ChatMediaServiceTests {
    @Mock ChatMediaRepository mediaRepository;
    @Mock ChatRoomParticipantRepository participantRepository;
    @Mock S3Client s3Client;

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

    @Test
    void uploadsVideoWithSanitizedFilenameAndLowercasedExtension() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);
        when(mediaRepository.save(any(ChatMedia.class))).thenAnswer(invocation -> {
            ChatMedia media = invocation.getArgument(0);
            ReflectionTestUtils.setField(media, "id", 31L);
            return media;
        });

        var response = service.upload(
                10L,
                20L,
                new MockMultipartFile(
                        "file", "clips/../trip.MP4", "video/mp4", new byte[] {1, 2, 3}));

        assertThat(response.type()).isEqualTo("VIDEO");
        assertThat(response.originalFilename()).isEqualTo("trip.MP4");
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("media-bucket");
        assertThat(requestCaptor.getValue().key()).startsWith("chat/10/").endsWith(".mp4");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("video/mp4");
    }

    @Test
    void uploadsFileWithoutUsableNameUsingFallbackFilenameAndEmptyExtension() throws Exception {
        ChatMediaService service = service();
        MultipartFile missingName = mock(MultipartFile.class);
        when(missingName.isEmpty()).thenReturn(false);
        when(missingName.getContentType()).thenReturn("image/webp");
        when(missingName.getSize()).thenReturn(1L);
        when(missingName.getOriginalFilename()).thenReturn(null);
        when(missingName.getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[] {9}));
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);
        when(mediaRepository.save(any(ChatMedia.class))).thenAnswer(invocation -> {
            ChatMedia media = invocation.getArgument(0);
            ReflectionTestUtils.setField(media, "id", 32L);
            return media;
        });

        var fromNullName = service.upload(10L, 20L, missingName);
        var fromBlankName = service.upload(
                10L, 20L, new MockMultipartFile("file", "   ", "image/gif", new byte[] {9}));

        assertThat(fromNullName.originalFilename()).isEqualTo("upload");
        assertThat(fromBlankName.originalFilename()).isEqualTo("upload");
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, org.mockito.Mockito.times(2))
                .putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getAllValues())
                .allSatisfy(request -> assertThat(request.key()).doesNotContain("."));
    }

    @Test
    void uploadsFileWhoseNameHasNoExtension() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);
        when(mediaRepository.save(any(ChatMedia.class))).thenAnswer(invocation -> {
            ChatMedia media = invocation.getArgument(0);
            ReflectionTestUtils.setField(media, "id", 33L);
            return media;
        });

        var response = service.upload(
                10L,
                20L,
                new MockMultipartFile("file", "screenshot", "image/jpeg", new byte[] {9}));

        assertThat(response.originalFilename()).isEqualTo("screenshot");
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().key()).doesNotContain(".");
    }

    @Test
    void rejectsNullFile() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.upload(10L, 20L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MEDIA_INVALID));
        verifyNoInteractions(s3Client, mediaRepository);
    }

    @Test
    void rejectsEmptyFile() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.upload(
                        10L, 20L, new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MEDIA_INVALID));
        verifyNoInteractions(s3Client, mediaRepository);
    }

    @Test
    void rejectsFileWithoutContentType() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.upload(
                        10L, 20L, new MockMultipartFile("file", "a.png", null, new byte[] {1})))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MEDIA_INVALID));
        verifyNoInteractions(s3Client, mediaRepository);
    }

    @Test
    void rejectsImageLargerThanTwentyMegabytes() {
        ChatMediaService service = service();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(20L * 1024 * 1024 + 1);
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.upload(10L, 20L, file))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MEDIA_INVALID));
        verifyNoInteractions(s3Client, mediaRepository);
    }

    @Test
    void rejectsVideoLargerThanOneHundredMegabytes() {
        ChatMediaService service = service();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("VIDEO/MP4");
        when(file.getSize()).thenReturn(100L * 1024 * 1024 + 1);
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.upload(10L, 20L, file))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MEDIA_INVALID));
        verifyNoInteractions(s3Client, mediaRepository);
    }

    @Test
    void translatesStorageFailureIntoInternalError() {
        ChatMediaService service = service();
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkException.builder().message("s3 down").build());

        assertThatThrownBy(() -> service.upload(
                        10L,
                        20L,
                        new MockMultipartFile("file", "a.png", "image/png", new byte[] {1})))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INTERNAL_ERROR);
                            assertThat(exception.getMessage()).isEqualTo("채팅 파일을 저장하지 못했습니다.");
                        });
        verifyNoInteractions(mediaRepository);
    }

    @Test
    void translatesUnreadableUploadStreamIntoInternalError() throws Exception {
        ChatMediaService service = service();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(128L);
        when(file.getOriginalFilename()).thenReturn("broken.jpg");
        when(file.getInputStream()).thenThrow(new IOException("stream closed"));
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.upload(10L, 20L, file))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_ERROR));
        verifyNoInteractions(s3Client, mediaRepository);
    }

    @Test
    void downloadsStoredObjectForParticipant() {
        ChatMediaService service = service();
        when(mediaRepository.findById(30L)).thenReturn(Optional.of(storedMedia()));
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        new ByteArrayInputStream(new byte[] {7, 8})));

        ChatMediaService.MediaDownload download = service.download(30L, 20L);

        assertThat(download.mimeType()).isEqualTo("image/png");
        assertThat(download.filename()).isEqualTo("phone.png");
        assertThat(download.resource()).isNotNull();
        ArgumentCaptor<GetObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("media-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("chat/10/object.png");
    }

    @Test
    void downloadThrowsWhenMediaRowIsMissing() {
        ChatMediaService service = service();
        when(mediaRepository.findById(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(30L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MEDIA_NOT_FOUND));
        verifyNoInteractions(s3Client, participantRepository);
    }

    @Test
    void downloadRejectsMemberWhoLeftTheRoom() {
        ChatMediaService service = service();
        when(mediaRepository.findById(30L)).thenReturn(Optional.of(storedMedia()));
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(false);

        assertThatThrownBy(() -> service.download(30L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        verifyNoInteractions(s3Client);
    }

    @Test
    void downloadTranslatesMissingStorageObjectIntoNotFound() {
        ChatMediaService service = service();
        when(mediaRepository.findById(30L)).thenReturn(Optional.of(storedMedia()));
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(10L, 20L))
                .thenReturn(true);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkException.builder().message("no such key").build());

        assertThatThrownBy(() -> service.download(30L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_MEDIA_NOT_FOUND));
    }

    private ChatMedia storedMedia() {
        ChatMedia media = ChatMedia.verified(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                10L,
                20L,
                MediaType.IMAGE,
                "media-bucket",
                "chat/10/object.png",
                "phone.png",
                "image/png",
                3L);
        ReflectionTestUtils.setField(media, "id", 30L);
        return media;
    }

    private ChatMediaService service() {
        return new ChatMediaService(
                mediaRepository,
                participantRepository,
                s3Client,
                new S3MediaProperties(
                        "ap-northeast-2", "media-bucket", null, false,
                        Duration.ofMinutes(10), Duration.ofMinutes(5), null));
    }
}
