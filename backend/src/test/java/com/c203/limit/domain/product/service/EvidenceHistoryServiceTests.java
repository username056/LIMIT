package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvidenceHistoryServiceTests {

    private final ListingRepository listings = mock(ListingRepository.class);
    private final ListingChecklistItemRepository items =
            mock(ListingChecklistItemRepository.class);
    private final EvidenceRepository evidence = mock(EvidenceRepository.class);
    private final MediaUrlResolver urls = mock(MediaUrlResolver.class);
    private EvidenceHistoryService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceHistoryService(listings, items, evidence, urls);
    }

    @Test
    void hidesEvidenceWhenChecklistItemIsNotBuyerVisible() {
        Listing listing = mock(Listing.class);
        ListingChecklistItem item = mock(ListingChecklistItem.class);
        when(listing.getSellerId()).thenReturn(10L);
        when(item.isVisibleToBuyer()).thenReturn(false);
        when(listings.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(listing));
        when(items.findByIdAndListingId(2L, 1L)).thenReturn(Optional.of(item));

        assertThat(service.findAll(1L, 2L, 20L)).isEmpty();
    }

    @Test
    void ownerReceivesOrderedHistoryWithFreshMediaUrls() {
        Listing listing = mock(Listing.class);
        ListingChecklistItem item = mock(ListingChecklistItem.class);
        Evidence first = evidence(101L, item, "evidence/1/first.jpg", 1);
        Evidence second = evidence(102L, item, "evidence/1/second.jpg", 2);
        when(listing.getSellerId()).thenReturn(10L);
        when(item.getId()).thenReturn(2L);
        when(listings.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(listing));
        when(items.findByIdAndListingId(2L, 1L)).thenReturn(Optional.of(item));
        when(evidence.findAllByListingChecklistItem_IdOrderByUploadedAtAscIdAsc(2L))
                .thenReturn(List.of(first, second));
        when(urls.resolve("evidence/1/first.jpg", null)).thenReturn("first-url");
        when(urls.resolve("evidence/1/second.jpg", null)).thenReturn("second-url");

        var result = service.findAll(1L, 2L, 10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAttemptNo()).isEqualTo(1);
        assertThat(result.get(0).isLatest()).isFalse();
        assertThat(result.get(1).getMediaUrl()).isEqualTo("second-url");
        assertThat(result.get(1).isLatest()).isTrue();
    }

    private Evidence evidence(
            Long id, ListingChecklistItem item, String objectKey, int minute) {
        Evidence value = mock(Evidence.class);
        when(value.getId()).thenReturn(id);
        when(value.getListingChecklistItem()).thenReturn(item);
        when(value.getEvidenceType()).thenReturn(EvidenceType.PHOTO);
        when(value.getS3Key()).thenReturn(objectKey);
        when(value.getProcessingStatus()).thenReturn(EvidenceProcessingStatus.READY);
        when(value.getUploadedAt()).thenReturn(LocalDateTime.of(2026, 7, 29, 1, minute));
        return value;
    }
}
