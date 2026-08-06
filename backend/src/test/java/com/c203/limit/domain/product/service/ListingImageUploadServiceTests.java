package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.dto.request.CompleteListingImageRequest;
import com.c203.limit.domain.product.dto.request.CreateListingImageUploadUrlRequest;
import com.c203.limit.domain.product.dto.request.UpdateListingImageOrderRequest;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class ListingImageUploadServiceTests {
    @Mock ListingRepository listingRepository;
    @Mock ListingImageRepository imageRepository;
    @Mock MediaUploadSessionRepository uploadSessionRepository;
    @Mock MediaObjectStorage storage;
    @Mock ListingImageCompletionService completionService;
    @Mock ApplicationEventPublisher events;
    ListingImageUploadService service;

    @BeforeEach
    void setUp() {
        service =
                new ListingImageUploadService(
                        listingRepository,
                        imageRepository,
                        uploadSessionRepository,
                        storage,
                        new S3MediaProperties(
                                "ap-northeast-2",
                                "limit-dev-media",
                                null,
                                false,
                                Duration.ofMinutes(10),
                                Duration.ofMinutes(5),
                                ""),
                        completionService,
                        events);
    }

    @Test
    void createsPendingImageUploadSessionAndPresignedUrl() throws Exception {
        Listing listing = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing));
        when(storage.presignPut(
                        org.mockito.ArgumentMatchers.eq("limit-dev-media"),
                        org.mockito.ArgumentMatchers.startsWith("tmp/55/"),
                        org.mockito.ArgumentMatchers.eq("image/webp"),
                        org.mockito.ArgumentMatchers.eq(5_000L),
                        org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10))))
                .thenReturn(URI.create("https://s3.example.test/image-upload").toURL());

        var response =
                service.createUploadUrl(
                        55L,
                        1001L,
                        new CreateListingImageUploadUrlRequest(
                                "thumbnail.webp", "image/webp", 5_000L));

        assertThat(response.getPresignedUrl())
                .isEqualTo("https://s3.example.test/image-upload");
        assertThat(response.getStorageKey()).startsWith("tmp/55/");
        var session = ArgumentCaptor.forClass(MediaUploadSession.class);
        verify(uploadSessionRepository).save(session.capture());
        assertThat(session.getValue().getPurpose()).isEqualTo(MediaUploadPurpose.LISTING_IMAGE);
        assertThat(session.getValue().getFinalObjectKey())
                .startsWith("listings/1001/images/")
                .endsWith(".webp");
    }

    @Test
    void rejectsUploadUrlWhenBucketIsNotConfigured() {
        ListingImageUploadService unconfigured = serviceWith(properties("", ""));

        assertThatThrownBy(() -> unconfigured.createUploadUrl(
                        55L, 1001L, imageRequest("thumbnail.webp", "image/webp", 5_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_STORAGE_NOT_CONFIGURED));
        verify(listingRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void rejectsUploadUrlWhenListingIsMissing() {
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L, 1001L, imageRequest("thumbnail.webp", "image/webp", 5_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void rejectsUploadUrlWhenListingBelongsToAnotherSeller() {
        Listing owned = listingOwnedBy(99L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L, 1001L, imageRequest("thumbnail.webp", "image/webp", 5_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsUploadUrlWhenImageCountReachedLimit() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        when(imageRepository.countByListingId(1001L)).thenReturn(10L);

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L, 1001L, imageRequest("thumbnail.webp", "image/webp", 5_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_IMAGE_LIMIT_EXCEEDED));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsUploadUrlForUnsupportedMimeType() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        when(imageRepository.countByListingId(1001L)).thenReturn(0L);

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L, 1001L, imageRequest("clip.gif", "image/gif", 5_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsUploadUrlWhenFileExceedsMaximumSize() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        when(imageRepository.countByListingId(1001L)).thenReturn(0L);

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        imageRequest("huge.png", "image/png", 15L * 1024L * 1024L + 1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void normalizesMimeTypeParametersAndOmitsMissingFilenameExtension() throws Exception {
        Listing listing = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing));
        when(storage.presignPut(
                        eq("limit-dev-media"),
                        startsWith("tmp/55/"),
                        eq("image/jpeg"),
                        eq(5_000L),
                        eq(Duration.ofMinutes(10))))
                .thenReturn(URI.create("https://s3.example.test/image-upload").toURL());

        var response =
                service.createUploadUrl(
                        55L,
                        1001L,
                        imageRequest("photo", "image/jpeg; charset=binary", 5_000L));

        assertThat(response.getRequiredHeaders()).containsEntry("Content-Type", "image/jpeg");
        assertThat(response.getStorageKey()).doesNotContain(".");
        var session = ArgumentCaptor.forClass(MediaUploadSession.class);
        verify(uploadSessionRepository).save(session.capture());
        assertThat(session.getValue().getExpectedMimeType()).isEqualTo("image/jpeg");
        assertThat(session.getValue().getFinalObjectKey())
                .startsWith("listings/1001/images/")
                .doesNotContain(".");
    }

    @Test
    void promotesUploadedImageBeforeCompletingDatabaseRecord() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.LISTING_IMAGE);
        when(session.getUploaderId()).thenReturn(55L);
        when(session.getListingId()).thenReturn(1001L);
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getExpiresAt()).thenReturn(LocalDateTime.now().plusMinutes(10));
        when(session.getBucketName()).thenReturn("limit-dev-media");
        when(session.getObjectKey()).thenReturn("tmp/55/upload-1.webp");
        when(session.getFinalObjectKey()).thenReturn("listings/1001/images/image-1.webp");
        when(session.getExpectedFileSize()).thenReturn(5_000L);
        when(session.getExpectedMimeType()).thenReturn("image/webp");
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));
        when(storage.head("limit-dev-media", "tmp/55/upload-1.webp"))
                .thenReturn(new MediaObjectStorage.StoredObject(5_000L, "image/webp"));
        ListingImageResponse completed = mock(ListingImageResponse.class);
        when(completionService.complete(
                        org.mockito.ArgumentMatchers.eq(55L),
                        org.mockito.ArgumentMatchers.eq(1001L),
                        any(CompleteListingImageRequest.class),
                        any(LocalDateTime.class)))
                .thenReturn(completed);

        var result =
                service.complete(
                        55L,
                        1001L,
                        new CompleteListingImageRequest(
                                "upload-1", ListingImageType.THUMBNAIL, 0));

        assertThat(result).isSameAs(completed);
        verify(storage)
                .promote(
                        "limit-dev-media",
                        "tmp/55/upload-1.webp",
                        "listings/1001/images/image-1.webp",
                        "image/webp");
        verify(completionService)
                .complete(
                        org.mockito.ArgumentMatchers.eq(55L),
                        org.mockito.ArgumentMatchers.eq(1001L),
                        any(CompleteListingImageRequest.class),
                        any(LocalDateTime.class));
    }

    @Test
    void rejectsCompletionWhenUploadSessionIsMissing() {
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_NOT_FOUND));
    }

    @Test
    void rejectsCompletionWhenSessionWasCreatedForAnotherPurpose() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.EVIDENCE);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
        verify(storage, never()).head(any(), any());
    }

    @Test
    void rejectsCompletionWhenSessionBelongsToAnotherUploader() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.LISTING_IMAGE);
        when(session.getUploaderId()).thenReturn(56L);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
    }

    @Test
    void rejectsCompletionWhenSessionBelongsToAnotherListing() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.LISTING_IMAGE);
        when(session.getUploaderId()).thenReturn(55L);
        when(session.getListingId()).thenReturn(2002L);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
    }

    @Test
    void rejectsCompletionWhenSessionAlreadyCompleted() {
        MediaUploadSession session = ownedSession();
        when(session.getStatus()).thenReturn(MediaUploadStatus.COMPLETED);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED));
        verify(storage, never()).head(any(), any());
    }

    @Test
    void rejectsCompletionWhenSessionAlreadyExpired() {
        MediaUploadSession session = ownedSession();
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getExpiresAt()).thenReturn(LocalDateTime.of(2020, 1, 1, 0, 0));
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_EXPIRED));
        verify(storage, never()).head(any(), any());
    }

    @Test
    void rejectsCompletionWhenStoredObjectSizeDiffers() {
        MediaUploadSession session = pendingSession();
        when(session.getExpectedFileSize()).thenReturn(5_000L);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));
        when(storage.head("limit-dev-media", "tmp/55/upload-1.webp"))
                .thenReturn(new MediaObjectStorage.StoredObject(4_000L, "image/webp"));

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_MISMATCH));
        verify(storage, never()).promote(any(), any(), any(), any());
    }

    @Test
    void rejectsCompletionWhenStoredObjectMimeTypeDiffers() {
        MediaUploadSession session = pendingSession();
        when(session.getExpectedFileSize()).thenReturn(5_000L);
        when(session.getExpectedMimeType()).thenReturn("image/webp");
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));
        when(storage.head("limit-dev-media", "tmp/55/upload-1.webp"))
                .thenReturn(new MediaObjectStorage.StoredObject(5_000L, "image/png"));

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_MISMATCH));
        verify(completionService, never())
                .complete(any(), any(), any(CompleteListingImageRequest.class), any());
    }

    @Test
    void mapsStorageFailureToUnavailableError() {
        MediaUploadSession session = pendingSession();
        when(session.getUploadId()).thenReturn("upload-1");
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));
        when(storage.head("limit-dev-media", "tmp/55/upload-1.webp"))
                .thenThrow(S3Exception.builder().statusCode(503).message("unavailable").build());

        assertThatThrownBy(() -> service.complete(55L, 1001L, completeRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE));
        verify(completionService, never())
                .complete(any(), any(), any(CompleteListingImageRequest.class), any());
    }

    @Test
    void rejectsImageListingWhenProductIsMissing() {
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAll(1001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void buildsImageUrlsFromPublicBaseUrlWhenConfigured() {
        ListingImageUploadService withCdn =
                serviceWith(properties("limit-dev-media", "https://cdn.example.com/"));
        Listing listing = mock(Listing.class);
        when(listing.isPubliclyVisible()).thenReturn(true);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing));
        ListingImage stored = image(9L, ListingImageType.THUMBNAIL, "listings/1001/a.webp");
        when(imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(1001L))
                .thenReturn(List.of(stored));

        List<ListingImageResponse> responses = withCdn.findAll(1001L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getImageUrl())
                .isEqualTo("https://cdn.example.com/listings/1001/a.webp");
        verify(storage, never()).presignGet(any(), any(), any());
    }

    @Test
    void buildsPresignedImageUrlsWhenPublicBaseUrlIsBlank() throws Exception {
        Listing listing = mock(Listing.class);
        when(listing.isPubliclyVisible()).thenReturn(true);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing));
        ListingImage stored = image(9L, ListingImageType.DETAIL, "listings/1001/a.webp");
        when(imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(1001L))
                .thenReturn(List.of(stored));
        when(storage.presignGet(
                        "limit-dev-media", "listings/1001/a.webp", Duration.ofMinutes(5)))
                .thenReturn(URI.create("https://s3.example.test/a.webp").toURL());

        List<ListingImageResponse> responses = service.findAll(1001L);

        assertThat(responses.get(0).getImageUrl()).isEqualTo("https://s3.example.test/a.webp");
    }

    @Test
    void rejectsDeleteWhenImageDoesNotBelongToListing() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        when(imageRepository.findByIdAndListingId(9L, 1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(55L, 1001L, 9L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_IMAGE_NOT_FOUND));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void promotesNextImageToThumbnailWhenThumbnailIsDeleted() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        ListingImage deleted = mock(ListingImage.class);
        when(deleted.getImageType()).thenReturn(ListingImageType.THUMBNAIL);
        when(deleted.getS3Key()).thenReturn("listings/1001/a.webp");
        when(imageRepository.findByIdAndListingId(9L, 1001L)).thenReturn(Optional.of(deleted));
        ListingImage remaining = mock(ListingImage.class);
        when(imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(1001L))
                .thenReturn(List.of(remaining));

        service.delete(55L, 1001L, 9L);

        verify(imageRepository).delete(deleted);
        verify(remaining).changeType(ListingImageType.THUMBNAIL);
        verify(events)
                .publishEvent(
                        new ListingImageDeletedEvent("limit-dev-media", "listings/1001/a.webp"));
    }

    @Test
    void keepsThumbnailUntouchedWhenDetailImageIsDeleted() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        ListingImage deleted = mock(ListingImage.class);
        when(deleted.getImageType()).thenReturn(ListingImageType.DETAIL);
        when(deleted.getS3Key()).thenReturn("listings/1001/b.webp");
        when(imageRepository.findByIdAndListingId(9L, 1001L)).thenReturn(Optional.of(deleted));

        service.delete(55L, 1001L, 9L);

        verify(imageRepository, never()).findAllByListingIdOrderByDisplayOrderAscIdAsc(any());
        verify(events)
                .publishEvent(
                        new ListingImageDeletedEvent("limit-dev-media", "listings/1001/b.webp"));
    }

    @Test
    void rejectsReorderWhenRequestedImageCountDiffers() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        when(imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(1001L))
                .thenReturn(List.of(mock(ListingImage.class), mock(ListingImage.class)));
        UpdateListingImageOrderRequest request = mock(UpdateListingImageOrderRequest.class);
        when(request.getImageIds()).thenReturn(List.of(1L));

        assertThatThrownBy(() -> service.updateOrder(55L, 1001L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
    }

    @Test
    void rejectsReorderWhenRequestedIdsContainDuplicates() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        when(imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(1001L))
                .thenReturn(List.of(mock(ListingImage.class), mock(ListingImage.class)));
        UpdateListingImageOrderRequest request = mock(UpdateListingImageOrderRequest.class);
        when(request.getImageIds()).thenReturn(List.of(1L, 1L));

        assertThatThrownBy(() -> service.updateOrder(55L, 1001L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
    }

    @Test
    void rejectsReorderWhenThumbnailIsNotPartOfListing() {
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        ListingImage first = mock(ListingImage.class);
        when(first.getId()).thenReturn(1L);
        when(imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(1001L))
                .thenReturn(List.of(first));
        UpdateListingImageOrderRequest request = mock(UpdateListingImageOrderRequest.class);
        when(request.getImageIds()).thenReturn(List.of(1L));
        when(request.getThumbnailImageId()).thenReturn(77L);

        assertThatThrownBy(() -> service.updateOrder(55L, 1001L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(first, never()).changeOrder(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void reordersImagesAndMovesThumbnailToRequestedImage() {
        ListingImageUploadService withCdn =
                serviceWith(properties("limit-dev-media", "https://cdn.example.com"));
        Listing owned = listingOwnedBy(55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(owned));
        ListingImage first = image(1L, ListingImageType.THUMBNAIL, "listings/1001/a.webp");
        ListingImage second = image(2L, ListingImageType.DETAIL, "listings/1001/b.webp");
        when(imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(1001L))
                .thenReturn(List.of(first, second));
        UpdateListingImageOrderRequest request = mock(UpdateListingImageOrderRequest.class);
        when(request.getImageIds()).thenReturn(List.of(2L, 1L));
        when(request.getThumbnailImageId()).thenReturn(2L);

        List<ListingImageResponse> responses = withCdn.updateOrder(55L, 1001L, request);

        assertThat(responses).extracting(ListingImageResponse::getImageId).containsExactly(2L, 1L);
        assertThat(responses.get(0).getImageUrl())
                .isEqualTo("https://cdn.example.com/listings/1001/b.webp");
        verify(second).changeOrder(0);
        verify(second).changeType(ListingImageType.THUMBNAIL);
        verify(first).changeOrder(1);
        verify(first).changeType(ListingImageType.DETAIL);
    }

    private ListingImageUploadService serviceWith(S3MediaProperties properties) {
        return new ListingImageUploadService(
                listingRepository,
                imageRepository,
                uploadSessionRepository,
                storage,
                properties,
                completionService,
                events);
    }

    private S3MediaProperties properties(String bucket, String publicBaseUrl) {
        return new S3MediaProperties(
                "ap-northeast-2",
                bucket,
                null,
                false,
                Duration.ofMinutes(10),
                Duration.ofMinutes(5),
                publicBaseUrl);
    }

    private CreateListingImageUploadUrlRequest imageRequest(
            String filename, String contentType, long fileSize) {
        return new CreateListingImageUploadUrlRequest(filename, contentType, fileSize);
    }

    private CompleteListingImageRequest completeRequest() {
        return new CompleteListingImageRequest("upload-1", ListingImageType.THUMBNAIL, 0);
    }

    private MediaUploadSession ownedSession() {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getPurpose()).thenReturn(MediaUploadPurpose.LISTING_IMAGE);
        when(session.getUploaderId()).thenReturn(55L);
        when(session.getListingId()).thenReturn(1001L);
        return session;
    }

    private MediaUploadSession pendingSession() {
        MediaUploadSession session = ownedSession();
        when(session.getStatus()).thenReturn(MediaUploadStatus.PENDING);
        when(session.getExpiresAt()).thenReturn(LocalDateTime.of(2999, 1, 1, 0, 0));
        when(session.getBucketName()).thenReturn("limit-dev-media");
        when(session.getObjectKey()).thenReturn("tmp/55/upload-1.webp");
        return session;
    }

    private ListingImage image(Long id, ListingImageType type, String key) {
        ListingImage image = mock(ListingImage.class);
        when(image.getId()).thenReturn(id);
        when(image.getImageType()).thenReturn(type);
        when(image.getS3Key()).thenReturn(key);
        return image;
    }

    private Listing listing(Long id, Long sellerId) {
        Listing listing = mock(Listing.class);
        when(listing.getId()).thenReturn(id);
        when(listing.getSellerId()).thenReturn(sellerId);
        return listing;
    }

    private Listing listingOwnedBy(Long sellerId) {
        Listing listing = mock(Listing.class);
        when(listing.getSellerId()).thenReturn(sellerId);
        return listing;
    }
}
