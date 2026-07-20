package com.c203.limit.statistics.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "16. 통계")
public interface StatisticsApi {

    @Operation(operationId = "statistics1", summary = "드롭 통계 조회", description = "요청\n권한: SELLER(본인) | ADMIN / Path: dropId(long), sale_type IN ('FCFS','RAFFLE')인 드롭만 대상(AUCTION은 STATISTICS-2 사용)\n\n응답\n200 OK {\"data\":{\"dropId\":5002,\"participantCount\":3204,\"competitionRate\":32.04,\"conversionRate\":18.5,\"aggregatedAt\":\"2026-07-16T09:00:00+09:00\"}} (집계 전: 0값) / 오류: 401 UNAUTHORIZED, 403 FORBIDDEN, 404 DROP_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/DropStatisticsResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/drops/{dropId}/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> statistics1(
            @PathVariable("dropId") Long dropId
    );
    @Operation(operationId = "statistics2", summary = "경매 통계 조회", description = "요청\n권한: SELLER(본인) | ADMIN / Path: auctionId(long) - 드롭과 별개 경로, 실제로는 sale_type='AUCTION'인 drop_event.drop_id와 동일값\n\n응답\n200 OK {\"data\":{\"auctionId\":5003,\"bidCount\":128,\"avgBidPrice\":412000,\"finalPrice\":530000,\"aggregatedAt\":\"2026-07-16T09:00:00+09:00\"}} (집계 전: 0값, finalPrice null) / 오류: 401 UNAUTHORIZED, 403 FORBIDDEN, 404 AUCTION_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/AuctionStatisticsResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/auctions/{auctionId}/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> statistics2(
            @PathVariable("auctionId") Long auctionId
    );
    @Operation(operationId = "statistics3", summary = "판매자 통계 조회", description = "요청\n권한: SELLER(본인) | ADMIN / Path: sellerId(long) / Query: periodType(DAILY|WEEKLY|MONTHLY, 기본 DAILY), from, to\n\n응답\n200 OK {\"data\":[{\"sellerId\":55,\"periodType\":\"DAILY\",\"periodStart\":\"2026-07-15\",\"salesCount\":42,\"refundRate\":2.4,\"disputeRate\":0.5,\"aggregatedAt\":\"2026-07-16T03:00:00+09:00\"}]} / 오류: 401 UNAUTHORIZED, 403 FORBIDDEN", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/SellerStatisticsResponse")))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/sellers/{sellerId}/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> statistics3(
            @PathVariable("sellerId") Long sellerId,
            @RequestParam(name = "periodType", required = false) String periodType,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to
    );
    @Operation(operationId = "statistics4", summary = "인기 상품 조회", description = "요청\n권한: PUBLIC / Query: categoryId(선택), limit(기본 10, 최대 20)\n\n응답\n200 OK {\"data\":[{\"rank\":1,\"productId\":1001,\"name\":\"Air Jordan 1 Retro High OG\",\"thumbnailUrl\":\"https://cdn.example.com/thumb/1001.jpg\",\"popularityScore\":982.5}]}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/PopularProductResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/popular-products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> statistics4(
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "limit", required = false) Integer limit
    );
    @Operation(operationId = "statistics5", summary = "추천 상품 조회", description = "요청\n권한: USER(로그인 필수) / Query: limit(기본 20)\n\n응답\n200 OK {\"data\":[{\"rank\":1,\"productId\":1044,\"name\":\"New Balance 990v6\",\"thumbnailUrl\":\"https://cdn.example.com/thumb/1044.jpg%22,%22score\":0.874}]} (추천 데이터 없으면 빈 배열) / 오류: 401 UNAUTHORIZED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/RecommendedProductResponse")))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/product-recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> statistics5(
            @RequestParam(name = "limit", required = false) Integer limit
    );
    @Operation(operationId = "statistics6", summary = "최근 본 상품", description = "요청\n권한: USER(로그인 필수) / Query: limit(기본 10, 최대 30) / Redis List(recently-viewed:{userId})로 서빙\n\n응답\n200 OK {\"data\":[{\"productId\":1001,\"name\":\"Air Jordan 1 Retro High OG\",\"thumbnailUrl\":\"https://cdn.example.com/thumb/1001.jpg\",\"viewedAt\":\"2026-07-16T09:40:00+09:00\"}]} / 오류: 401 UNAUTHORIZED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/RecentlyViewedProductResponse")))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/product-view-history", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> statistics6(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "List", required = false) String List
    );
}
