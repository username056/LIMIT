package com.c203.limit.domain.chat.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.c203.limit.domain.chat.domain.MediaType;
import com.c203.limit.domain.chat.dto.response.ChatMediaResponse;
import com.c203.limit.domain.chat.entity.ChatMedia;
import com.c203.limit.domain.chat.repository.ChatMediaRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;

@Service
public class ChatMediaService {
    private static final Logger log = LoggerFactory.getLogger(ChatMediaService.class);
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_TYPES =
            Set.of("video/mp4", "video/webm", "video/quicktime");

    private final ChatMediaRepository mediaRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final Path storageRoot;

    public ChatMediaService(
            ChatMediaRepository mediaRepository,
            ChatRoomParticipantRepository participantRepository,
            @Value("${limit.chat.media-storage-dir:./build/chat-media}") String storageDirectory) {
        this.mediaRepository = mediaRepository;
        this.participantRepository = participantRepository;
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public ChatMediaResponse upload(Long roomId, Long memberId, MultipartFile file) {
        requireParticipant(roomId, memberId);
        MediaType type = validate(file);
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extension(originalFilename);
        String objectKey = UUID.randomUUID() + extension;
        Path target = storageRoot.resolve(objectKey).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(ErrorCode.CHAT_MEDIA_INVALID);
        }
        try {
            Files.createDirectories(storageRoot);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            ChatMedia media = mediaRepository.save(ChatMedia.verified(
                    UUID.randomUUID(), roomId, memberId, type, objectKey, originalFilename,
                    file.getContentType(), file.getSize()));
            log.info(
                    "chat media stored: mediaId={}, type={}, sizeBytes={}",
                    media.getId(),
                    media.getType(),
                    media.getFileSizeBytes());
            return ChatMediaResponse.from(media);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "채팅 파일을 저장하지 못했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public MediaDownload download(Long mediaId, Long memberId) {
        ChatMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MEDIA_NOT_FOUND));
        requireParticipant(media.getChatRoomId(), memberId);
        try {
            Path target = storageRoot.resolve(media.getObjectKey()).normalize();
            if (!target.startsWith(storageRoot) || !Files.isRegularFile(target)) {
                throw new BusinessException(ErrorCode.CHAT_MEDIA_NOT_FOUND);
            }
            return new MediaDownload(new UrlResource(target.toUri()), media.getMimeType(), media.getOriginalFilename());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.CHAT_MEDIA_NOT_FOUND);
        }
    }

    private MediaType validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getContentType() == null) {
            throw new BusinessException(ErrorCode.CHAT_MEDIA_INVALID);
        }
        String mimeType = file.getContentType().toLowerCase(Locale.ROOT);
        if (IMAGE_TYPES.contains(mimeType) && file.getSize() <= MAX_IMAGE_BYTES) {
            return MediaType.IMAGE;
        }
        if (VIDEO_TYPES.contains(mimeType) && file.getSize() <= MAX_VIDEO_BYTES) {
            return MediaType.VIDEO;
        }
        throw new BusinessException(ErrorCode.CHAT_MEDIA_INVALID);
    }

    private void requireParticipant(Long roomId, Long memberId) {
        if (!participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, memberId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        return Path.of(filename).getFileName().toString();
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index).toLowerCase(Locale.ROOT);
    }

    public record MediaDownload(Resource resource, String mimeType, String filename) {}
}
