package com.c203.limit.domain.seller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.seller.entity.Seller;
import com.c203.limit.domain.seller.entity.SellerStatus;
import com.c203.limit.domain.seller.entity.SellerType;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerStatusReaderTests {

    @Mock SellerRepository sellerRepository;

    @Test
    void addsSellerRoleOnlyForActiveProfile() {
        Seller seller =
                Seller.register(
                        1L, SellerType.INDIVIDUAL, "KR", null, "은행", "예금주", "1234");
        when(sellerRepository.findByMemberId(1L)).thenReturn(Optional.of(seller));

        var roles = new SellerStatusReader(sellerRepository).rolesFor(1L);

        assertThat(roles).containsExactlyInAnyOrder("MEMBER", "SELLER");
    }

    @Test
    void rejectsSuspendedSellerEvenWhenAccessTokenStillHasSellerRole() {
        Seller seller =
                Seller.register(
                        1L, SellerType.INDIVIDUAL, "KR", null, "은행", "예금주", "1234");
        ReflectionTestUtils.setField(seller, "status", SellerStatus.SUSPENDED);
        when(sellerRepository.findByMemberId(1L)).thenReturn(Optional.of(seller));

        assertThatThrownBy(() -> new SellerStatusReader(sellerRepository).requireActiveSeller(1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SELLER_NOT_ACTIVE));
    }
}
