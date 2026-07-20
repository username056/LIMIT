package com.c203.limit.domain.product.controller;

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

@Tag(name = "04. 상품")
public interface ProductApi {

    @Operation(operationId = "product1", summary = "상품 등록", description = "요청\n권한: SELLER / multipart/form-data - request(JSON): {\"brandId\":12,\"categoryId\":3,\"name\":\"Air Jordan 1 Retro High OG\",\"description\":\"정품 미개봉 제품입니다.\",\"price\":259000,\"quantity\":50} + images(파일들) + authenticityProofs(파일들)\n\n응답\n201 Created {\"data\":{\"productId\":1001,\"sellerId\":55,\"sellerName\":\"hypebeast_store\",\"brandId\":12,\"brandName\":\"Nike\",\"categoryId\":3,\"categoryName\":\"Sneakers\",\"name\":\"Air Jordan 1 Retro High OG\",\"description\":\"정품 미개봉 제품입니다.\",\"price\":259000,\"saleStatus\":\"BEFORE_SALE\",\"hasReport\":false,\"saleStartedAt\":null,\"images\":[{\"imageId\":1,\"imageType\":\"THUMBNAIL\",\"cdnUrl\":\"https://cdn.example.com/...\"}],\"authenticityProofs\":[{\"proofId\":1,\"proofType\":\"RECEIPT\",\"cdnUrl\":\"https://cdn.example.com/...\"}],\"inventory\":{\"inventoryId\":701,\"totalQuantity\":50,\"availableQuantity\":50,\"reservedQuantity\":0,\"soldQuantity\":0,\"status\":\"AVAILABLE\"},\"createdAt\":\"2026-07-16T10:00:00+09:00\",\"updatedAt\":\"2026-07-16T10:00:00+09:00\"}} / 오류: 401 UNAUTHORIZED, 400 VALIDATION_FAILED, 422 INVALID_FILE_TYPE, 422 MALICIOUS_FILE_DETECTED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/ProductDetailResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "422", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Void> product1(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateProductRequest"))) @RequestPart(name = "request", required = false) Object body,
            @RequestPart(name = "files", required = false) MultipartFile[] files
    );
    @Operation(operationId = "product2", summary = "상품 수정", description = "요청\n권한: SELLER(본인) / Path: productId(long) / Body(부분 수정): {\"price\":249000,\"description\":\"설명 수정본입니다.\",\"quantity\":40}\n\n응답\n200 OK {\"data\":{\"productId\":1001,\"sellerId\":55,\"sellerName\":\"hypebeast_store\",\"brandId\":12,\"brandName\":\"Nike\",\"categoryId\":3,\"categoryName\":\"Sneakers\",\"name\":\"Air Jordan 1 Retro High OG\",\"description\":\"설명 수정본입니다.\",\"price\":249000,\"saleStatus\":\"BEFORE_SALE\",\"hasReport\":false,\"saleStartedAt\":null,\"images\":[],\"authenticityProofs\":[],\"inventory\":{\"inventoryId\":701,\"totalQuantity\":40,\"availableQuantity\":40,\"reservedQuantity\":0,\"soldQuantity\":0,\"status\":\"AVAILABLE\"},\"createdAt\":\"2026-07-16T10:00:00+09:00\",\"updatedAt\":\"2026-07-16T11:20:00+09:00\"}} / 오류: 401 UNAUTHORIZED, 403 FORBIDDEN, 404 PRODUCT_NOT_FOUND, 409 PRODUCT_EDIT_NOT_ALLOWED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/ProductDetailResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> product2(
            @PathVariable("productId") Long productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateProductRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "product3", summary = "상품 목록 조회", description = "요청\n권한: PUBLIC / Query: keyword, brandId, categoryId, minPrice, maxPrice, saleStatus(기본 ON_SALE, HIDDEN/SUSPENDED는 서버가 강제 제외), page, size, sort\n\n응답\n200 OK {\"data\":[{\"productId\":1001,\"name\":\"Air Jordan 1 Retro High OG\",\"brandName\":\"Nike\",\"categoryName\":\"Sneakers\",\"price\":259000,\"saleStatus\":\"ON_SALE\",\"thumbnailUrl\":\"https://cdn.example.com/thumb/1001.jpg\",\"hasReport\":false}],\"meta\":{\"page\":0,\"size\":20,\"totalElements\":134,\"totalPages\":7,\"hasNext\":true}}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/ProductSummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> product3(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "brandId", required = false) Long brandId,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "saleStatus", required = false) String saleStatus,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort
    );
    @Operation(operationId = "product4", summary = "이미지 조회", description = "요청\n권한: PUBLIC / Path: productId(long)\n\n응답\n200 OK {\"data\":[{\"imageId\":1,\"imageType\":\"THUMBNAIL\",\"cdnUrl\":\"https://cdn.example.com/...\"},{\"imageId\":2,\"imageType\":\"DETAIL\",\"cdnUrl\":\"https://cdn.example.com/...\"}],\"meta\":null} / 오류: 404 PRODUCT_NOT_FOUND")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/ProductImageResponse")))),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/products/{productId}/images", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> product4(
            @PathVariable("productId") Long productId
    );
    @Operation(operationId = "product5", summary = "상품 상세 조회", description = "요청\n권한: PUBLIC / Path: productId(long) / HIDDEN·SUSPENDED 상품은 소유 셀러·관리자가 아니면 404\n\n응답\n200 OK {\"data\":{\"productId\":1001,\"sellerId\":55,\"sellerName\":\"hypebeast_store\",\"brandId\":12,\"brandName\":\"Nike\",\"categoryId\":3,\"categoryName\":\"Sneakers\",\"name\":\"Air Jordan 1 Retro High OG\",\"description\":\"정품 미개봉 제품입니다.\",\"price\":259000,\"saleStatus\":\"ON_SALE\",\"hasReport\":false,\"saleStartedAt\":\"2026-07-16T09:00:00+09:00\",\"images\":[{\"imageId\":1,\"imageType\":\"THUMBNAIL\",\"cdnUrl\":\"https://cdn.example.com/...\"}],\"authenticityProofs\":[{\"proofId\":1,\"proofType\":\"RECEIPT\",\"cdnUrl\":\"https://cdn.example.com/...\"}],\"inventory\":{\"inventoryId\":701,\"totalQuantity\":50,\"availableQuantity\":37,\"reservedQuantity\":5,\"soldQuantity\":8,\"status\":\"AVAILABLE\"},\"createdAt\":\"2026-07-16T10:00:00+09:00\",\"updatedAt\":\"2026-07-16T11:20:00+09:00\"}} / 오류: 404 PRODUCT_NOT_FOUND")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/ProductDetailResponse"))),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> product5(
            @PathVariable("productId") Long productId
    );
    @Operation(operationId = "product6", summary = "셀러 상품 목록", description = "요청\n권한: SELLER(본인) / Query: name, brandId, categoryId, saleStatus, hasReport, page, size, sort / seller_id는 토큰 강제, HIDDEN·SUSPENDED 포함 전체 조회 가능\n\n응답\n200 OK {\"data\":[{\"productId\":1001,\"name\":\"Air Jordan 1 Retro High OG\",\"brandName\":\"Nike\",\"categoryName\":\"Sneakers\",\"saleStatus\":\"ON_SALE\",\"hasReport\":false,\"createdAt\":\"2026-07-16T10:00:00+09:00\"}],\"meta\":{\"page\":0,\"size\":20,\"totalElements\":4,\"totalPages\":1,\"hasNext\":false}} / 오류: 401 UNAUTHORIZED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/ConsoleProductSummaryResponse")))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/sellers/me/products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> product6(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "brandId", required = false) Long brandId,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "saleStatus", required = false) String saleStatus,
            @RequestParam(name = "hasReport", required = false) Boolean hasReport,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort
    );
    @Operation(operationId = "product7", summary = "관리자 상품 목록", description = "요청\n권한: ADMIN / Query: name, sellerId, brandId, categoryId, saleStatus, hasReport, page, size, sort / 전체 판매자 상품 대상\n\n응답\n200 OK {\"data\":[{\"productId\":1001,\"name\":\"Air Jordan 1 Retro High OG\",\"sellerName\":\"hypebeast_store\",\"brandName\":\"Nike\",\"categoryName\":\"Sneakers\",\"saleStatus\":\"ON_SALE\",\"hasReport\":false,\"createdAt\":\"2026-07-16T10:00:00+09:00\"}],\"meta\":{\"page\":0,\"size\":20,\"totalElements\":812,\"totalPages\":41,\"hasNext\":true}} / 오류: 401 UNAUTHORIZED, 403 FORBIDDEN", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/ConsoleProductSummaryResponse")))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> product7(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "sellerId", required = false) Long sellerId,
            @RequestParam(name = "brandId", required = false) Long brandId,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "saleStatus", required = false) String saleStatus,
            @RequestParam(name = "hasReport", required = false) Boolean hasReport,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort
    );
    @Operation(operationId = "product8", summary = "상품 숨김·판매중지 / 상품 조치 해제", description = "요청\n권한: SELLER(본인) | ADMIN(전체) / Path: productId(long) / Body: {\"actionType\":\"SUSPEND\",\"reason\":\"정품 증빙 자료 진위 확인 불가로 판매 중지 처리합니다.\"} / actionType 구분 없이 소유 여부로만 판단\n\n권한: SELLER(본인) | ADMIN(전체) / Path: productId(long) / Body: {\"actionType\":\"RELEASE\",\"reason\":\"소명 자료 제출 완료로 조치를 해제합니다.\"} / 직전 조치가 누구 것이든 소유 셀러·관리자면 해제 가능(정책 재검토 필요)\n\n응답\n201 Created {\"data\":{\"actionId\":301,\"productId\":1001,\"actionType\":\"SUSPEND\",\"reason\":\"정품 증빙 자료 진위 확인 불가로 판매 중지 처리합니다.\",\"adminId\":7,\"adminName\":\"operator_kim\",\"createdAt\":\"2026-07-16T11:00:00+09:00\"}} / 오류: 401 UNAUTHORIZED, 400 VALIDATION_FAILED, 403 FORBIDDEN\n\n201 Created {\"data\":{\"actionId\":302,\"productId\":1001,\"actionType\":\"RELEASE\",\"reason\":\"소명 자료 제출 완료로 조치를 해제합니다.\",\"adminId\":null,\"adminName\":null,\"createdAt\":\"2026-07-16T12:00:00+09:00\"}} / 오류: 401 UNAUTHORIZED, 403 FORBIDDEN", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/ProductActionResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/products/{productId}/actions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> product8(
            @PathVariable("productId") Long productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateProductActionRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "product10", summary = "상품 조치 이력 조회", description = "요청\n권한: SELLER(본인) | ADMIN(전체) / Path: productId(long) / Query: page, size (기본 최신순 정렬)\n\n응답\n200 OK {\"data\":[{\"actionId\":302,\"productId\":1001,\"actionType\":\"RELEASE\",\"reason\":\"소명 자료 제출 완료로 조치를 해제합니다.\",\"adminId\":null,\"adminName\":null,\"createdAt\":\"2026-07-16T12:00:00+09:00\"},{\"actionId\":301,\"productId\":1001,\"actionType\":\"SUSPEND\",\"reason\":\"정품 증빙 자료 진위 확인 불가로 판매 중지 처리합니다.\",\"adminId\":7,\"adminName\":\"operator_kim\",\"createdAt\":\"2026-07-16T11:00:00+09:00\"}],\"meta\":{\"page\":0,\"size\":20,\"totalElements\":2,\"totalPages\":1,\"hasNext\":false}} / 오류: 401 UNAUTHORIZED, 403 FORBIDDEN", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/ProductActionResponse")))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/products/{productId}/actions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> product10(
            @PathVariable("productId") Long productId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    );
}
