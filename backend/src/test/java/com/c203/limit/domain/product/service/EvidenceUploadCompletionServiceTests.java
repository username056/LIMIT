package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.EvidenceType;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * complete()의 검증 순서(완료됨→만료→권한/체크리스트 불일치), URL 결정(publicBaseUrl vs presign),
 * 완료 처리(markCompleted/markSubmitted 분기), attemptNo 계산(uploadedAt→id 정렬 후 위치)을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class EvidenceUploadCompletionServiceTests {

    private static final Long SELLER_ID = 1L;
    private static final Long PRODUCT_ID = 1001L;
    private static final Long CHECKLIST_ITEM_ID = 7002L;
    private static final String UPLOAD_ID = "upload-uuid";
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 8, 5, 12, 0);

    @Mock
    private MediaUploadSessionRepository uploadSessionRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private MediaObjectStorage storage;

    private EvidenceUploadCompletionService service(S3MediaProperties properties) {
        return new EvidenceUploadCompletionService(uploadSessionRepository, evidenceRepository, storage, properties);
    }

    private S3MediaProperties propertiesWithPublicBaseUrl(String publicBaseUrl) {
        return new S3MediaProperties(
                "ap-northeast-2", "limit-bucket", null, false, Duration.ofMinutes(10), Duration.ofMinutes(10),
                publicBaseUrl);
    }

    private MediaUploadSession completableSession(ListingChecklistItem item) {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getExpiresAt()).thenReturn(COMPLETED_AT.plusMinutes(5));
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.EVIDENCE);
        when(session.getUploaderId()).thenReturn(SELLER_ID);
        when(session.getListingId()).thenReturn(PRODUCT_ID);
        when(session.getChecklistItem()).thenReturn(item);
        when(session.getFinalObjectKey()).thenReturn("evidence/1001/7002/final.jpg");
        when(session.getExpectedMimeType()).thenReturn("image/jpeg");
        return session;
    }

    private ListingChecklistItem checklistItem(Integer minCount) {
        ListingChecklistItem item = mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(CHECKLIST_ITEM_ID);
        when(item.getEvidenceType()).thenReturn(EvidenceType.PHOTO);
        when(item.getMinCount()).thenReturn(minCount);
        return item;
    }

    private Evidence evidenceWithTiming(LocalDateTime uploadedAt, Long id) {
        Evidence evidence =
                Evidence.upload(PRODUCT_ID, mock(ListingChecklistItem.class), EvidenceType.PHOTO, "old-key",
                        "image/jpeg", null);
        ReflectionTestUtils.setField(evidence, "uploadedAt", uploadedAt);
        ReflectionTestUtils.setField(evidence, "id", id);
        return evidence;
    }

    private Evidence lastSaved;

    @Test
    void completesFirstAttemptAndMarksItemCompletedWhenMinCountReached() {
        ListingChecklistItem item = checklistItem(1);
        MediaUploadSession session = completableSession(item);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(invocation -> {
            lastSaved = invocation.getArgument(0);
            return lastSaved;
        });
        // save()가 반환한 것과 동일한 참조를 이력 조회 결과에도 그대로 흘려보내야 attemptNo 계산이
        // 그 원소를 identity로 찾아낼 수 있다(Evidence는 equals/hashCode를 오버라이드하지 않음).
        when(evidenceRepository.findAllByListingChecklistItem_Id(CHECKLIST_ITEM_ID))
                .thenAnswer(invocation -> List.of(lastSaved));

        EvidenceUploadCompletionService service = service(propertiesWithPublicBaseUrl("https://cdn.example.com"));

        EvidenceResponse response = service.complete(
                SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT);

        assertThat(response.getAttemptNo()).isEqualTo(1);
        assertThat(response.getChecklistItemId()).isEqualTo(CHECKLIST_ITEM_ID);
        assertThat(response.getEvidenceType()).isEqualTo("PHOTO");
        assertThat(response.getMediaUrl()).isEqualTo("https://cdn.example.com/evidence/1001/7002/final.jpg");
        verify(item).markCompleted();
    }

    @Test
    void marksItemSubmittedWhenHistoryCountBelowRequiredMinCount() {
        ListingChecklistItem item = checklistItem(3);
        MediaUploadSession session = completableSession(item);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(invocation -> {
            lastSaved = invocation.getArgument(0);
            return lastSaved;
        });
        when(evidenceRepository.findAllByListingChecklistItem_Id(CHECKLIST_ITEM_ID))
                .thenAnswer(invocation -> List.of(lastSaved));

        EvidenceUploadCompletionService service = service(propertiesWithPublicBaseUrl("https://cdn.example.com"));

        service.complete(SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT);

        verify(item).markSubmitted();
    }

    @Test
    void defaultsRequiredCountToOneWhenMinCountIsNull() {
        ListingChecklistItem item = checklistItem(null);
        MediaUploadSession session = completableSession(item);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(invocation -> {
            lastSaved = invocation.getArgument(0);
            return lastSaved;
        });
        when(evidenceRepository.findAllByListingChecklistItem_Id(CHECKLIST_ITEM_ID))
                .thenAnswer(invocation -> List.of(lastSaved));

        EvidenceUploadCompletionService service = service(propertiesWithPublicBaseUrl("https://cdn.example.com"));

        service.complete(SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT);

        verify(item).markCompleted();
    }

    @Test
    void computesAttemptNoBySortingHistoryByUploadedAtThenId() {
        ListingChecklistItem item = checklistItem(5);
        MediaUploadSession session = completableSession(item);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(invocation -> {
            lastSaved = invocation.getArgument(0);
            return lastSaved;
        });
        Evidence olderAttempt = evidenceWithTiming(COMPLETED_AT.minusMinutes(10), 1L);
        when(evidenceRepository.findAllByListingChecklistItem_Id(CHECKLIST_ITEM_ID))
                .thenAnswer(invocation -> List.of(olderAttempt, lastSaved));

        EvidenceUploadCompletionService service = service(propertiesWithPublicBaseUrl("https://cdn.example.com"));

        EvidenceResponse response = service.complete(
                SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT);

        // olderAttempt가 시간상 더 이른 uploadedAt을 가지므로 정렬 후 saved는 두 번째(=2회차)다.
        assertThat(response.getAttemptNo()).isEqualTo(2);
    }

    @Test
    void usesPublicBaseUrlWithoutCallingStorageWhenConfigured() {
        ListingChecklistItem item = checklistItem(1);
        MediaUploadSession session = completableSession(item);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(invocation -> {
            lastSaved = invocation.getArgument(0);
            return lastSaved;
        });
        when(evidenceRepository.findAllByListingChecklistItem_Id(CHECKLIST_ITEM_ID))
                .thenAnswer(invocation -> List.of(lastSaved));

        EvidenceUploadCompletionService service = service(propertiesWithPublicBaseUrl("https://cdn.example.com/"));

        EvidenceResponse response = service.complete(
                SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT);

        assertThat(response.getMediaUrl()).isEqualTo("https://cdn.example.com/evidence/1001/7002/final.jpg");
    }

    @Test
    void fallsBackToPresignedUrlWhenPublicBaseUrlIsBlank() throws MalformedURLException {
        ListingChecklistItem item = checklistItem(1);
        MediaUploadSession session = completableSession(item);
        when(session.getBucketName()).thenReturn("limit-bucket");
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(invocation -> {
            lastSaved = invocation.getArgument(0);
            return lastSaved;
        });
        when(evidenceRepository.findAllByListingChecklistItem_Id(CHECKLIST_ITEM_ID))
                .thenAnswer(invocation -> List.of(lastSaved));
        when(storage.presignGet(eq("limit-bucket"), eq("evidence/1001/7002/final.jpg"), any(Duration.class)))
                .thenReturn(new URL("https://presigned.example.com/evidence/final.jpg"));

        EvidenceUploadCompletionService service = service(propertiesWithPublicBaseUrl(""));

        EvidenceResponse response = service.complete(
                SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT);

        assertThat(response.getMediaUrl()).isEqualTo("https://presigned.example.com/evidence/final.jpg");
    }

    @Test
    void rejectsAlreadyCompletedSession() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.COMPLETED);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service(propertiesWithPublicBaseUrl(null))
                        .complete(SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED);
    }

    @Test
    void rejectsSessionThatIsNotPending() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.FAILED);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service(propertiesWithPublicBaseUrl(null))
                        .complete(SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEDIA_UPLOAD_EXPIRED);
    }

    @Test
    void rejectsSessionPastItsExpiryTimeEvenWhenStatusIsStillPending() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getExpiresAt()).thenReturn(COMPLETED_AT.minusMinutes(1));
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service(propertiesWithPublicBaseUrl(null))
                        .complete(SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEDIA_UPLOAD_EXPIRED);
    }

    @Test
    void rejectsWrongPurpose() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getExpiresAt()).thenReturn(COMPLETED_AT.plusMinutes(5));
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.LISTING_IMAGE);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service(propertiesWithPublicBaseUrl(null))
                        .complete(SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED);
    }

    @Test
    void rejectsWhenChecklistItemIsMissingFromTheSession() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getExpiresAt()).thenReturn(COMPLETED_AT.plusMinutes(5));
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.EVIDENCE);
        when(session.getUploaderId()).thenReturn(SELLER_ID);
        when(session.getListingId()).thenReturn(PRODUCT_ID);
        when(session.getChecklistItem()).thenReturn(null);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service(propertiesWithPublicBaseUrl(null))
                        .complete(SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED);
    }

    @Test
    void rejectsWhenSessionChecklistItemDoesNotMatchRequestedItem() {
        ListingChecklistItem otherItem = mock(ListingChecklistItem.class);
        when(otherItem.getId()).thenReturn(9999L);
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getExpiresAt()).thenReturn(COMPLETED_AT.plusMinutes(5));
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.EVIDENCE);
        when(session.getUploaderId()).thenReturn(SELLER_ID);
        when(session.getListingId()).thenReturn(PRODUCT_ID);
        when(session.getChecklistItem()).thenReturn(otherItem);
        when(uploadSessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service(propertiesWithPublicBaseUrl(null))
                        .complete(SELLER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, UPLOAD_ID, null, COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED);
    }
}
