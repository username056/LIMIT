package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.product.dto.request.CompleteEvidenceRequest;
import com.c203.limit.domain.product.dto.request.CreateEvidenceUploadUrlRequest;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class EvidenceUploadService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceUploadService.class);
    private static final Set<String> PHOTO_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_TYPES =
            Set.of("video/mp4", "video/quicktime", "video/webm");
    // 진단 자료는 파일로 저장되는 형식(txt·xml·html)뿐 아니라 사진으로도 올라온다.
    // 진단 앱이 결과를 파일로 내보내지 못하면 판매자가 화면을 찍어 올리는 수밖에 없어서,
    // 사진을 막으면 그 항목을 채울 방법이 사라진다.
    private static final Set<String> DIAGNOSTIC_TYPES = Set.of(
            "text/plain",
            "text/xml",
            "application/xml",
            "text/html",
            "application/octet-stream",
            "image/jpeg",
            "image/png",
            "image/webp");

    private final ListingRepository listingRepository;
    private final ListingChecklistItemRepository checklistItemRepository;
    private final MediaUploadSessionRepository uploadSessionRepository;
    private final EvidenceRepository evidenceRepository;
    private final MediaObjectStorage storage;
    private final S3MediaProperties properties;
    private final EvidenceUploadCompletionService completionService;
    private final VideoDurationVerifier videoDurationVerifier;
    private final Clock clock;

    @Autowired
    public EvidenceUploadService(
            ListingRepository listingRepository,
            ListingChecklistItemRepository checklistItemRepository,
            MediaUploadSessionRepository uploadSessionRepository,
            EvidenceRepository evidenceRepository,
            MediaObjectStorage storage,
            S3MediaProperties properties,
            EvidenceUploadCompletionService completionService,
            VideoDurationVerifier videoDurationVerifier) {
        this(
                listingRepository,
                checklistItemRepository,
                uploadSessionRepository,
                evidenceRepository,
                storage,
                properties,
                completionService,
                videoDurationVerifier,
                Clock.systemUTC());
    }

    EvidenceUploadService(
            ListingRepository listingRepository,
            ListingChecklistItemRepository checklistItemRepository,
            MediaUploadSessionRepository uploadSessionRepository,
            EvidenceRepository evidenceRepository,
            MediaObjectStorage storage,
            S3MediaProperties properties,
            EvidenceUploadCompletionService completionService,
            VideoDurationVerifier videoDurationVerifier,
            Clock clock) {
        this.listingRepository = listingRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.evidenceRepository = evidenceRepository;
        this.storage = storage;
        this.properties = properties;
        this.completionService = completionService;
        this.videoDurationVerifier = videoDurationVerifier;
        this.clock = clock;
    }

    @Transactional
    public EvidenceUploadUrlResponse createUploadUrl(
            Long sellerId,
            Long productId,
            Long checklistItemId,
            CreateEvidenceUploadUrlRequest request) {
        requireConfigured();
        Listing listing = ownedListing(sellerId, productId);
        ListingChecklistItem item = checklistItemRepository
                .findByIdAndListingId(checklistItemId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (item.getMaxCount() != null
                && item.getMaxCount() > 0
                && evidenceRepository.countByListingChecklistItem_Id(checklistItemId)
                        >= item.getMaxCount()) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }
        validate(item, request);

        String uploadId = UUID.randomUUID().toString();
        String extension = extension(request.getFilename());
        String temporaryKey = "tmp/" + sellerId + "/" + uploadId + extension;
        String finalKey = "evidence/" + listing.getId() + "/" + checklistItemId + "/"
                + UUID.randomUUID() + extension;
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(properties.uploadTtl());

        MediaUploadSession session = MediaUploadSession.evidence(
                uploadId,
                sellerId,
                listing.getId(),
                item,
                properties.bucket(),
                temporaryKey,
                finalKey,
                request.getFilename(),
                normalizedContentType(request.getContentType()),
                request.getFileSize(),
                request.getDurationSeconds(),
                expiresAt,
                now);
        uploadSessionRepository.save(session);

        var url = storage.presignPut(
                properties.bucket(),
                temporaryKey,
                session.getExpectedMimeType(),
                session.getExpectedFileSize(),
                properties.uploadTtl());
        return new EvidenceUploadUrlResponse(
                uploadId,
                temporaryKey,
                url.toString(),
                expiresAt.atOffset(ZoneOffset.UTC),
                Map.of("Content-Type", session.getExpectedMimeType()));
    }

    public EvidenceResponse complete(
            Long sellerId,
            Long productId,
            Long checklistItemId,
            CompleteEvidenceRequest request) {
        MediaUploadSession session = uploadSessionRepository
                .findById(request.getUploadId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_UPLOAD_NOT_FOUND));
        validateSessionOwner(session, sellerId, productId, checklistItemId);
        if (session.getStatus()
                == com.c203.limit.domain.product.entity.MediaUploadStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (session.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_EXPIRED);
        }

        MediaObjectStorage.StoredObject object;
        try {
            object = storage.head(session.getBucketName(), session.getObjectKey());
        } catch (S3Exception exception) {
            log.warn("media upload head failed: uploadId={}, status={}", session.getUploadId(), exception.statusCode());
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        }
        if (object.contentLength() != session.getExpectedFileSize()
                || !normalizedContentType(object.contentType())
                        .equals(session.getExpectedMimeType())) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_MISMATCH);
        }
        videoDurationVerifier.verify(session);

        try {
            storage.promote(
                    session.getBucketName(),
                    session.getObjectKey(),
                    session.getFinalObjectKey(),
                    session.getExpectedMimeType());
        } catch (S3Exception exception) {
            log.warn("media upload promotion failed: uploadId={}, status={}", session.getUploadId(), exception.statusCode());
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        }

        return completionService.complete(
                sellerId,
                productId,
                checklistItemId,
                request.getUploadId(),
                request.getCapturedAt(),
                now);
    }

    private Listing ownedListing(Long sellerId, Long productId) {
        Listing listing = listingRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        return listing;
    }

    private void validate(
            ListingChecklistItem item, CreateEvidenceUploadUrlRequest request) {
        String contentType = normalizedContentType(request.getContentType());
        boolean typeAllowed = switch (item.getEvidenceType()) {
            case PHOTO -> PHOTO_TYPES.contains(contentType);
            case VIDEO -> VIDEO_TYPES.contains(contentType);
            case DIAGNOSTIC_FILE -> DIAGNOSTIC_TYPES.contains(contentType);
            default -> false;
        };
        if (!typeAllowed) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }
        if (item.getMaxFileSizeMb() != null
                && request.getFileSize() > item.getMaxFileSizeMb() * 1024L * 1024L) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }
        if (item.getEvidenceType() == EvidenceType.VIDEO) {
            Integer duration = request.getDurationSeconds();
            if (duration == null
                    || (item.getMinDurationSec() != null && duration < item.getMinDurationSec())
                    || (item.getMaxDurationSec() != null && duration > item.getMaxDurationSec())) {
                throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
            }
        } else if (request.getDurationSeconds() != null) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }
    }

    private void validateSessionOwner(
            MediaUploadSession session,
            Long sellerId,
            Long productId,
            Long checklistItemId) {
        if (!session.getUploaderId().equals(sellerId)
                || !session.getListingId().equals(productId)
                || session.getChecklistItem() == null
                || !session.getChecklistItem().getId().equals(checklistItemId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
    }

    private void requireConfigured() {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
        }
    }

    private String normalizedContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : "";
    }
}
