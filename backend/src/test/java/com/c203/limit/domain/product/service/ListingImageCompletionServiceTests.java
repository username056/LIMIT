package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.dto.request.CompleteListingImageRequest;
import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingImage;
import com.c203.limit.domain.product.entity.ListingImageType;
import com.c203.limit.domain.product.entity.MediaUploadPurpose;
import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.entity.MediaUploadStatus;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * complete()의 분기 커버리지.
 *
 * <p>업로드 세션 검증 순서(이미 완료 → 소유·용도 불일치 → 매물 소유권 → 10장 제한 → 대표 이미지
 * 승계), 대표 사진 교체 시 기존 대표를 DETAIL로 내리는 상태 전이, publicBaseUrl 유무에 따른 URL
 * 생성 분기를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ListingImageCompletionServiceTests {

    private static final Long SELLER_ID = 55L;
    private static final Long PRODUCT_ID = 1001L;
    private static final String UPLOAD_ID = "upload-1";
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 8, 5, 12, 0);

    @Mock MediaUploadSessionRepository sessionRepository;
    @Mock ListingRepository listingRepository;
    @Mock ListingImageRepository imageRepository;
    @Mock MediaObjectStorage storage;

    private ListingImageCompletionService service;

    @BeforeEach
    void setUp() {
        // publicBaseUrl은 개별 테스트에서 필요할 때 별도 인스턴스로 재구성한다(record라 불변).
        S3MediaProperties properties = new S3MediaProperties(
                "ap-northeast-2", "limit-dev-media", null, false,
                Duration.ofMinutes(10), Duration.ofMinutes(5), null);
        service = new ListingImageCompletionService(
                sessionRepository, listingRepository, imageRepository, storage, properties);
    }

    @Test
    void completesFirstThumbnailAndBuildsPresignedUrlWhenPublicBaseUrlIsBlank() throws Exception {
        MediaUploadSession session = completableSession();
        when(session.getBucketName()).thenReturn("bucket-1");
        Listing listing = mock(Listing.class);
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
        when(imageRepository.countByListingId(PRODUCT_ID)).thenReturn(0L);
        when(imageRepository.findFirstByListingIdAndImageTypeOrderByIdAsc(
                        PRODUCT_ID, ListingImageType.THUMBNAIL))
                .thenReturn(Optional.empty());
        when(imageRepository.save(any(ListingImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.presignGet(eq("bucket-1"), eq("listings/1001/images/final.webp"), any()))
                .thenReturn(URI.create("https://s3.example.test/final.webp").toURL());

        ListingImageResponse response = service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT);

        assertThat(response.getImageType()).isEqualTo("THUMBNAIL");
        assertThat(response.getDisplayOrder()).isEqualTo(0);
        assertThat(response.getMimeType()).isEqualTo("image/webp");
        assertThat(response.getImageUrl()).isEqualTo("https://s3.example.test/final.webp");

        ArgumentCaptor<ListingImage> captor = ArgumentCaptor.forClass(ListingImage.class);
        verify(imageRepository).save(captor.capture());
        ListingImage saved = captor.getValue();
        assertThat(saved.getListing()).isSameAs(listing);
        assertThat(saved.getImageType()).isEqualTo(ListingImageType.THUMBNAIL);
        assertThat(saved.getDisplayOrder()).isZero();
        assertThat(saved.getS3Key()).isEqualTo("listings/1001/images/final.webp");
        assertThat(saved.getMimeType()).isEqualTo("image/webp");

        verify(session).complete(COMPLETED_AT);
        // 첫 대표 이미지라 기존 대표를 내릴 대상이 없다.
        verify(imageRepository, never()).findFirstByListingIdAndImageTypeOrderByIdAsc(
                any(), eq(ListingImageType.DETAIL));
    }

    @Test
    void usesPublicBaseUrlWithoutCallingStorageWhenConfigured() throws Exception {
        S3MediaProperties properties = new S3MediaProperties(
                "ap-northeast-2", "limit-dev-media", null, false,
                Duration.ofMinutes(10), Duration.ofMinutes(5), "https://cdn.example.test/");
        service = new ListingImageCompletionService(
                sessionRepository, listingRepository, imageRepository, storage, properties);
        MediaUploadSession session = completableSession();
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(mock(Listing.class)));
        when(imageRepository.countByListingId(PRODUCT_ID)).thenReturn(0L);
        when(imageRepository.findFirstByListingIdAndImageTypeOrderByIdAsc(
                        PRODUCT_ID, ListingImageType.THUMBNAIL))
                .thenReturn(Optional.empty());
        when(imageRepository.save(any(ListingImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ListingImageResponse response = service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT);

        // publicBaseUrl의 끝 슬래시는 제거되고 key 앞에 슬래시 하나만 붙는다.
        assertThat(response.getImageUrl())
                .isEqualTo("https://cdn.example.test/listings/1001/images/final.webp");
        verify(storage, never()).presignGet(any(), any(), any());
    }

    @Test
    void demotesExistingThumbnailToDetailWhenNewThumbnailUploaded() throws Exception {
        MediaUploadSession session = completableSession();
        when(session.getBucketName()).thenReturn("bucket-1");
        Listing listing = mock(Listing.class);
        ListingImage existingThumbnail =
                ListingImage.create(listing, ListingImageType.THUMBNAIL, 0, "old-key", null, "image/webp");
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
        when(imageRepository.countByListingId(PRODUCT_ID)).thenReturn(1L);
        when(imageRepository.findFirstByListingIdAndImageTypeOrderByIdAsc(
                        PRODUCT_ID, ListingImageType.THUMBNAIL))
                .thenReturn(Optional.of(existingThumbnail));
        when(imageRepository.save(any(ListingImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.presignGet(any(), any(), any()))
                .thenReturn(URI.create("https://s3.example.test/new.webp").toURL());

        service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT);

        assertThat(existingThumbnail.getImageType()).isEqualTo(ListingImageType.DETAIL);
    }

    @Test
    void doesNotTouchExistingThumbnailWhenUploadingDetailImage() throws Exception {
        MediaUploadSession session = completableSession();
        when(session.getBucketName()).thenReturn("bucket-1");
        Listing listing = mock(Listing.class);
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
        when(imageRepository.countByListingId(PRODUCT_ID)).thenReturn(1L);
        when(imageRepository.save(any(ListingImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.presignGet(any(), any(), any()))
                .thenReturn(URI.create("https://s3.example.test/detail.webp").toURL());

        service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.DETAIL, 1),
                COMPLETED_AT);

        verify(imageRepository, never()).findFirstByListingIdAndImageTypeOrderByIdAsc(any(), any());
    }

    @Test
    void rejectsWhenUploadSessionIsMissing() {
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEDIA_UPLOAD_NOT_FOUND);
        verify(imageRepository, never()).save(any());
    }

    @Test
    void rejectsAlreadyCompletedSessionBeforeCheckingOwnership() {
        // 이미 COMPLETED인 세션은 소유자·매물이 어긋나 있어도 ALREADY_COMPLETED로 먼저 걸려야 한다
        // (검증 순서 자체가 계약이라 순서를 바꾸는 리팩터링이 조용히 의미를 바꾸지 않게 고정한다).
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.COMPLETED);
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED);
        verify(listingRepository, never()).findByIdAndSellerIdAndDeletedAtIsNull(any(), any());
    }

    @Test
    void rejectsWhenSessionPurposeIsNotListingImage() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.EVIDENCE);
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED);
    }

    @Test
    void rejectsWhenUploaderDoesNotMatchSeller() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.LISTING_IMAGE);
        when(session.getUploaderId()).thenReturn(999L);
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED);
    }

    @Test
    void rejectsWhenSessionListingDoesNotMatchRequestedProduct() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.LISTING_IMAGE);
        when(session.getUploaderId()).thenReturn(SELLER_ID);
        when(session.getListingId()).thenReturn(9999L);
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED);
    }

    @Test
    void rejectsWhenListingIsNotOwnedBySeller() {
        MediaUploadSession session = pendingSession();
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.THUMBNAIL, 0),
                COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED);
        verify(imageRepository, never()).countByListingId(any());
    }

    @Test
    void rejectsWhenListingAlreadyHasTenImages() {
        MediaUploadSession session = pendingSession();
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(mock(Listing.class)));
        when(imageRepository.countByListingId(PRODUCT_ID)).thenReturn(10L);

        assertThatThrownBy(() -> service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.DETAIL, 10),
                COMPLETED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.LISTING_IMAGE_LIMIT_EXCEEDED);
        verify(imageRepository, never()).save(any());
    }

    @Test
    void allowsCompletionWhenListingHasExactlyNineImages() throws Exception {
        // countByListingId의 상한 경계값(9는 허용, 10부터 차단)을 확인한다.
        MediaUploadSession session = completableSession();
        when(session.getBucketName()).thenReturn("bucket-1");
        Listing listing = mock(Listing.class);
        when(sessionRepository.findByIdForUpdate(UPLOAD_ID)).thenReturn(Optional.of(session));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
        when(imageRepository.countByListingId(PRODUCT_ID)).thenReturn(9L);
        when(imageRepository.save(any(ListingImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.presignGet(any(), any(), any()))
                .thenReturn(URI.create("https://s3.example.test/tenth.webp").toURL());

        ListingImageResponse response = service.complete(
                SELLER_ID,
                PRODUCT_ID,
                new CompleteListingImageRequest(UPLOAD_ID, ListingImageType.DETAIL, 9),
                COMPLETED_AT);

        assertThat(response.getDisplayOrder()).isEqualTo(9);
        verify(imageRepository).save(any(ListingImage.class));
    }

    // 검증 단계(상태·소유·매물 일치)만 통과시키는 최소 스텁이다. 이 단계에서 예외로 끝나는
    // 테스트에 s3Key·mimeType까지 스텁하면 실제로 안 쓰여 Mockito strict-stub 오류가 난다.
    private MediaUploadSession pendingSession() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.LISTING_IMAGE);
        when(session.getUploaderId()).thenReturn(SELLER_ID);
        when(session.getListingId()).thenReturn(PRODUCT_ID);
        return session;
    }

    // ListingImage.create()까지 도달하는 테스트용. bucketName은 storage.presignGet 분기에서만
    // 쓰이므로 필요한 테스트에서 별도로 스텁한다.
    private MediaUploadSession completableSession() {
        MediaUploadSession session = pendingSession();
        when(session.getFinalObjectKey()).thenReturn("listings/1001/images/final.webp");
        when(session.getExpectedMimeType()).thenReturn("image/webp");
        return session;
    }
}
