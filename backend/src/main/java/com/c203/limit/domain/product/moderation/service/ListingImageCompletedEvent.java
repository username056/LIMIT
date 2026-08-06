package com.c203.limit.domain.product.moderation.service;

public record ListingImageCompletedEvent(Long imageId, String bucket, String objectKey) {}
