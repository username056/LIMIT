package com.c203.limit.domain.seller.service;

import com.c203.limit.domain.seller.entity.SellerStatus;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerStatusReader {
    private static final Logger log = LoggerFactory.getLogger(SellerStatusReader.class);
    private final SellerRepository sellerRepository;

    public SellerStatusReader(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    @Transactional(readOnly = true)
    public Set<String> rolesFor(Long memberId) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add("MEMBER");
        sellerRepository
                .findByMemberId(memberId)
                .filter(seller -> seller.getStatus() == SellerStatus.ACTIVE)
                .ifPresent(seller -> roles.add("SELLER"));
        return Set.copyOf(roles);
    }

    @Transactional(readOnly = true)
    public String statusOf(Long memberId) {
        return sellerRepository
                .findByMemberId(memberId)
                .map(seller -> seller.getStatus().name())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public void requireActiveSeller(Long memberId) {
        boolean active =
                sellerRepository
                        .findByMemberId(memberId)
                        .map(seller -> seller.isActive())
                        .orElse(false);
        if (!active) {
            log.warn("seller operation denied because active profile is missing");
            throw new BusinessException(ErrorCode.SELLER_NOT_ACTIVE);
        }
    }
}
