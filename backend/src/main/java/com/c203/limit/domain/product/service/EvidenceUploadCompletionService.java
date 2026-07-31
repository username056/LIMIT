package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.entity.MediaUploadPurpose;
import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.entity.MediaUploadStatus;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceUploadCompletionService {

    private static final Logger log =
            LoggerFactory.getLogger(EvidenceUploadCompletionService.class);

    private final MediaUploadSessionRepository uploadSessionRepository;
    private final EvidenceRepository evidenceRepository;
    private final MediaObjectStorage storage;
    private final S3MediaProperties properties;

    public EvidenceUploadCompletionService(
            MediaUploadSessionRepository uploadSessionRepository,
            EvidenceRepository evidenceRepository,
            MediaObjectStorage storage,
            S3MediaProperties properties) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.evidenceRepository = evidenceRepository;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional
    public EvidenceResponse complete(
            Long sellerId,
            Long productId,
            Long checklistItemId,
            String uploadId,
            OffsetDateTime capturedAt,
            LocalDateTime completedAt) {
        MediaUploadSession session = uploadSessionRepository
                .findByIdForUpdate(uploadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_UPLOAD_NOT_FOUND));
        validate(session, sellerId, productId, checklistItemId, completedAt);

        ListingChecklistItem item = session.getChecklistItem();
        String mediaUrl = publicOrPresignedUrl(session);
        Evidence evidence = Evidence.upload(
                productId,
                item,
                item.getEvidenceType(),
                session.getFinalObjectKey(),
                session.getExpectedMimeType(),
                capturedAt == null ? null : capturedAt.toLocalDateTime());
        evidence.markReady(mediaUrl);
        Evidence saved = evidenceRepository.save(evidence);
        session.complete(completedAt);

        List<Evidence> history =
                evidenceRepository.findAllByListingChecklistItem_Id(checklistItemId);
        int requiredCount = item.getMinCount() == null ? 1 : item.getMinCount();
        if (history.size() >= requiredCount) {
            item.markCompleted();
        } else {
            item.markSubmitted();
        }

        int attemptNo = history.stream()
                .sorted(Comparator.comparing(Evidence::getUploadedAt).thenComparing(Evidence::getId))
                .toList()
                .indexOf(saved)
                + 1;
        log.info(
                "evidence upload completed: uploadId={}, evidenceId={}, checklistItemId={}",
                uploadId,
                saved.getId(),
                checklistItemId);
        return new EvidenceResponse(
                saved.getId(),
                checklistItemId,
                saved.getEvidenceType().name(),
                attemptNo,
                true,
                mediaUrl,
                saved.getProcessingStatus().name(),
                "NONE",
                capturedAt,
                saved.getUploadedAt().atOffset(ZoneOffset.UTC));
    }

    private void validate(
            MediaUploadSession session,
            Long sellerId,
            Long productId,
            Long checklistItemId,
            LocalDateTime completedAt) {
        if (session.getStatus() == MediaUploadStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED);
        }
        if (session.getStatus() != MediaUploadStatus.PENDING
                || session.getExpiresAt().isBefore(completedAt)) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_EXPIRED);
        }
        if (session.getPurpose() != MediaUploadPurpose.EVIDENCE
                || !session.getUploaderId().equals(sellerId)
                || !session.getListingId().equals(productId)
                || session.getChecklistItem() == null
                || !session.getChecklistItem().getId().equals(checklistItemId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
    }

    private String publicOrPresignedUrl(MediaUploadSession session) {
        if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
            return properties.publicBaseUrl().replaceAll("/$", "")
                    + "/"
                    + session.getFinalObjectKey();
        }
        return storage
                .presignGet(
                        session.getBucketName(),
                        session.getFinalObjectKey(),
                        properties.downloadTtl())
                .toString();
    }
}
