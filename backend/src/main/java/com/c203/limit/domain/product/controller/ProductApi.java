package com.c203.limit.domain.product.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.c203.limit.domain.product.dto.request.CreateProductRequest;
import com.c203.limit.domain.product.dto.request.TransitionProductStatusRequest;
import com.c203.limit.domain.product.dto.request.UpdateProductRequest;
import com.c203.limit.domain.product.dto.response.MyProductSummaryResponse;
import com.c203.limit.domain.product.dto.response.MyProductSummaryPageApiResponse;
import com.c203.limit.domain.product.dto.response.ProductCreatedApiResponse;
import com.c203.limit.domain.product.dto.response.ProductCreatedResponse;
import com.c203.limit.domain.product.dto.response.ProductDetailApiResponse;
import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.dto.response.ProductStatusTransitionApiResponse;
import com.c203.limit.domain.product.dto.response.ProductStatusTransitionResponse;
import com.c203.limit.domain.product.dto.response.ProductSummaryPageApiResponse;
import com.c203.limit.domain.product.dto.response.ProductSummaryResponse;
import com.c203.limit.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "03. 상품", description = "중고 전자기기 상품 CRUD와 거래 상태 API")
public interface ProductApi {

    @Operation(operationId = "product01", summary = "상품 등록", description = "ACTIVE 판매자만 상품 초안을 생성할 수 있으며 선택한 모델의 최신 체크리스트 템플릿을 스냅샷으로 고정합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "상품 초안 생성 성공", content = @Content(schema = @Schema(implementation = ProductCreatedApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_FAILED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SELLER role / ACTIVE seller required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DEVICE_MODEL_NOT_FOUND"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PRODUCT_DRAFT_LIMIT_EXCEEDED")
    })
    @PostMapping(path = "/api/v1/products", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ProductCreatedResponse>> createProduct(@Valid @RequestBody CreateProductRequest request);

    @Operation(operationId = "product02", summary = "상품 수정", description = "본인 상품을 부분 수정합니다. 예약 이후에는 모델과 주요 기기 정보를 수정할 수 없습니다.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상품 수정 성공", content = @Content(schema = @Schema(implementation = ProductDetailApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PRODUCT_EDIT_NOT_ALLOWED")
    })
    @PatchMapping(path = "/api/v1/products/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductRequest request
    );

    @Operation(operationId = "product03", summary = "상품 삭제", description = "상품을 논리 삭제합니다. 거래 중이거나 거래가 완료된 상품은 삭제할 수 없습니다.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "상품 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PRODUCT_DELETE_NOT_ALLOWED")
    })
    @DeleteMapping(path = "/api/v1/products/{productId}")
    ResponseEntity<Void> deleteProduct(@PathVariable Long productId);

    @Operation(operationId = "product04", summary = "상품 목록 조회", description = "공개 판매 상품을 기기·가격·지역·검증 상태로 검색합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상품 목록 조회 성공", content = @Content(schema = @Schema(implementation = ProductSummaryPageApiResponse.class)))
    @GetMapping(path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) Long deviceModelId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String tradeRegion,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    );

    @Operation(operationId = "product05", summary = "상품 상세 조회", description = "공개 가능한 상품 정보와 체크리스트 진행 요약을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상품 상세 조회 성공", content = @Content(schema = @Schema(implementation = ProductDetailApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND")
    })
    @GetMapping(path = "/api/v1/products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(@PathVariable Long productId);

    @Operation(
            operationId = "product05a",
            summary = "내 상품 상세 조회",
            description = "로그인한 판매자가 본인의 비공개 상태 상품을 포함한 상세 정보를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 상품 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = ProductDetailApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND")
    })
    @GetMapping(path = "/api/v1/members/me/products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ProductDetailResponse>> getMyProduct(@PathVariable Long productId);

    @Operation(operationId = "product06", summary = "내 상품 목록 조회", description = "ACTIVE 판매자가 등록한 모든 상태의 상품을 조회합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 상품 목록 조회 성공", content = @Content(schema = @Schema(implementation = MyProductSummaryPageApiResponse.class)))
    @GetMapping(path = "/api/v1/members/me/products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<List<MyProductSummaryResponse>>> getMyProducts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort
    );

    @Operation(operationId = "product07", summary = "상품 상태 전환", description = "허용된 상태 전이만 수행합니다. ON_SALE 전환에는 필수 체크리스트 완료가 필요합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "상품 상태 전환 성공", content = @Content(schema = @Schema(implementation = ProductStatusTransitionApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "INVALID_PRODUCT_STATUS_TRANSITION"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "REQUIRED_EVIDENCE_INCOMPLETE")
    })
    @PostMapping(path = "/api/v1/products/{productId}/status-transitions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ProductStatusTransitionResponse>> transitionProductStatus(
            @PathVariable Long productId,
            @Valid @RequestBody TransitionProductStatusRequest request
    );
}
