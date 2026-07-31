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
}
