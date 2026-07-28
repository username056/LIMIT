package com.c203.limit.domain.seller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.entity.Seller;
import com.c203.limit.domain.seller.entity.SellerType;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerServiceTests {

    @Mock SellerRepository sellerRepository;
    @Mock MemberRepository memberRepository;

    SellerService service;

    @BeforeEach
    void setUp() {
        service = new SellerService(sellerRepository, memberRepository);
    }

    @Test
    void registersVerifiedMemberAsActiveSellerImmediately() {
        Member member = verifiedMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(sellerRepository.saveAndFlush(any(Seller.class)))
                .thenAnswer(
                        invocation -> {
                            Seller seller = invocation.getArgument(0);
                            ReflectionTestUtils.setField(seller, "id", 11L);
                            return seller;
                        });

        var response = service.register(1L, individualRequest());

        assertThat(response.sellerId()).isEqualTo(11L);
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.sellerType()).isEqualTo("INDIVIDUAL");
    }

    @Test
    void rejectsDuplicateSellerRegistration() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(verifiedMember(1L)));
        when(sellerRepository.existsByMemberId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.register(1L, individualRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ALREADY_SELLER));
    }

    @Test
    void requiresBusinessNameForBusinessSeller() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(verifiedMember(1L)));

        var request =
                new CreateSellerRequest(
                        SellerType.BUSINESS,
                        "KR",
                        null,
                        "국민은행",
                        "판매자",
                        "1234",
                        true);

        assertThatThrownBy(() -> service.register(1L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    private Member verifiedMember(Long id) {
        Member member = Member.createLocal("seller@example.com", "encoded", "seller", null);
        member.verifyEmail();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private CreateSellerRequest individualRequest() {
        return new CreateSellerRequest(
                SellerType.INDIVIDUAL,
                "KR",
                null,
                "국민은행",
                "판매자",
                "1234",
                true);
    }
}
