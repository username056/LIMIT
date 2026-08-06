package com.c203.limit.domain.product.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingImage;
import com.c203.limit.domain.product.entity.ListingImageType;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ListingImageHashPersistenceServiceTests {
    @Mock ListingImageRepository imageRepository;
    @Mock ModerationRiskService riskService;

    ListingImageHashPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ListingImageHashPersistenceService(
                imageRepository,
                riskService,
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void hashesAreStoredBeforeRiskAnalysisStarts() {
        Listing listing = listing();
        ListingImage image = ListingImage.create(
                listing, ListingImageType.DETAIL, "products/101", null, "image/png");
        ReflectionTestUtils.setField(image, "id", 101L);
        when(imageRepository.findById(101L)).thenReturn(Optional.of(image));

        service.record(
                101L,
                new ListingImageHashCalculator.ImageHashes(
                        "sha256", "0000000000000000"));

        assertThat(image.getContentSha256()).isEqualTo("sha256");
        assertThat(image.getPerceptualHash()).isEqualTo("0000000000000000");
        assertThat(image.getAnalyzedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 0, 0));
        verify(imageRepository).flush();
        verify(riskService).analyzeImage(101L);
    }

    @Test
    void unknownImageIsRejected() {
        when(imageRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(
                        404L, new ListingImageHashCalculator.ImageHashes("sha", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_IMAGE_NOT_FOUND));
    }

    private Listing listing() {
        Category category = Category.createTopLevel("Laptop", DeviceType.LAPTOP, 0);
        Listing listing = Listing.createDraft(
                55L, category, "LG Gram", "good condition", 500_000, 10L);
        ReflectionTestUtils.setField(listing, "id", 1001L);
        return listing;
    }
}
