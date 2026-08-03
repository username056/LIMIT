package com.c203.limit.domain.admin.dto.response;

import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import java.util.List;

public record AdminProductMaterialsResponse(
        Long productId,
        List<ListingImageResponse> images,
        List<AdminChecklistMaterialResponse> checklistItems) {}
