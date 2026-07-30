package com.c203.limit.domain.seller.service;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.entity.MemberStatus;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.dto.response.PublicSellerProfileResponse;
import com.c203.limit.domain.seller.dto.response.SellerProfileResponse;
import com.c203.limit.domain.seller.entity.Seller;
import com.c203.limit.domain.seller.entity.SellerType;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SellerService {
    private static final Logger log = LoggerFactory.getLogger(SellerService.class);
    // 상품·결제 응답과 같은 기준으로 변환한다. systemDefault를 쓰면 실행 호스트가 UTC일 때
    // 같은 화면 안에서 시각의 offset이 서로 달라진다(+09:00 계약이 깨진다).
    private static final ZoneId SELLER_TIME_ZONE = ZoneId.of("Asia/Seoul");
    private final SellerRepository sellerRepository;
    private final MemberRepository memberRepository;
    private final ListingRepository listingRepository;

    public SellerService(
            SellerRepository sellerRepository,
            MemberRepository memberRepository,
            ListingRepository listingRepository) {
        this.sellerRepository = sellerRepository;
        this.memberRepository = memberRepository;
        this.listingRepository = listingRepository;
    }

    @Transactional
    public SellerProfileResponse register(Long memberId, CreateSellerRequest request) {
        var member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE || member.getEmailVerifiedAt() == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_ACTIVE);
        }
        if (sellerRepository.existsByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.ALREADY_SELLER);
        }
        if (request.sellerType() == SellerType.BUSINESS
                && !StringUtils.hasText(request.businessName())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Seller seller =
                Seller.register(
                        memberId,
                        request.sellerType(),
                        request.countryCode(),
                        normalizeNullable(request.businessName()),
                        request.settlementBankName().trim(),
                        request.settlementAccountHolder().trim(),
                        request.settlementAccountLast4());
        try {
            SellerProfileResponse response =
                    SellerProfileResponse.from(sellerRepository.saveAndFlush(seller));
            log.info("seller profile activated: sellerType={}", response.sellerType());
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ALREADY_SELLER);
        }
    }

    @Transactional(readOnly = true)
    public SellerProfileResponse profile(Long memberId) {
        return sellerRepository
                .findByMemberId(memberId)
                .map(SellerProfileResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_PROFILE_NOT_FOUND));
    }

    /**
     * 구매자에게 보여 줄 판매자 프로필. 정산 계좌와 사업자 상호는 담지 않는다 — 거래 상대를 가늠하는
     * 데 필요하지 않고, 본인만 볼 값이다.
     *
     * <p>기준은 회원이다. {@code listing.seller_id}는 FK 없는 회원 ID여서, 판매자 등록 행이 없는
     * 회원의 상품도 존재할 수 있다(등록 경로가 여러 개이고 판매자 등록은 취소될 수 있다). 상품이
     * 보이는데 "누구에게 사는지"를 못 보여주면 안 되므로, seller 행이 없으면 개인·사업자 구분과
     * 등록 시각만 비우고 닉네임과 판매 중 개수는 그대로 돌려준다.
     */
    @Transactional(readOnly = true)
    public PublicSellerProfileResponse publicProfile(Long sellerMemberId) {
        Member member = memberRepository
                .findById(sellerMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Seller seller = sellerRepository.findByMemberId(sellerMemberId).orElse(null);
        long onSaleCount = listingRepository.countBySellerIdAndStatusAndDeletedAtIsNull(
                sellerMemberId, ListingStatus.ON_SALE);
        return new PublicSellerProfileResponse(
                sellerMemberId,
                member.getNickname(),
                seller == null ? null : seller.getSellerType().name(),
                seller == null ? null : offset(seller.getCreatedAt()),
                onSaleCount);
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(SELLER_TIME_ZONE).toOffsetDateTime();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
