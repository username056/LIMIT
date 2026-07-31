package com.c203.limit.global.bootstrap;

import com.c203.limit.domain.auth.bootstrap.MemberBootstrapService;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.bootstrap.ProductDemoDataService;
import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.entity.SellerType;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.domain.seller.service.SellerService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@EnableConfigurationProperties(DemoDataProperties.class)
@ConditionalOnProperty(name = "limit.bootstrap.demo-data.enabled", havingValue = "true")
public class DemoDataRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataRunner.class);

    private final MemberBootstrapService memberBootstrapService;
    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final SellerService sellerService;
    private final ProductDemoDataService productDemoDataService;
    private final DemoDataProperties properties;

    public DemoDataRunner(
            MemberBootstrapService memberBootstrapService,
            MemberRepository memberRepository,
            SellerRepository sellerRepository,
            SellerService sellerService,
            ProductDemoDataService productDemoDataService,
            DemoDataProperties properties) {
        this.memberBootstrapService = memberBootstrapService;
        this.memberRepository = memberRepository;
        this.sellerRepository = sellerRepository;
        this.sellerService = sellerService;
        this.productDemoDataService = productDemoDataService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateProperties();
        int createdMembers = 0;
        int createdSellers = 0;
        int createdProducts = 0;

        for (int sequence = 1; sequence <= properties.getUserCount(); sequence++) {
            String email =
                    "demo%02d@%s"
                            .formatted(sequence, properties.getEmailDomain().toLowerCase(Locale.ROOT));
            String nickname = "데모회원%02d".formatted(sequence);
            String phone = "0109%07d".formatted(sequence);
            if (memberBootstrapService.ensureVerifiedMember(
                    email, properties.getPassword(), nickname, phone)) {
                createdMembers++;
            }

            Member member =
                    memberRepository
                            .findByEmailIgnoreCase(email)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Demo member was not found after bootstrap"));
            if (!sellerRepository.existsByMemberId(member.getId())) {
                sellerService.register(
                        member.getId(),
                        new CreateSellerRequest(
                                SellerType.INDIVIDUAL,
                                "KR",
                                null,
                                "데모은행",
                                nickname,
                                "%04d".formatted(sequence),
                                true));
                createdSellers++;
            }
            createdProducts +=
                    productDemoDataService.seedForSeller(
                            member.getId(), sequence, properties.getProductsPerUser());
        }

        log.info(
                "Demo data processed: requestedUsers={}, createdMembers={}, createdSellers={}, createdProducts={}",
                properties.getUserCount(),
                createdMembers,
                createdSellers,
                createdProducts);
    }

    private void validateProperties() {
        if (properties.getUserCount() < 1 || properties.getUserCount() > 100) {
            throw new IllegalStateException("DEMO_DATA_USER_COUNT must be between 1 and 100");
        }
        if (properties.getProductsPerUser() < 0 || properties.getProductsPerUser() > 20) {
            throw new IllegalStateException(
                    "DEMO_DATA_PRODUCTS_PER_USER must be between 0 and 20");
        }
        if (!StringUtils.hasText(properties.getPassword())) {
            throw new IllegalStateException(
                    "DEMO_DATA_PASSWORD is required when demo data is enabled");
        }
        if (!StringUtils.hasText(properties.getEmailDomain())) {
            throw new IllegalStateException(
                    "DEMO_DATA_EMAIL_DOMAIN is required when demo data is enabled");
        }
    }
}
