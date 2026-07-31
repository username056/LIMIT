package com.c203.limit.domain.seller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 구매자에게 보여 주는 판매자 프로필.
 *
 * <p>{@link SellerProfileResponse}(본인 조회)와 일부러 분리했다. Seller 엔티티에는 정산 은행명·예금주· 계좌 끝 4자리와 사업자 상호가 들어
 * 있는데, 그건 판매자 본인만 볼 값이다. 이 DTO에는 구매자가 거래 상대를 가늠하는 데 필요한 것만 담는다.
 */
@Schema(name = "PublicSellerProfileResponse", description = "구매자에게 공개하는 판매자 프로필")
public record PublicSellerProfileResponse(
        @Schema(
                        description =
                                "판매자의 회원 ID. Listing.sellerId와 같은 값이며 seller 테이블의"
                                        + " PK(seller_id)가 아니다.",
                        example = "20")
                Long sellerId,
        @Schema(description = "판매자 닉네임", example = "limit_seller") String nickname,
        @Schema(description = "개인/사업자 구분. 판매자 등록 행이 없는 회원이면 null.", example = "INDIVIDUAL")
                String sellerType,
        @Schema(description = "판매자 등록 시각. 판매자 등록 행이 없으면 null.") OffsetDateTime joinedAt,
        @Schema(description = "현재 판매 중인 상품 수", example = "3") long onSaleCount) {}
