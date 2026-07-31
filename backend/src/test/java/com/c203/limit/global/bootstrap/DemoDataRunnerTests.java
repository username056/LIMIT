package com.c203.limit.global.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.auth.bootstrap.MemberBootstrapService;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.bootstrap.ProductDemoDataService;
import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.domain.seller.service.SellerService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DemoDataRunnerTests {
    @Mock MemberBootstrapService memberBootstrapService;
    @Mock MemberRepository memberRepository;
    @Mock SellerRepository sellerRepository;
    @Mock SellerService sellerService;
    @Mock ProductDemoDataService productDemoDataService;

    @Test
    void createsVerifiedMembersSellersAndProducts() throws Exception {
        DemoDataProperties properties = properties();
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(101L);
        when(memberBootstrapService.ensureVerifiedMember(
                        "demo01@limit.local",
                        "LocalDemo123!",
                        "데모회원01",
                        "01090000001"))
                .thenReturn(true);
        when(memberRepository.findByEmailIgnoreCase("demo01@limit.local"))
                .thenReturn(Optional.of(member));
        when(productDemoDataService.seedForSeller(101L, 1, 3)).thenReturn(3);

        runner(properties).run(mock(ApplicationArguments.class));

        var sellerRequest = ArgumentCaptor.forClass(CreateSellerRequest.class);
        verify(sellerService).register(org.mockito.ArgumentMatchers.eq(101L), sellerRequest.capture());
        verify(productDemoDataService).seedForSeller(101L, 1, 3);
        org.assertj.core.api.Assertions.assertThat(sellerRequest.getValue().sellerTermsAccepted())
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(sellerRequest.getValue().settlementAccountLast4())
                .isEqualTo("0001");
    }

    @Test
    void requiresPasswordWhenEnabled() {
        DemoDataProperties properties = properties();
        properties.setPassword(" ");

        assertThatThrownBy(
                        () ->
                                runner(properties)
                                        .run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_DATA_PASSWORD");
    }

    private DemoDataRunner runner(DemoDataProperties properties) {
        return new DemoDataRunner(
                memberBootstrapService,
                memberRepository,
                sellerRepository,
                sellerService,
                productDemoDataService,
                properties);
    }

    private DemoDataProperties properties() {
        DemoDataProperties properties = new DemoDataProperties();
        properties.setUserCount(1);
        properties.setProductsPerUser(3);
        properties.setPassword("LocalDemo123!");
        return properties;
    }
}
