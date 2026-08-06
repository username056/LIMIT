package com.c203.limit.domain.seller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.entity.Seller;
import com.c203.limit.domain.seller.entity.SellerType;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
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
    @Mock ListingRepository listingRepository;
    @Mock MediaUrlResolver mediaUrlResolver;

    SellerService service;

    @BeforeEach
    void setUp() {
        service = new SellerService(sellerRepository, memberRepository, listingRepository, mediaUrlResolver);
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

    // 구매자에게 보여 줄 프로필에는 정산 계좌와 사업자 상호가 들어가면 안 된다.
    @Test
    void publicProfileExposesOnlyBuyerFacingFields() {
        Member member = verifiedMember(55L);
        Seller seller = Seller.register(
                55L, SellerType.BUSINESS, "KR", "리미트상회", "국민은행", "판매자", "1234");
        when(sellerRepository.findByMemberId(55L)).thenReturn(Optional.of(seller));
        when(memberRepository.findById(55L)).thenReturn(Optional.of(member));
        when(listingRepository.countPublicBySellerId(55L)).thenReturn(3L);

        var result = service.publicProfile(55L);

        assertThat(result.sellerId()).isEqualTo(55L);
        assertThat(result.nickname()).isEqualTo("seller");
        assertThat(result.sellerType()).isEqualTo("BUSINESS");
        assertThat(result.onSaleCount()).isEqualTo(3L);
        // 응답 레코드 자체에 정산·상호 필드가 없어야 한다.
        assertThat(result.toString())
                .doesNotContain("국민은행")
                .doesNotContain("1234")
                .doesNotContain("리미트상회");
    }

    // listing.seller_id는 FK 없는 회원 ID라서 판매자 등록 행이 없는 회원의 상품도 존재한다.
    // 그 상품 상세에서도 "누구에게 사는지"는 보여야 한다.
    @Test
    void publicProfileStillWorksWhenSellerRowIsMissing() {
        Member member = verifiedMember(20L);
        when(memberRepository.findById(20L)).thenReturn(Optional.of(member));
        when(sellerRepository.findByMemberId(20L)).thenReturn(Optional.empty());
        when(listingRepository.countPublicBySellerId(20L)).thenReturn(1L);

        var result = service.publicProfile(20L);

        assertThat(result.nickname()).isEqualTo("seller");
        assertThat(result.onSaleCount()).isEqualTo(1L);
        assertThat(result.sellerType()).isNull();
        assertThat(result.joinedAt()).isNull();
    }

    @Test
    void publicProfileFailsWhenMemberDoesNotExist() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publicProfile(99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
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
