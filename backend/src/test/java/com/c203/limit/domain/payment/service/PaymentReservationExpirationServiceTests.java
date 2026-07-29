package com.c203.limit.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentMethod;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.service.ListingService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentReservationExpirationServiceTests {

    private static final Long LISTING_ID = 100L;

    @Mock PaymentRepository paymentRepository;
    @Mock ListingService listingService;

    PaymentReservationExpirationService service;

    private Payment requestedPayment() {
        Member buyer = Member.createLocal("buyer@test.com", "encoded", "buyer", null);
        ReflectionTestUtils.setField(buyer, "id", 2L);
        return Payment.request(
                LISTING_ID, buyer, "idem-1", BigDecimal.valueOf(650_000), PaymentMethod.CARD);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new PaymentReservationExpirationService(paymentRepository, listingService);
    }

    @Test
    void expireOneMarksPaymentExpiredAndReleasesListing() {
        Payment payment = requestedPayment();
        when(paymentRepository.findByListingIdAndStatus(LISTING_ID, PaymentStatus.REQUESTED))
                .thenReturn(Optional.of(payment));

        ReservationExpirationResult result = service.expireOne(LISTING_ID);

        assertThat(result).isEqualTo(ReservationExpirationResult.EXPIRED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(payment.getFailedReason()).isNotBlank();
        verify(listingService).expireReservation(eq(LISTING_ID), anyString());
    }

    @Test
    void expireOneSkipsWhenNoRequestedPaymentExists() {
        when(paymentRepository.findByListingIdAndStatus(LISTING_ID, PaymentStatus.REQUESTED))
                .thenReturn(Optional.empty());

        ReservationExpirationResult result = service.expireOne(LISTING_ID);

        assertThat(result).isEqualTo(ReservationExpirationResult.SKIPPED_NO_PAYMENT);
        verify(listingService, never()).expireReservation(any(), anyString());
    }

    @Test
    void expireOnePropagatesExceptionWhenListingAlreadyChanged() {
        Payment payment = requestedPayment();
        when(paymentRepository.findByListingIdAndStatus(LISTING_ID, PaymentStatus.REQUESTED))
                .thenReturn(Optional.of(payment));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.LISTING_NOT_RESERVED))
                .when(listingService)
                .expireReservation(eq(LISTING_ID), anyString());

        assertThatThrownBy(() -> service.expireOne(LISTING_ID))
                .isInstanceOf(BusinessException.class);
    }
}
