package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.dto.request.CompleteListingImageRequest;
import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import com.c203.limit.domain.product.entity.*;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingImageCompletionService {

    private static final Logger log = LoggerFactory.getLogger(ListingImageCompletionService.class);

    private final MediaUploadSessionRepository sessionRepository;
    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final MediaObjectStorage storage;
    private final S3MediaProperties properties;

    public ListingImageCompletionService(
            MediaUploadSessionRepository sessionRepository,
            ListingRepository listingRepository,
            ListingImageRepository imageRepository,
            MediaObjectStorage storage,
            S3MediaProperties properties) {
        this.sessionRepository = sessionRepository;
        this.listingRepository = listingRepository;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional
    public ListingImageResponse complete(
            Long sellerId,
            Long productId,
            CompleteListingImageRequest request,
            LocalDateTime completedAt) {
        MediaUploadSession session = sessionRepository
                .findByIdForUpdate(request.getUploadId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_UPLOAD_NOT_FOUND));
        if (session.getStatus() == MediaUploadStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED);
        }
        if (session.getPurpose() != MediaUploadPurpose.LISTING_IMAGE
                || !session.getUploaderId().equals(sellerId)
                || !session.getListingId().equals(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        Listing listing = listingRepository
                .findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED));
        if (imageRepository.countByListingId(productId) >= 10) {
            throw new BusinessException(ErrorCode.LISTING_IMAGE_LIMIT_EXCEEDED);
        }
        if (request.getImageType() == ListingImageType.THUMBNAIL) {
            imageRepository
                    .findFirstByListingIdAndImageTypeOrderByIdAsc(
                            productId, ListingImageType.THUMBNAIL)
                    .ifPresent(image -> image.changeType(ListingImageType.DETAIL));
        }
        ListingImage saved = imageRepository.save(ListingImage.create(
                listing,
                request.getImageType(),
                request.getDisplayOrder(),
                session.getFinalObjectKey(),
                null,
                session.getExpectedMimeType()));
        session.complete(completedAt);
        log.info(
                "Listing image upload completed: productId={}, imageId={}, imageType={}, displayOrder={}",
                productId,
                saved.getId(),
                saved.getImageType(),
                saved.getDisplayOrder());
        String url = properties.publicBaseUrl() != null
                        && !properties.publicBaseUrl().isBlank()
                ? properties.publicBaseUrl().replaceAll("/$", "")
                        + "/"
                        + saved.getS3Key()
                : storage
                        .presignGet(
                                session.getBucketName(),
                                saved.getS3Key(),
                                properties.downloadTtl())
                        .toString();
        return ListingImageResponse.of(saved, url);
    }
}
