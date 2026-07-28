package com.c203.limit.domain.seller.service;

import com.c203.limit.domain.member.entity.MemberStatus;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.dto.response.SellerProfileResponse;
import com.c203.limit.domain.seller.entity.Seller;
import com.c203.limit.domain.seller.entity.SellerType;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SellerService {
    private static final Logger log = LoggerFactory.getLogger(SellerService.class);
    private final SellerRepository sellerRepository;
    private final MemberRepository memberRepository;

    public SellerService(SellerRepository sellerRepository, MemberRepository memberRepository) {
        this.sellerRepository = sellerRepository;
        this.memberRepository = memberRepository;
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

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
