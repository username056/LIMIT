package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.dto.request.CompleteListingImageRequest;
import com.c203.limit.domain.product.dto.request.CreateListingImageUploadUrlRequest;
import com.c203.limit.domain.product.dto.request.UpdateListingImageOrderRequest;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingImage;
import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.entity.MediaUploadStatus;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class ListingImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ListingImageUploadService.class);
    private static final Set<String> IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 15L * 1024L * 1024L;

    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final MediaUploadSessionRepository uploadSessionRepository;
    private final MediaObjectStorage storage;
    private final S3MediaProperties properties;
    private final ListingImageCompletionService completionService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ListingImageUploadService(
            ListingRepository listingRepository,
            ListingImageRepository imageRepository,
            MediaUploadSessionRepository uploadSessionRepository,
            MediaObjectStorage storage,
            S3MediaProperties properties,
            ListingImageCompletionService completionService,
            ApplicationEventPublisher events) {
        this.listingRepository = listingRepository;
        this.imageRepository = imageRepository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.storage = storage;
        this.properties = properties;
        this.completionService = completionService;
        this.events = events;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public EvidenceUploadUrlResponse createUploadUrl(
            Long sellerId, Long productId, CreateListingImageUploadUrlRequest request) {
        requireConfigured();
        Listing listing = ownedListing(sellerId, productId);
        if (imageRepository.countByListingId(productId) >= 10) {
            throw new BusinessException(ErrorCode.LISTING_IMAGE_LIMIT_EXCEEDED);
        }
        String contentType = normalized(request.getContentType());
        if (!IMAGE_TYPES.contains(contentType) || request.getFileSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }

        String uploadId = UUID.randomUUID().toString();
        String extension = extension(request.getFilename());
        String temporaryKey = "tmp/" + sellerId + "/" + uploadId + extension;
        String finalKey =
                "listings/" + listing.getId() + "/images/" + UUID.randomUUID() + extension;
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(properties.uploadTtl());
        MediaUploadSession session = MediaUploadSession.listingImage(
                uploadId,
                sellerId,
                productId,
                properties.bucket(),
                temporaryKey,
                finalKey,
                request.getFilename(),
                contentType,
                request.getFileSize(),
                expiresAt,
                now);
        uploadSessionRepository.save(session);
        var url = storage.presignPut(
                properties.bucket(),
                temporaryKey,
                contentType,
                request.getFileSize(),
                properties.uploadTtl());
        return new EvidenceUploadUrlResponse(
                uploadId,
                temporaryKey,
                url.toString(),
                expiresAt.atOffset(ZoneOffset.UTC),
                Map.of("Content-Type", contentType));
    }

    public ListingImageResponse complete(
            Long sellerId, Long productId, CompleteListingImageRequest request) {
        MediaUploadSession session = uploadSessionRepository
                .findById(request.getUploadId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_UPLOAD_NOT_FOUND));
        validateSession(session, sellerId, productId);
        if (session.getStatus() == MediaUploadStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED);
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_EXPIRED);
        }
        try {
            var object = storage.head(session.getBucketName(), session.getObjectKey());
            if (object.contentLength() != session.getExpectedFileSize()
                    || !normalized(object.contentType()).equals(session.getExpectedMimeType())) {
                throw new BusinessException(ErrorCode.MEDIA_UPLOAD_MISMATCH);
            }
            storage.promote(
                    session.getBucketName(),
                    session.getObjectKey(),
                    session.getFinalObjectKey(),
                    session.getExpectedMimeType());
        } catch (S3Exception exception) {
            log.warn(
                    "listing image storage request failed: uploadId={}, status={}",
                    session.getUploadId(),
                    exception.statusCode());
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        }
        return completionService.complete(
                sellerId,
                productId,
                request,
                LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<ListingImageResponse> findAll(Long productId) {
        listingRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(productId).stream()
                .map(image -> ListingImageResponse.of(image, url(image.getS3Key())))
                .toList();
    }

    @Transactional
    public void delete(Long sellerId, Long productId, Long imageId) {
        ownedListing(sellerId, productId);
        ListingImage image = imageRepository
                .findByIdAndListingId(imageId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_IMAGE_NOT_FOUND));
        boolean wasThumbnail =
                image.getImageType()
                        == com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL;
        imageRepository.delete(image);
        imageRepository.flush();
        if (wasThumbnail) {
            imageRepository
                    .findAllByListingIdOrderByDisplayOrderAscIdAsc(productId)
                    .stream()
                    .findFirst()
                    .ifPresent(candidate -> candidate.changeType(
                            com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL));
        }
        events.publishEvent(new ListingImageDeletedEvent(properties.bucket(), image.getS3Key()));
    }

    @Transactional
    public List<ListingImageResponse> updateOrder(
            Long sellerId, Long productId, UpdateListingImageOrderRequest request) {
        ownedListing(sellerId, productId);
        List<ListingImage> images =
                imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(productId);
        List<Long> requestedIds = request.getImageIds();
        Set<Long> currentIds = images.stream().map(ListingImage::getId).collect(java.util.stream.Collectors.toSet());
        if (requestedIds.size() != images.size()
                || new HashSet<>(requestedIds).size() != requestedIds.size()
                || !currentIds.equals(new HashSet<>(requestedIds))
                || !currentIds.contains(request.getThumbnailImageId())) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }
        Map<Long, ListingImage> byId = images.stream()
                .collect(java.util.stream.Collectors.toMap(ListingImage::getId, image -> image));
        for (int index = 0; index < requestedIds.size(); index++) {
            ListingImage image = byId.get(requestedIds.get(index));
            image.changeOrder(index);
            image.changeType(image.getId().equals(request.getThumbnailImageId())
                    ? com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL
                    : com.c203.limit.domain.product.entity.ListingImageType.DETAIL);
        }
        return requestedIds.stream()
                .map(byId::get)
                .map(image -> ListingImageResponse.of(image, url(image.getS3Key())))
                .toList();
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

    private void validateSession(MediaUploadSession session, Long sellerId, Long productId) {
        if (session.getPurpose()
                        != com.c203.limit.domain.product.entity.MediaUploadPurpose.LISTING_IMAGE
                || !session.getUploaderId().equals(sellerId)
                || !session.getListingId().equals(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
    }

    private String url(String key) {
        if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
            return properties.publicBaseUrl().replaceAll("/$", "") + "/" + key;
        }
        return storage.presignGet(properties.bucket(), key, properties.downloadTtl()).toString();
    }

    private void requireConfigured() {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
        }
    }

    private String normalized(String value) {
        return value == null
                ? ""
                : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String extension = filename.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : "";
    }
}
