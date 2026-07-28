package com.c203.limit.domain.seller.dto.response;

import com.c203.limit.domain.seller.entity.Seller;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "SellerProfileResponse", description = "판매자 프로필과 현재 상태")
public record SellerProfileResponse(
        Long sellerId,
        Long memberId,
        String sellerType,
        String status,
        String countryCode,
        String businessName,
        String settlementBankName,
        String settlementAccountHolder,
        String settlementAccountLast4,
        LocalDateTime createdAt) {

    public static SellerProfileResponse from(Seller seller) {
        return new SellerProfileResponse(
                seller.getId(),
                seller.getMemberId(),
                seller.getSellerType().name(),
                seller.getStatus().name(),
                seller.getCountryCode(),
                seller.getBusinessName(),
                seller.getSettlementBankName(),
                seller.getSettlementAccountHolder(),
                seller.getSettlementAccountLast4(),
                seller.getCreatedAt());
    }
}
