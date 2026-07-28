package com.c203.limit.domain.seller.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "seller")
public class Seller extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_id")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_type", nullable = false, length = 20)
    private SellerType sellerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerStatus status;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "business_name", length = 100)
    private String businessName;

    @Column(name = "settlement_bank_name", nullable = false, length = 100)
    private String settlementBankName;

    @Column(name = "settlement_account_holder", nullable = false, length = 100)
    private String settlementAccountHolder;

    @Column(name = "settlement_account_last4", nullable = false, length = 4)
    private String settlementAccountLast4;

    public static Seller register(
            Long memberId,
            SellerType sellerType,
            String countryCode,
            String businessName,
            String settlementBankName,
            String settlementAccountHolder,
            String settlementAccountLast4) {
        Seller seller = new Seller();
        seller.memberId = memberId;
        seller.sellerType = sellerType;
        seller.status = SellerStatus.ACTIVE;
        seller.countryCode = countryCode;
        seller.businessName = businessName;
        seller.settlementBankName = settlementBankName;
        seller.settlementAccountHolder = settlementAccountHolder;
        seller.settlementAccountLast4 = settlementAccountLast4;
        return seller;
    }

    public boolean isActive() {
        return status == SellerStatus.ACTIVE;
    }
}
