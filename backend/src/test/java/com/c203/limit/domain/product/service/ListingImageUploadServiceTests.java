package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.dto.request.CompleteListingImageRequest;
import com.c203.limit.domain.product.dto.request.CreateListingImageUploadUrlRequest;
import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingImageType;
import com.c203.limit.domain.product.entity.MediaUploadPurpose;
import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.entity.MediaUploadStatus;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.S3MediaProperties;
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
import org.springframework.context.ApplicationEventPublisher;

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

    private Listing listing(Long id, Long sellerId) {
        Listing listing = mock(Listing.class);
        when(listing.getId()).thenReturn(id);
        when(listing.getSellerId()).thenReturn(sellerId);
        return listing;
    }
}
