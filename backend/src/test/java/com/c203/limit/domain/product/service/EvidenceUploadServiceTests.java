package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.product.dto.request.CompleteEvidenceRequest;
import com.c203.limit.domain.product.dto.request.CreateEvidenceUploadUrlRequest;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.entity.MediaUploadStatus;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceUploadServiceTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC);

    @Mock ListingRepository listingRepository;
    @Mock ListingChecklistItemRepository checklistItemRepository;
    @Mock MediaUploadSessionRepository uploadSessionRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock MediaObjectStorage storage;
    @Mock EvidenceUploadCompletionService completionService;
    @Mock VideoDurationVerifier videoDurationVerifier;
    EvidenceUploadService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceUploadService(
                listingRepository,
                checklistItemRepository,
                uploadSessionRepository,
                evidenceRepository,
                storage,
                new S3MediaProperties(
                        "ap-northeast-2",
                        "l1mit-dev-media-0b849303",
                        null,
                        false,
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(5),
                        ""),
                completionService,
                videoDurationVerifier,
                CLOCK);
    }

    @Test
    void createsPendingVideoUploadSessionAndPresignedUrl() throws Exception {
        Listing listing = listing(1001L, 55L);
        ListingChecklistItem item = videoItem(7002L, 5, 30, 20);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByIdAndListingId(7002L, 1001L))
                .thenReturn(Optional.of(item));
        when(storage.presignPut(
                        org.mockito.ArgumentMatchers.eq("l1mit-dev-media-0b849303"),
                        org.mockito.ArgumentMatchers.startsWith("tmp/55/"),
                        org.mockito.ArgumentMatchers.eq("video/mp4"),
                        org.mockito.ArgumentMatchers.eq(10_000L),
                        org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10))))
                .thenReturn(URI.create("https://s3.example.test/presigned").toURL());

        var response = service.createUploadUrl(
                55L,
                1001L,
                7002L,
                new CreateEvidenceUploadUrlRequest("hinge.mp4", "video/mp4", 10_000L, 12));

        assertThat(response.getUploadId()).hasSize(36);
        assertThat(response.getStorageKey()).startsWith("tmp/55/");
        assertThat(response.getPresignedUrl()).isEqualTo("https://s3.example.test/presigned");
        assertThat(response.getRequiredHeaders())
                .containsEntry("Content-Type", "video/mp4")
                .doesNotContainKey("Content-Length");
        verify(uploadSessionRepository).save(any(MediaUploadSession.class));
    }

    @Test
    void rejectsVideoOutsideChecklistDuration() {
        Listing listing = listing(1001L, 55L);
        ListingChecklistItem item = videoItem(7002L, 5, 30, 20);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByIdAndListingId(7002L, 1001L))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7002L,
                        new CreateEvidenceUploadUrlRequest(
                                "hinge.mp4", "video/mp4", 10_000L, 31)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsCompletedUploadBeforeCallingS3() {
        MediaUploadSession session = session(MediaUploadStatus.COMPLETED, 10_000L);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(
                        55L,
                        1001L,
                        7002L,
                        new CompleteEvidenceRequest(
                                "upload-1", OffsetDateTime.parse("2026-07-29T17:00:00+09:00"))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED));
        verify(storage, never()).head(any(), any());
    }

    @Test
    void rejectsS3ObjectWithDifferentSize() {
        MediaUploadSession session = session(MediaUploadStatus.PENDING, 10_000L);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));
        when(storage.head("bucket", "tmp/55/upload-1.mp4"))
                .thenReturn(new MediaObjectStorage.StoredObject(9_999L, "video/mp4"));

        assertThatThrownBy(() -> service.complete(
                        55L,
                        1001L,
                        7002L,
                        new CompleteEvidenceRequest("upload-1", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_MISMATCH));
        verify(storage, never()).promote(any(), any(), any(), any());
    }

    private Listing listing(Long id, Long sellerId) {
        Listing listing = org.mockito.Mockito.mock(Listing.class);
        when(listing.getId()).thenReturn(id);
        when(listing.getSellerId()).thenReturn(sellerId);
        return listing;
    }

    // 진단 앱이 결과를 파일로 내보내지 못하면 판매자는 화면을 찍어 올리는 수밖에 없다.
    // 사진을 막으면 그 항목을 채울 방법이 사라진다.
    @Test
    void acceptsPhotoAndTextForDiagnosticItem() throws Exception {
        for (String contentType : new String[] {"image/jpeg", "text/html", "text/plain"}) {
            Listing listing = listing(1001L, 55L);
            ListingChecklistItem item = diagnosticItem(7003L);
            when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                    .thenReturn(Optional.of(listing));
            when(checklistItemRepository.findByIdAndListingId(7003L, 1001L))
                    .thenReturn(Optional.of(item));
            when(storage.presignPut(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.eq(contentType),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.any(Duration.class)))
                    .thenReturn(URI.create("https://s3.example.test/presigned").toURL());

            var response = service.createUploadUrl(
                            55L,
                            1001L,
                            7003L,
                            new CreateEvidenceUploadUrlRequest(
                                    "diagnosis", contentType, 10_000L, null));

            assertThat(response.getRequiredHeaders()).containsEntry("Content-Type", contentType);
        }
    }

    @Test
    void rejectsVideoForDiagnosticItem() {
        Listing listing = listing(1001L, 55L);
        ListingChecklistItem item = diagnosticItem(7003L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByIdAndListingId(7003L, 1001L))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7003L,
                        new CreateEvidenceUploadUrlRequest(
                                "clip.mp4", "video/mp4", 10_000L, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(uploadSessionRepository, never()).save(any());
    }

    private ListingChecklistItem diagnosticItem(Long id) {
        ListingChecklistItem item = org.mockito.Mockito.mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getEvidenceType()).thenReturn(EvidenceType.DIAGNOSTIC_FILE);
        when(item.getMaxCount()).thenReturn(3);
        when(item.getMaxFileSizeMb()).thenReturn(20);
        return item;
    }

    private ListingChecklistItem videoItem(
            Long id, Integer minDuration, Integer maxDuration, Integer maxFileSizeMb) {
        ListingChecklistItem item = org.mockito.Mockito.mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getEvidenceType()).thenReturn(EvidenceType.VIDEO);
        when(item.getMinDurationSec()).thenReturn(minDuration);
        when(item.getMaxDurationSec()).thenReturn(maxDuration);
        when(item.getMaxFileSizeMb()).thenReturn(maxFileSizeMb);
        when(item.getMaxCount()).thenReturn(3);
        return item;
    }

    private MediaUploadSession session(MediaUploadStatus status, long expectedSize) {
        MediaUploadSession session = org.mockito.Mockito.mock(MediaUploadSession.class);
        ListingChecklistItem item = org.mockito.Mockito.mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(7002L);
        when(session.getUploadId()).thenReturn("upload-1");
        when(session.getUploaderId()).thenReturn(55L);
        when(session.getListingId()).thenReturn(1001L);
        when(session.getChecklistItem()).thenReturn(item);
        when(session.getStatus()).thenReturn(status);
        when(session.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 7, 29, 8, 10));
        when(session.getBucketName()).thenReturn("bucket");
        when(session.getObjectKey()).thenReturn("tmp/55/upload-1.mp4");
        when(session.getExpectedFileSize()).thenReturn(expectedSize);
        when(session.getExpectedMimeType()).thenReturn("video/mp4");
        return session;
    }

    @Test
    void rejectsUploadWhenMediaBucketIsNotConfigured() {
        for (String bucket : new String[] {null, "   "}) {
            EvidenceUploadService unconfigured = serviceWithBucket(bucket);

            assertThatThrownBy(() -> unconfigured.createUploadUrl(
                            55L,
                            1001L,
                            7002L,
                            new CreateEvidenceUploadUrlRequest(
                                    "hinge.mp4", "video/mp4", 10_000L, 12)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.MEDIA_STORAGE_NOT_CONFIGURED));
        }
        verify(listingRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void rejectsUploadForAProductThatDoesNotExist() {
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7002L,
                        new CreateEvidenceUploadUrlRequest("hinge.mp4", "video/mp4", 10_000L, 12)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsUploadFromAnotherSeller() {
        Listing fixtureListing1 = listing(1001L, 99L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(fixtureListing1));

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7002L,
                        new CreateEvidenceUploadUrlRequest("hinge.mp4", "video/mp4", 10_000L, 12)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
        verify(checklistItemRepository, never()).findByIdAndListingId(any(), any());
    }

    @Test
    void rejectsUploadForAChecklistItemOutsideTheProduct() {
        Listing fixtureListing2 = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(fixtureListing2));
        when(checklistItemRepository.findByIdAndListingId(7002L, 1001L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7002L,
                        new CreateEvidenceUploadUrlRequest("hinge.mp4", "video/mp4", 10_000L, 12)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ITEM_NOT_FOUND));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsUploadWhenChecklistItemAlreadyHoldsTheMaximumEvidence() {
        Listing fixtureListing3 = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(fixtureListing3));
        ListingChecklistItem fixtureItem4 = videoItem(7002L, 5, 30, 20);
        when(checklistItemRepository.findByIdAndListingId(7002L, 1001L))
                .thenReturn(Optional.of(fixtureItem4));
        when(evidenceRepository.countByListingChecklistItem_Id(7002L)).thenReturn(3L);

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7002L,
                        new CreateEvidenceUploadUrlRequest("hinge.mp4", "video/mp4", 10_000L, 12)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(uploadSessionRepository, never()).save(any());
    }

    /** 개수 제한이 없거나 0인 항목은 기존 증빙 수를 세지 않고 통과해야 한다. */
    @Test
    void skipsEvidenceCountCheckWhenChecklistItemHasNoUsableLimit() throws Exception {
        for (Integer maxCount : new Integer[] {null, 0}) {
            Listing fixtureListing5 = listing(1001L, 55L);
            when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                    .thenReturn(Optional.of(fixtureListing5));
            ListingChecklistItem fixtureItem6 = photoItem(7004L, maxCount, 20);
            when(checklistItemRepository.findByIdAndListingId(7004L, 1001L))
                    .thenReturn(Optional.of(fixtureItem6));
            when(storage.presignPut(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.eq("image/jpeg"),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.any(Duration.class)))
                    .thenReturn(URI.create("https://s3.example.test/presigned").toURL());

            var response = service.createUploadUrl(
                    55L,
                    1001L,
                    7004L,
                    new CreateEvidenceUploadUrlRequest(
                            "front.JPEG", "image/jpeg", 10_000L, null));

            assertThat(response.getStorageKey()).endsWith(".jpeg");
        }
        verify(evidenceRepository, never()).countByListingChecklistItem_Id(7004L);
    }

    @Test
    void rejectsUploadForAnItemThatExpectsASellerConfirmation() {
        Listing fixtureListing7 = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(fixtureListing7));
        ListingChecklistItem item = org.mockito.Mockito.mock(ListingChecklistItem.class);
        when(item.getEvidenceType()).thenReturn(EvidenceType.SELLER_CONFIRMATION);
        when(checklistItemRepository.findByIdAndListingId(7005L, 1001L))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7005L,
                        new CreateEvidenceUploadUrlRequest(
                                "front.jpg", "image/jpeg", 10_000L, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsFileLargerThanTheChecklistLimit() {
        Listing fixtureListing8 = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(fixtureListing8));
        ListingChecklistItem fixtureItem9 = photoItem(7004L, 3, 1);
        when(checklistItemRepository.findByIdAndListingId(7004L, 1001L))
                .thenReturn(Optional.of(fixtureItem9));

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7004L,
                        new CreateEvidenceUploadUrlRequest(
                                "front.jpg", "image/jpeg", 1024L * 1024L + 1, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsDurationOnANonVideoChecklistItem() {
        Listing fixtureListing10 = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(fixtureListing10));
        ListingChecklistItem fixtureItem11 = photoItem(7004L, 3, 20);
        when(checklistItemRepository.findByIdAndListingId(7004L, 1001L))
                .thenReturn(Optional.of(fixtureItem11));

        assertThatThrownBy(() -> service.createUploadUrl(
                        55L,
                        1001L,
                        7004L,
                        new CreateEvidenceUploadUrlRequest("front.jpg", "image/jpeg", 10_000L, 5)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsVideoWithoutOrBelowTheRequiredDuration() {
        Listing fixtureListing12 = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(fixtureListing12));
        ListingChecklistItem fixtureItem13 = videoItem(7002L, 5, 30, 20);
        when(checklistItemRepository.findByIdAndListingId(7002L, 1001L))
                .thenReturn(Optional.of(fixtureItem13));

        for (Integer duration : new Integer[] {null, 4}) {
            assertThatThrownBy(() -> service.createUploadUrl(
                            55L,
                            1001L,
                            7002L,
                            new CreateEvidenceUploadUrlRequest(
                                    "hinge.mp4", "video/mp4", 10_000L, duration)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
        }
        verify(uploadSessionRepository, never()).save(any());
    }

    /** 확장자는 저장 키에만 쓰이므로 확신할 수 없는 값은 붙이지 않는다. */
    @Test
    void dropsFilenameExtensionsThatCannotBeTrusted() throws Exception {
        for (String filename : new String[] {"diagnosis", "report.", "dump.verylongextension"}) {
            Listing fixtureListing14 = listing(1001L, 55L);
            when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                    .thenReturn(Optional.of(fixtureListing14));
            ListingChecklistItem fixtureItem15 = photoItem(7004L, 3, 20);
            when(checklistItemRepository.findByIdAndListingId(7004L, 1001L))
                    .thenReturn(Optional.of(fixtureItem15));
            when(storage.presignPut(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.eq("image/png"),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.any(Duration.class)))
                    .thenReturn(URI.create("https://s3.example.test/presigned").toURL());

            var response = service.createUploadUrl(
                    55L,
                    1001L,
                    7004L,
                    new CreateEvidenceUploadUrlRequest(filename, "image/png", 10_000L, null));

            assertThat(response.getStorageKey()).isEqualTo("tmp/55/" + response.getUploadId());
        }
    }

    @Test
    void normalizesContentTypeParametersBeforeValidating() throws Exception {
        Listing fixtureListing16 = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(fixtureListing16));
        ListingChecklistItem fixtureItem17 = photoItem(7004L, 3, 20);
        when(checklistItemRepository.findByIdAndListingId(7004L, 1001L))
                .thenReturn(Optional.of(fixtureItem17));
        when(storage.presignPut(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("image/webp"),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(URI.create("https://s3.example.test/presigned").toURL());

        var response = service.createUploadUrl(
                55L,
                1001L,
                7004L,
                new CreateEvidenceUploadUrlRequest(
                        "front.webp", "IMAGE/WEBP; charset=utf-8", 10_000L, null));

        assertThat(response.getRequiredHeaders()).containsEntry("Content-Type", "image/webp");
    }

    @Test
    void rejectsCompletionForAnUnknownUploadSession() {
        when(uploadSessionRepository.findById("upload-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(
                        55L, 1001L, 7002L, new CompleteEvidenceRequest("upload-x", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_NOT_FOUND));
        verify(storage, never()).head(any(), any());
    }

    @Test
    void rejectsCompletionWhenTheSessionBelongsToSomethingElse() {
        MediaUploadSession otherSeller = customSession(99L, 1001L, 7002L);
        MediaUploadSession otherListing = customSession(55L, 2002L, 7002L);
        MediaUploadSession otherItem = customSession(55L, 1001L, 8003L);
        MediaUploadSession withoutItem = customSession(55L, 1001L, null);
        when(uploadSessionRepository.findById("seller")).thenReturn(Optional.of(otherSeller));
        when(uploadSessionRepository.findById("listing")).thenReturn(Optional.of(otherListing));
        when(uploadSessionRepository.findById("item")).thenReturn(Optional.of(otherItem));
        when(uploadSessionRepository.findById("none")).thenReturn(Optional.of(withoutItem));

        for (String uploadId : new String[] {"seller", "listing", "item", "none"}) {
            assertThatThrownBy(() -> service.complete(
                            55L, 1001L, 7002L, new CompleteEvidenceRequest(uploadId, null)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
        }
        verify(storage, never()).head(any(), any());
    }

    @Test
    void rejectsCompletionAfterTheUploadWindowClosed() {
        MediaUploadSession session = session(MediaUploadStatus.PENDING, 10_000L);
        when(session.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 7, 29, 7, 59));
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(
                        55L, 1001L, 7002L, new CompleteEvidenceRequest("upload-1", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_EXPIRED));
        verify(storage, never()).head(any(), any());
    }

    @Test
    void reportsStorageUnavailableWhenTheHeadRequestFails() {
        MediaUploadSession session = session(MediaUploadStatus.PENDING, 10_000L);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));
        when(storage.head("bucket", "tmp/55/upload-1.mp4"))
                .thenThrow(S3Exception.builder().statusCode(503).message("unavailable").build());

        assertThatThrownBy(() -> service.complete(
                        55L, 1001L, 7002L, new CompleteEvidenceRequest("upload-1", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE));
        verify(videoDurationVerifier, never()).verify(any());
    }

    @Test
    void rejectsStoredObjectWhoseContentTypeDoesNotMatchTheSession() {
        MediaUploadSession session = session(MediaUploadStatus.PENDING, 10_000L);
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));

        for (String storedType : new String[] {"video/webm", null}) {
            when(storage.head("bucket", "tmp/55/upload-1.mp4"))
                    .thenReturn(new MediaObjectStorage.StoredObject(10_000L, storedType));

            assertThatThrownBy(() -> service.complete(
                            55L, 1001L, 7002L, new CompleteEvidenceRequest("upload-1", null)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.MEDIA_UPLOAD_MISMATCH));
        }
        verify(storage, never()).promote(any(), any(), any(), any());
    }

    @Test
    void reportsStorageUnavailableWhenPromotionFails() {
        MediaUploadSession session = session(MediaUploadStatus.PENDING, 10_000L);
        when(session.getFinalObjectKey()).thenReturn("evidence/1001/7002/final.mp4");
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));
        when(storage.head("bucket", "tmp/55/upload-1.mp4"))
                .thenReturn(new MediaObjectStorage.StoredObject(10_000L, "video/mp4"));
        org.mockito.Mockito.doThrow(
                        S3Exception.builder().statusCode(500).message("copy failed").build())
                .when(storage)
                .promote("bucket", "tmp/55/upload-1.mp4", "evidence/1001/7002/final.mp4", "video/mp4");

        assertThatThrownBy(() -> service.complete(
                        55L, 1001L, 7002L, new CompleteEvidenceRequest("upload-1", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE));
        verify(completionService, never())
                .complete(any(), any(), any(), any(), any(), any());
    }

    @Test
    void promotesVerifiedObjectAndDelegatesToTheCompletionService() {
        MediaUploadSession session = session(MediaUploadStatus.PENDING, 10_000L);
        when(session.getFinalObjectKey()).thenReturn("evidence/1001/7002/final.mp4");
        when(uploadSessionRepository.findById("upload-1")).thenReturn(Optional.of(session));
        when(storage.head("bucket", "tmp/55/upload-1.mp4"))
                .thenReturn(new MediaObjectStorage.StoredObject(10_000L, "VIDEO/MP4; codecs=avc1"));
        EvidenceResponse expected = org.mockito.Mockito.mock(EvidenceResponse.class);
        OffsetDateTime capturedAt = OffsetDateTime.parse("2026-07-29T17:00:00+09:00");
        when(completionService.complete(
                        55L,
                        1001L,
                        7002L,
                        "upload-1",
                        capturedAt,
                        LocalDateTime.of(2026, 7, 29, 8, 0)))
                .thenReturn(expected);

        var result = service.complete(
                55L, 1001L, 7002L, new CompleteEvidenceRequest("upload-1", capturedAt));

        assertThat(result).isSameAs(expected);
        verify(videoDurationVerifier).verify(session);
        verify(storage)
                .promote(
                        "bucket",
                        "tmp/55/upload-1.mp4",
                        "evidence/1001/7002/final.mp4",
                        "video/mp4");
    }

    /** 길이·용량 제한을 걸지 않은 항목은 어떤 값이 와도 통과시켜야 한다. */
    @Test
    void acceptsVideoWhenTheChecklistItemDeclaresNoSizeOrDurationLimit() throws Exception {
        Listing listing = listing(1001L, 55L);
        ListingChecklistItem item = videoItem(7006L, null, null, null);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByIdAndListingId(7006L, 1001L))
                .thenReturn(Optional.of(item));
        when(storage.presignPut(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("video/quicktime"),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(URI.create("https://s3.example.test/presigned").toURL());

        var response = service.createUploadUrl(
                55L,
                1001L,
                7006L,
                new CreateEvidenceUploadUrlRequest(
                        "clip.mov", "video/quicktime", 900_000_000L, 3_600));

        assertThat(response.getRequiredHeaders())
                .containsEntry("Content-Type", "video/quicktime");
        assertThat(response.getStorageKey()).endsWith(".mov");
    }

    private EvidenceUploadService serviceWithBucket(String bucket) {
        return new EvidenceUploadService(
                listingRepository,
                checklistItemRepository,
                uploadSessionRepository,
                evidenceRepository,
                storage,
                new S3MediaProperties(
                        "ap-northeast-2",
                        bucket,
                        null,
                        false,
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(5),
                        ""),
                completionService,
                videoDurationVerifier,
                CLOCK);
    }

    private ListingChecklistItem photoItem(Long id, Integer maxCount, Integer maxFileSizeMb) {
        ListingChecklistItem item = org.mockito.Mockito.mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getEvidenceType()).thenReturn(EvidenceType.PHOTO);
        when(item.getMaxCount()).thenReturn(maxCount);
        when(item.getMaxFileSizeMb()).thenReturn(maxFileSizeMb);
        return item;
    }

    private MediaUploadSession customSession(
            Long uploaderId, Long listingId, Long checklistItemId) {
        MediaUploadSession session = org.mockito.Mockito.mock(MediaUploadSession.class);
        when(session.getUploaderId()).thenReturn(uploaderId);
        when(session.getListingId()).thenReturn(listingId);
        if (checklistItemId == null) {
            when(session.getChecklistItem()).thenReturn(null);
        } else {
            ListingChecklistItem item = org.mockito.Mockito.mock(ListingChecklistItem.class);
            when(item.getId()).thenReturn(checklistItemId);
            when(session.getChecklistItem()).thenReturn(item);
        }
        return session;
    }
}
