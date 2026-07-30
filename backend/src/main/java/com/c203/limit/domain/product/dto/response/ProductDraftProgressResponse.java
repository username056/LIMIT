package com.c203.limit.domain.product.dto.response;

import java.util.Set;

public record ProductDraftProgressResponse(
        int step,
        Set<Long> confirmedChecklistItemIds) {}
