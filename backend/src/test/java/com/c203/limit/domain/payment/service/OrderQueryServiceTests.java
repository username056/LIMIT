package com.c203.limit.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.payment.dto.response.OrderSummaryResponse;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentMethod;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import com.c203.limit.domain.payment.repository.ListingOrderSummaryReader;
import com.c203.limit.domain.payment.repository.ListingOrderSummaryReader.ListingOrderSummary;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTests {

    private static final Long BUYER_ID = 2L;
    private static final Long LISTING_ID = 100L;
    private static final Long PAYMENT_ID = 500L;

    @Mock PaymentRepository paymentRepository;
    @Mock ListingOrderSummaryReader listingOrderSummaryReader;
    @Mock MediaUrlResolver mediaUrlResolver;

    OrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(paymentRepository, listingOrderSummaryReader, mediaUrlResolver);
    }

    private Payment payment(PaymentStatus status) {
        Member buyer = Member.createLocal("buyer@test.com", "encoded", "buyer", null);
        ReflectionTestUtils.setField(buyer, "id", BUYER_ID);
        Payment payment = Payment.request(
                LISTING_ID, buyer, "idem-1", BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        if (status == PaymentStatus.APPROVED) {
            payment.approve(BigDecimal.valueOf(650_000), "toss-key-1");
        } else if (status == PaymentStatus.CANCELLED) {
            payment.cancel("구매자가 결제 전 예약을 취소함");
        }
        return payment;
    }

    @Test
    void listOrdersReturnsEmptyWhenNoPayments() {
        when(paymentRepository.findByBuyer_IdAndStatusNotOrderByRequestedAtDesc(
                        BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(List.of());

        List<OrderSummaryResponse> result = service.listOrders(BUYER_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(listingOrderSummaryReader);
        verifyNoInteractions(mediaUrlResolver);
    }

    @Test
    void listOrdersExcludesRequestedPayments() {
        when(paymentRepository.findByBuyer_IdAndStatusNotOrderByRequestedAtDesc(
                        BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(List.of());

        service.listOrders(BUYER_ID);

        verify(paymentRepository)
                .findByBuyer_IdAndStatusNotOrderByRequestedAtDesc(eq(BUYER_ID), eq(PaymentStatus.REQUESTED));
    }

    @Test
    void listOrdersMapsPaymentAndListingFieldsWithResolvedThumbnail() {
        Payment payment = payment(PaymentStatus.APPROVED);
        when(paymentRepository.findByBuyer_IdAndStatusNotOrderByRequestedAtDesc(
                        BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(List.of(payment));
        ListingOrderSummary listing =
                new ListingOrderSummary(LISTING_ID, "갤럭시 S24", "PAID", "https://cdn/thumb.jpg", "s3/key.jpg");
        when(listingOrderSummaryReader.findByIds(List.of(LISTING_ID))).thenReturn(List.of(listing));
        when(mediaUrlResolver.resolve("s3/key.jpg", "https://cdn/thumb.jpg"))
                .thenReturn("https://resolved/thumb.jpg");

        List<OrderSummaryResponse> result = service.listOrders(BUYER_ID);

        assertThat(result).hasSize(1);
        OrderSummaryResponse summary = result.get(0);
        assertThat(summary.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(summary.getListingId()).isEqualTo(LISTING_ID);
        assertThat(summary.getProductName()).isEqualTo("갤럭시 S24");
        assertThat(summary.getThumbnailUrl()).isEqualTo("https://resolved/thumb.jpg");
        assertThat(summary.getPrice()).isEqualByComparingTo("650000");
        assertThat(summary.getPaymentStatus()).isEqualTo("APPROVED");
        assertThat(summary.getListingStatus()).isEqualTo("PAID");
        assertThat(summary.getRequestedAt()).isNotNull();
        assertThat(summary.getApprovedAt()).isNotNull();
    }

    @Test
    void listOrdersToleratesMissingListingData() {
        Payment payment = payment(PaymentStatus.CANCELLED);
        when(paymentRepository.findByBuyer_IdAndStatusNotOrderByRequestedAtDesc(
                        BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(List.of(payment));
        when(listingOrderSummaryReader.findByIds(List.of(LISTING_ID))).thenReturn(List.of());

        List<OrderSummaryResponse> result = service.listOrders(BUYER_ID);

        assertThat(result).hasSize(1);
        OrderSummaryResponse summary = result.get(0);
        assertThat(summary.getProductName()).isNull();
        assertThat(summary.getThumbnailUrl()).isNull();
        assertThat(summary.getListingStatus()).isNull();
        assertThat(summary.getPaymentStatus()).isEqualTo("CANCELLED");
        verifyNoInteractions(mediaUrlResolver);
    }
}
