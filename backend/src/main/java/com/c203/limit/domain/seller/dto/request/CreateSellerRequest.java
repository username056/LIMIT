package com.c203.limit.domain.seller.dto.request;

import com.c203.limit.domain.seller.entity.SellerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateSellerRequest", description = "심사 없는 즉시 판매자 등록 요청")
public record CreateSellerRequest(
        @NotNull SellerType sellerType,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        @Size(max = 100) String businessName,
        @NotBlank @Size(max = 100) String settlementBankName,
        @NotBlank @Size(max = 100) String settlementAccountHolder,
        @NotBlank @Pattern(regexp = "^\\d{4}$") String settlementAccountLast4,
        @AssertTrue boolean sellerTermsAccepted) {}
