package com.c203.limit.domain.place.controller;

import com.c203.limit.domain.place.dto.response.PlaceSearchResultResponse;
import com.c203.limit.domain.place.service.PlaceSearchService;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "09. 장소 검색")
public class PlaceController {
    private final PlaceSearchService placeSearchService;

    public PlaceController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    @Operation(
            summary = "거래 지역용 장소(역/랜드마크) 키워드 검색",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/places/search")
    public ApiResponse<List<PlaceSearchResultResponse>> search(
            @RequestParam String query, @RequestParam(defaultValue = "8") int size) {
        int boundedSize = Math.max(1, Math.min(size, 15));
        return ApiResponse.ok(placeSearchService.search(query, boundedSize));
    }
}
