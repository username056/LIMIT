package com.c203.limit.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.payment.dto.request.CreatePaymentRequest;
import com.c203.limit.domain.payment.dto.response.PaymentResponse;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentMethod;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.service.ListingService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    private static final Long LISTING_ID = 100L;
    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long PAYMENT_ID = 500L;

    @Mock PaymentRepository paymentRepository;
    @Mock MemberRepository memberRepository;
    @Mock ListingService listingService;
    @Mock PlatformTransactionManager transactionManager;

    PaymentService service;

    @BeforeEach
    void setUp() {
        lenient()
                .when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));
        service = new PaymentService(paymentRepository, memberRepository, listingService, transactionManager);
    }

    private Listing reservedListing(Long sellerId) {
        Category category = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 0);
        Listing listing = Listing.createDraft(sellerId, category, "갤럭시 S24", "설명", 650_000, 10L);
        ReflectionTestUtils.setField(listing, "id", LISTING_ID);
        ReflectionTestUtils.setField(listing, "status", ListingStatus.RESERVED);
        ReflectionTestUtils.setField(listing, "buyerId", BUYER_ID);
        return listing;
    }

    private Member buyer() {
        Member member = Member.createLocal("buyer@test.com", "encoded", "buyer", null);
        ReflectionTestUtils.setField(member, "id", BUYER_ID);
        return member;
    }

    private CreatePaymentRequest request(String idempotencyKey) {
        return new CreatePaymentRequest(LISTING_ID, PaymentMethod.CARD, idempotencyKey);
    }

    @Test
    void requestReservesListingAndCreatesPayment() {
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty());
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(reservedListing(SELLER_ID));
        when(memberRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer()));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(
                        invocation -> {
                            Payment payment = invocation.getArgument(0);
                            ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
                            return payment;
                        });

        PaymentResponse response = service.request(BUYER_ID, request("idem-1"));

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.getListingId()).isEqualTo(LISTING_ID);
        assertThat(response.getStatus()).isEqualTo("REQUESTED");
        assertThat(response.getMethod()).isEqualTo("CARD");
        assertThat(response.getRequestedAmount()).isEqualByComparingTo("650000");
    }

    @Test
    void requestReturnsExistingPaymentForDuplicateIdempotencyKey() {
        Payment existing =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(existing, "id", PAYMENT_ID);
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.of(existing));

        PaymentResponse response = service.request(BUYER_ID, request("idem-1"));

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        verifyNoInteractions(listingService);
        verifyNoInteractions(memberRepository);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void requestRejectsSelfPurchase() {
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty());
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(reservedListing(BUYER_ID));

        assertThatThrownBy(() -> service.request(BUYER_ID, request("idem-1")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SELF_PURCHASE_NOT_ALLOWED));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void requestRejectsWhenSameKeyReusedWithDifferentListing() {
        Payment existing =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(existing, "id", PAYMENT_ID);
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.of(existing));

        CreatePaymentRequest otherListingRequest =
                new CreatePaymentRequest(LISTING_ID + 1, PaymentMethod.CARD, "idem-1");

        assertThatThrownBy(() -> service.request(BUYER_ID, otherListingRequest))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    @Test
    void requestRejectsWhenSameKeyReusedWithDifferentMethod() {
        Payment existing =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(existing, "id", PAYMENT_ID);
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.of(existing));

        CreatePaymentRequest otherMethodRequest =
                new CreatePaymentRequest(LISTING_ID, PaymentMethod.ACCOUNT_TRANSFER, "idem-1");

        assertThatThrownBy(() -> service.request(BUYER_ID, otherMethodRequest))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    @Test
    void requestRecoversExistingPaymentWhenConcurrentSaveViolatesUniqueConstraint() {
        Payment existing =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(existing, "id", PAYMENT_ID);
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(reservedListing(SELLER_ID));
        when(memberRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer()));
        when(paymentRepository.save(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        PaymentResponse response = service.request(BUYER_ID, request("idem-1"));

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    void requestReportsConflictWhenUniqueViolationBelongsToAnotherBuyer() {
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty());
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(reservedListing(SELLER_ID));
        when(memberRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer()));
        when(paymentRepository.save(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        assertThatThrownBy(() -> service.request(BUYER_ID, request("idem-1")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    @Test
    void requestRethrowsWhenListingReservationRaceCannotBeRecovered() {
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty());
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(reservedListing(SELLER_ID));
        when(memberRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer()));
        org.springframework.orm.ObjectOptimisticLockingFailureException thrown =
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Listing.class, LISTING_ID);
        when(paymentRepository.save(any(Payment.class))).thenThrow(thrown);

        assertThatThrownBy(() -> service.request(BUYER_ID, request("idem-1")))
                .isSameAs(thrown);
    }

    @Test
    void getReturnsPaymentForOwner() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.get(BUYER_ID, PAYMENT_ID);

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    void getThrowsNotFoundWhenPaymentMissing() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(BUYER_ID, PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    void getRejectsNonOwner() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        Long otherMemberId = 999L;
        assertThatThrownBy(() -> service.get(otherMemberId, PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
    }
}
