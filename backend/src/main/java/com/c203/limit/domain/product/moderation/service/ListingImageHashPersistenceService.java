package com.c203.limit.domain.product.moderation.service;

import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingImageHashPersistenceService {
    private static final Logger log =
            LoggerFactory.getLogger(ListingImageHashPersistenceService.class);

    private final ListingImageRepository imageRepository;
    private final ModerationRiskService riskService;
    private final Clock clock;

    public ListingImageHashPersistenceService(
            ListingImageRepository imageRepository, ModerationRiskService riskService, Clock clock) {
        this.imageRepository = imageRepository;
        this.riskService = riskService;
        this.clock = clock;
    }

    @Transactional
    public void record(Long imageId, ListingImageHashCalculator.ImageHashes hashes) {
        var image = imageRepository
                .findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_IMAGE_NOT_FOUND));
        image.recordHashes(hashes.sha256(), hashes.perceptualHash(), LocalDateTime.now(clock));
        imageRepository.flush();
        riskService.analyzeImage(imageId);
        log.info(
                "listing image hashes recorded: imageId={}, listingId={}, perceptualHashAvailable={}",
                imageId,
                image.getListing().getId(),
                hashes.perceptualHash() != null);
    }
}
