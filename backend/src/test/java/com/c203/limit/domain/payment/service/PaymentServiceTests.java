package com.c203.limit.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.payment.client.TossPaymentClient;
import com.c203.limit.domain.payment.client.TossPaymentClientException;
import com.c203.limit.domain.payment.client.TossPaymentResponse;
import com.c203.limit.domain.payment.dto.request.ConfirmPaymentRequest;
import com.c203.limit.domain.payment.dto.request.CreatePaymentRequest;
import com.c203.limit.domain.payment.dto.response.PaymentReconcileOutcome;
import com.c203.limit.domain.payment.dto.response.PaymentReconcileResponse;
import com.c203.limit.domain.payment.dto.response.PaymentResponse;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentMethod;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.service.ListingReservationView;
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
    @Mock TossPaymentClient tossPaymentClient;
    @Mock PlatformTransactionManager transactionManager;

    PaymentService service;

    @BeforeEach
    void setUp() {
        lenient()
                .when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));
        lenient().when(listingService.isReservationActive(any(), any())).thenReturn(true);
        service = new PaymentService(
                paymentRepository, memberRepository, listingService, tossPaymentClient, transactionManager);
    }

    private ListingReservationView listingView(Long sellerId) {
        return new ListingReservationView(sellerId, 650_000);
    }

    private Member buyer() {
        Member member = Member.createLocal("buyer@test.com", "encoded", "buyer", null);
        ReflectionTestUtils.setField(member, "id", BUYER_ID);
        return member;
    }

    private CreatePaymentRequest request(String idempotencyKey) {
        return new CreatePaymentRequest(LISTING_ID, PaymentMethod.CARD, idempotencyKey);
    }

    private Payment requestedPayment() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.assignProviderOrderId();
        return payment;
    }

    @Test
    void requestReservesListingAndCreatesPayment() {
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty());
        when(listingService.get(LISTING_ID)).thenReturn(listingView(SELLER_ID));
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(listingView(SELLER_ID));
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
        assertThat(response.getProviderOrderId()).isEqualTo("PAY-" + PAYMENT_ID + "-1");
    }

    @Test
    void requestContinuesExistingRequestedPaymentInsteadOfCreatingNew() {
        Payment existingRequested = requestedPayment();
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-new"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findTopByListingIdAndBuyer_IdAndStatusOrderByRequestedAtDesc(
                        LISTING_ID, BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(Optional.of(existingRequested));
        when(listingService.isReservationActive(LISTING_ID, BUYER_ID)).thenReturn(true);

        PaymentResponse response = service.request(
                BUYER_ID, new CreatePaymentRequest(LISTING_ID, PaymentMethod.TOSSPAY, "idem-new"));

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.getMethod()).isEqualTo("TOSSPAY");
        assertThat(existingRequested.getAttemptNo()).isEqualTo(2);
        verify(listingService).renewReservationForBuyer(LISTING_ID, BUYER_ID);
        verify(listingService, never()).reserve(any(), any());
        verify(listingService, never()).get(any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(memberRepository);
    }

    @Test
    void requestStillContinuesWithMostRecentWhenMultipleActiveRequestedPaymentsExist() {
        // findTopBy...가 "구매자당 REQUESTED는 최대 1건" 가정을 깔고 최신 1건만 가져오는데, 데이터
        // 이상으로 실제로는 여러 건일 수 있다. 그래도 흐름은 막지 않고 최신 1건으로 계속 진행해야
        // 한다 — 개수 확인은 운영자용 경고 로그만 남기고 결과에는 영향을 주지 않는다.
        Payment mostRecentRequested = requestedPayment();
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-new"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findTopByListingIdAndBuyer_IdAndStatusOrderByRequestedAtDesc(
                        LISTING_ID, BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(Optional.of(mostRecentRequested));
        when(paymentRepository.countByListingIdAndBuyer_IdAndStatus(
                        LISTING_ID, BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(2L);
        when(listingService.isReservationActive(LISTING_ID, BUYER_ID)).thenReturn(true);

        PaymentResponse response = service.request(
                BUYER_ID, new CreatePaymentRequest(LISTING_ID, PaymentMethod.CARD, "idem-new"));

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(mostRecentRequested.getAttemptNo()).isEqualTo(2);
    }

    @Test
    void requestRejectsWhenExistingRequestedPaymentHasConfirmAttempted() {
        Payment existingRequested = requestedPayment();
        existingRequested.markConfirmAttempted();
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-new"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findTopByListingIdAndBuyer_IdAndStatusOrderByRequestedAtDesc(
                        LISTING_ID, BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(Optional.of(existingRequested));

        assertThatThrownBy(() -> service.request(
                        BUYER_ID, new CreatePaymentRequest(LISTING_ID, PaymentMethod.CARD, "idem-new")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED));
        verify(listingService, never()).reserve(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void requestRejectsWhenExistingRequestedPaymentReservationNoLongerActive() {
        Payment existingRequested = requestedPayment();
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-new"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findTopByListingIdAndBuyer_IdAndStatusOrderByRequestedAtDesc(
                        LISTING_ID, BUYER_ID, PaymentStatus.REQUESTED))
                .thenReturn(Optional.of(existingRequested));
        when(listingService.isReservationActive(LISTING_ID, BUYER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.request(
                        BUYER_ID, new CreatePaymentRequest(LISTING_ID, PaymentMethod.CARD, "idem-new")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED));
        verify(listingService, never()).reserve(any(), any());
        verify(paymentRepository, never()).save(any());
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
    void requestRejectsSelfPurchaseWithoutReservingListing() {
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty());
        when(listingService.get(LISTING_ID)).thenReturn(listingView(BUYER_ID));

        assertThatThrownBy(() -> service.request(BUYER_ID, request("idem-1")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SELF_PURCHASE_NOT_ALLOWED));

        verify(listingService, never()).reserve(any(), any());
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
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(listingService.get(LISTING_ID)).thenReturn(listingView(SELLER_ID));
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(listingView(SELLER_ID));
        when(memberRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer()));
        when(paymentRepository.save(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        PaymentResponse response = service.request(BUYER_ID, request("idem-1"));

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        verify(listingService, times(1)).reserve(any(), any());
    }

    @Test
    void requestReportsConflictWhenUniqueViolationBelongsToAnotherBuyer() {
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty());
        when(listingService.get(LISTING_ID)).thenReturn(listingView(SELLER_ID));
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(listingView(SELLER_ID));
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
    void requestReportsConflictWhenLockFailureCannotBeRecoveredAfterMaxAttempts() {
        when(paymentRepository.findByBuyerIdAndIdempotencyKey(BUYER_ID, "idem-1"))
                .thenReturn(Optional.empty());
        when(listingService.get(LISTING_ID)).thenReturn(listingView(SELLER_ID));
        when(listingService.reserve(LISTING_ID, BUYER_ID)).thenReturn(listingView(SELLER_ID));
        when(memberRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer()));
        org.springframework.orm.ObjectOptimisticLockingFailureException thrown =
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Listing.class, LISTING_ID);
        when(paymentRepository.save(any(Payment.class))).thenThrow(thrown);

        assertThatThrownBy(() -> service.request(BUYER_ID, request("idem-1")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_REQUEST_CONFLICT));

        verify(listingService, times(3)).reserve(any(), any());
    }

    @Test
    void retryAttemptIssuesNewProviderOrderIdWhenReservationStillActive() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.assignProviderOrderId();
        String firstOrderId = payment.getProviderOrderId();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(listingService.isReservationActive(LISTING_ID, BUYER_ID)).thenReturn(true);

        PaymentResponse response = service.retryAttempt(BUYER_ID, PAYMENT_ID, PaymentMethod.CARD);

        assertThat(response.getStatus()).isEqualTo("REQUESTED");
        assertThat(payment.getAttemptNo()).isEqualTo(2);
        assertThat(payment.getProviderOrderId()).isEqualTo("PAY-" + PAYMENT_ID + "-2");
        assertThat(payment.getProviderOrderId()).isNotEqualTo(firstOrderId);
        verify(listingService).renewReservationForBuyer(LISTING_ID, BUYER_ID);
    }

    @Test
    void retryAttemptUpdatesPaymentMethodWhenChangedAtRetry() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.assignProviderOrderId();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(listingService.isReservationActive(LISTING_ID, BUYER_ID)).thenReturn(true);

        PaymentResponse response = service.retryAttempt(BUYER_ID, PAYMENT_ID, PaymentMethod.TOSSPAY);

        assertThat(response.getMethod()).isEqualTo("TOSSPAY");
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.TOSSPAY);
        verify(listingService).renewReservationForBuyer(LISTING_ID, BUYER_ID);
    }

    @Test
    void retryAttemptRejectsWhenReservationExpired() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(listingService.isReservationActive(LISTING_ID, BUYER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.retryAttempt(BUYER_ID, PAYMENT_ID, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED));
        assertThat(payment.getAttemptNo()).isEqualTo(1);
    }

    @Test
    void retryAttemptRejectsApprovedPayment() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.approve(java.math.BigDecimal.valueOf(650_000), "txn-1");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.retryAttempt(BUYER_ID, PAYMENT_ID, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED));
        assertThat(payment.getAttemptNo()).isEqualTo(1);
        verifyNoInteractions(listingService);
    }

    @Test
    void retryAttemptRejectsFailedPayment() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.fail("카드 승인이 거절되었습니다.");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.retryAttempt(BUYER_ID, PAYMENT_ID, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED));
        assertThat(payment.getAttemptNo()).isEqualTo(1);
        verifyNoInteractions(listingService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = PaymentStatus.class,
            names = {"CANCELLED", "EXPIRED"})
    void retryAttemptRejectsClosedPayments(PaymentStatus status) {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        if (status == PaymentStatus.CANCELLED) {
            payment.cancel("구매자가 결제 전 예약을 취소함");
        } else {
            payment.expire("예약 유예 시간 초과로 자동 만료");
        }
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.retryAttempt(BUYER_ID, PAYMENT_ID, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED));
        verifyNoInteractions(listingService);
    }

    @Test
    void retryAttemptRejectsRequestedPaymentWithConfirmAttempted() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.markConfirmAttempted();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.retryAttempt(BUYER_ID, PAYMENT_ID, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED));
        assertThat(payment.getAttemptNo()).isEqualTo(1);
        verifyNoInteractions(listingService);
    }

    @Test
    void retryAttemptRejectsNonOwner() {
        Payment payment =
                Payment.request(
                        LISTING_ID, buyer(), "idem-1", java.math.BigDecimal.valueOf(650_000), PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        Long otherMemberId = 999L;
        assertThatThrownBy(() -> service.retryAttempt(otherMemberId, PAYMENT_ID, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
        verifyNoInteractions(listingService);
    }

    @Test
    void retryAttemptThrowsNotFoundWhenPaymentMissing() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryAttempt(BUYER_ID, PAYMENT_ID, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
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
    void confirmApprovesPaymentAndMarksListingPaidOnSuccess() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.confirm(
                        "payment-key-1", payment.getProviderOrderId(), 650_000L, "payment-confirm-" + PAYMENT_ID + "-1"))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 650_000L, "CARD", null));

        PaymentResponse response = service.confirm(
                BUYER_ID,
                PAYMENT_ID,
                new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L));

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getApprovedAmount()).isEqualByComparingTo("650000");
        verify(listingService, times(1)).markPaid(LISTING_ID, BUYER_ID);
    }

    @Test
    void confirmEscalatesButKeepsApprovalWhenListingReservationNoLongerValid() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.confirm(
                        "payment-key-1", payment.getProviderOrderId(), 650_000L, "payment-confirm-" + PAYMENT_ID + "-1"))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 650_000L, "CARD", null));
        doThrow(new BusinessException(ErrorCode.LISTING_RESERVATION_MISMATCH))
                .when(listingService)
                .markPaid(LISTING_ID, BUYER_ID);

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_CONFIRM_RESERVATION_INVALID));

        assertThat(payment.getStatus().name()).isEqualTo("APPROVED");
    }

    @Test
    void confirmEscalatesForUnexpectedRuntimeExceptionDuringListingUpdate() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.confirm(
                        "payment-key-1", payment.getProviderOrderId(), 650_000L, "payment-confirm-" + PAYMENT_ID + "-1"))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 650_000L, "CARD", null));
        doThrow(new IllegalStateException("db hiccup"))
                .when(listingService)
                .markPaid(LISTING_ID, BUYER_ID);

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_CONFIRM_RESERVATION_INVALID));

        assertThat(payment.getStatus().name()).isEqualTo("APPROVED");
    }

    @Test
    void confirmRethrowsWhenLocalApprovalPersistFailsAfterTossSucceeds() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment))
                .thenReturn(Optional.of(payment))
                .thenThrow(new RuntimeException("db down"));
        when(tossPaymentClient.confirm(
                        "payment-key-1", payment.getProviderOrderId(), 650_000L, "payment-confirm-" + PAYMENT_ID + "-1"))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 650_000L, "CARD", null));

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(listingService, never()).markPaid(any(), any());
    }

    @Test
    void confirmRejectsWhenReservationNoLongerActiveWithoutCallingToss() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(listingService.isReservationActive(LISTING_ID, BUYER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_CONFIRM_RESERVATION_EXPIRED));

        assertThat(payment.getStatus().name()).isEqualTo("REQUESTED");
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void confirmRejectsOrderIdMismatch() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID, PAYMENT_ID, new ConfirmPaymentRequest("payment-key-1", "PAY-wrong", 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_ORDER_ID_MISMATCH));
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void confirmRejectsAmountMismatch() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 1_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void confirmReturnsExistingApprovalWhenReplayMatchesApprovedRecord() {
        Payment payment = requestedPayment();
        payment.approve(java.math.BigDecimal.valueOf(650_000), "payment-key-1");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.confirm(
                BUYER_ID,
                PAYMENT_ID,
                new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L));

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void confirmRejectsWhenReplayPaymentKeyMismatchesApprovedRecord() {
        Payment payment = requestedPayment();
        payment.approve(java.math.BigDecimal.valueOf(650_000), "txn-1");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_ALREADY_CONFIRMED_MISMATCH));
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void confirmMarksConfirmAttemptedBeforeCallingToss() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.confirm(
                        "payment-key-1", payment.getProviderOrderId(), 650_000L, "payment-confirm-" + PAYMENT_ID + "-1"))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 650_000L, "CARD", null));

        assertThat(payment.getConfirmAttemptedAt()).isNull();

        service.confirm(
                BUYER_ID,
                PAYMENT_ID,
                new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L));

        assertThat(payment.getConfirmAttemptedAt()).isNotNull();
    }

    @Test
    void confirmThrowsRetryableErrorWithoutFailingPaymentWhenTossFailureIsRetryable() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        TossPaymentClientException retryable = mock(TossPaymentClientException.class);
        when(retryable.isRetryable()).thenReturn(true);
        when(tossPaymentClient.confirm(any(), any(), anyLong(), any())).thenThrow(retryable);

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_CONFIRM_RETRYABLE));
        assertThat(payment.getStatus().name()).isEqualTo("REQUESTED");
        verify(listingService, never()).markPaid(any(), any());
    }

    @Test
    void confirmMarksPaymentFailedWhenTossRejectsNonRetryable() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        TossPaymentClientException rejected = mock(TossPaymentClientException.class);
        when(rejected.isRetryable()).thenReturn(false);
        when(rejected.getTossMessage()).thenReturn("카드 승인이 거절되었습니다.");
        when(tossPaymentClient.confirm(any(), any(), anyLong(), any())).thenThrow(rejected);

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.PAYMENT_CONFIRM_REJECTED);
                            assertThat(exception.getMessage()).isEqualTo("카드 승인이 거절되었습니다.");
                        });
        assertThat(payment.getStatus().name()).isEqualTo("FAILED");
        verify(listingService, never()).markPaid(any(), any());
        verify(listingService, times(1)).cancelReservation(eq(LISTING_ID), eq(BUYER_ID), any());
    }

    @Test
    void confirmKeepsFailedStatusWhenReservationReleaseFailsAfterRejection() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        TossPaymentClientException rejected = mock(TossPaymentClientException.class);
        when(rejected.isRetryable()).thenReturn(false);
        when(rejected.getTossMessage()).thenReturn("카드 승인이 거절되었습니다.");
        when(tossPaymentClient.confirm(any(), any(), anyLong(), any())).thenThrow(rejected);
        doThrow(new BusinessException(ErrorCode.LISTING_NOT_RESERVED))
                .when(listingService)
                .cancelReservation(eq(LISTING_ID), eq(BUYER_ID), any());

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_CONFIRM_REJECTED));
        assertThat(payment.getStatus().name()).isEqualTo("FAILED");
    }

    @Test
    void confirmRejectsNonOwner() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        Long otherMemberId = 999L;
        assertThatThrownBy(() -> service.confirm(
                        otherMemberId,
                        PAYMENT_ID,
                        new ConfirmPaymentRequest("payment-key-1", payment.getProviderOrderId(), 650_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void confirmThrowsNotFoundWhenPaymentMissing() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
                        BUYER_ID, PAYMENT_ID, new ConfirmPaymentRequest("payment-key-1", "PAY-1-1", 650_000L)))
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

    @Test
    void cancelReleasesReservationWhenRequested() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.cancel(BUYER_ID, PAYMENT_ID);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(payment.getStatus().name()).isEqualTo("CANCELLED");
        verify(listingService, times(1)).cancelReservation(eq(LISTING_ID), eq(BUYER_ID), any());
    }

    @Test
    void cancelIsIdempotentWhenAlreadyCancelled() {
        Payment payment = requestedPayment();
        payment.cancel("이미 취소됨");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.cancel(BUYER_ID, PAYMENT_ID);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verifyNoInteractions(listingService);
    }

    @Test
    void cancelIsIdempotentWhenAlreadyExpired() {
        Payment payment = requestedPayment();
        payment.expire("예약 유예 시간 초과로 자동 만료");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.cancel(BUYER_ID, PAYMENT_ID);

        assertThat(response.getStatus()).isEqualTo("EXPIRED");
        verifyNoInteractions(listingService);
    }

    @Test
    void cancelRejectsWhenAlreadyApproved() {
        Payment payment = requestedPayment();
        payment.approve(java.math.BigDecimal.valueOf(650_000), "txn-1");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.cancel(BUYER_ID, PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_NOT_CANCELLABLE));
        verifyNoInteractions(listingService);
    }

    @Test
    void cancelRejectsWhenAlreadyFailed() {
        Payment payment = requestedPayment();
        payment.fail("카드 승인이 거절되었습니다.");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.cancel(BUYER_ID, PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_NOT_CANCELLABLE));
        verifyNoInteractions(listingService);
    }

    @Test
    void cancelRejectsNonOwner() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        Long otherMemberId = 999L;
        assertThatThrownBy(() -> service.cancel(otherMemberId, PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
        verifyNoInteractions(listingService);
    }

    @Test
    void cancelThrowsNotFoundWhenPaymentMissing() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(BUYER_ID, PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    void cancelRejectsWhenConfirmAttemptedRecoversAsApprovedViaReconcile() {
        Payment payment = requestedPayment();
        payment.markConfirmAttempted();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.findByOrderId(payment.getProviderOrderId()))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 650_000L, "CARD", null));

        assertThatThrownBy(() -> service.cancel(BUYER_ID, PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_NOT_CANCELLABLE));
        assertThat(payment.getStatus().name()).isEqualTo("APPROVED");
        verify(listingService, never()).cancelReservation(any(), any(), any());
    }

    @Test
    void cancelProceedsWhenConfirmAttemptedButReconcileFindsNoTossRecord() {
        Payment payment = requestedPayment();
        payment.markConfirmAttempted();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        TossPaymentClientException notFound = mock(TossPaymentClientException.class);
        when(notFound.isRetryable()).thenReturn(false);
        when(tossPaymentClient.findByOrderId(payment.getProviderOrderId())).thenThrow(notFound);

        PaymentResponse response = service.cancel(BUYER_ID, PAYMENT_ID);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(payment.getStatus().name()).isEqualTo("CANCELLED");
        verify(listingService, times(1)).cancelReservation(eq(LISTING_ID), eq(BUYER_ID), any());
    }

    @Test
    void reconcileRecoversPaymentWhenTossReportsDoneWithMatchingAmount() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.findByOrderId(payment.getProviderOrderId()))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 650_000L, "CARD", null));

        PaymentReconcileResponse response = service.reconcile(PAYMENT_ID);

        assertThat(response.getOutcome()).isEqualTo(PaymentReconcileOutcome.RECOVERED);
        assertThat(response.getPayment().getStatus()).isEqualTo("APPROVED");
        // TTL이 지난 REQUESTED 건도 복구할 수 있어야 하므로, TTL을 검증하는 markPaid()가 아니라
        // 대사 전용 markPaidRecoveredFromPg()를 써야 한다.
        verify(listingService, times(1)).markPaidRecoveredFromPg(LISTING_ID, BUYER_ID);
        verify(listingService, never()).markPaid(any(), any());
    }

    @Test
    void reconcileEscalatesButKeepsApprovalWhenListingRecoveryFails() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.findByOrderId(payment.getProviderOrderId()))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 650_000L, "CARD", null));
        doThrow(new BusinessException(ErrorCode.LISTING_RESERVATION_MISMATCH))
                .when(listingService)
                .markPaidRecoveredFromPg(LISTING_ID, BUYER_ID);

        assertThatThrownBy(() -> service.reconcile(PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_CONFIRM_RESERVATION_INVALID));

        assertThat(payment.getStatus().name()).isEqualTo("APPROVED");
    }

    @Test
    void reconcileTakesNoActionWhenPaymentAlreadyResolved() {
        Payment payment = requestedPayment();
        payment.cancel("구매자가 결제 전 예약을 취소함");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentReconcileResponse response = service.reconcile(PAYMENT_ID);

        assertThat(response.getOutcome()).isEqualTo(PaymentReconcileOutcome.NO_ACTION);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void reconcileTakesNoActionWhenTossHasNoMatchingRecord() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        TossPaymentClientException notFound = mock(TossPaymentClientException.class);
        when(notFound.isRetryable()).thenReturn(false);
        when(tossPaymentClient.findByOrderId(payment.getProviderOrderId())).thenThrow(notFound);

        PaymentReconcileResponse response = service.reconcile(PAYMENT_ID);

        assertThat(response.getOutcome()).isEqualTo(PaymentReconcileOutcome.NO_ACTION);
        assertThat(payment.getStatus().name()).isEqualTo("REQUESTED");
    }

    @Test
    void reconcileThrowsRetryableWhenTossLookupFailsTemporarily() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        TossPaymentClientException retryable = mock(TossPaymentClientException.class);
        when(retryable.isRetryable()).thenReturn(true);
        when(tossPaymentClient.findByOrderId(payment.getProviderOrderId())).thenThrow(retryable);

        assertThatThrownBy(() -> service.reconcile(PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RECONCILE_RETRYABLE));
        assertThat(payment.getStatus().name()).isEqualTo("REQUESTED");
    }

    @Test
    void reconcileThrowsMismatchWhenTossAmountDiffersFromRequestedAmount() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.findByOrderId(payment.getProviderOrderId()))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "DONE", 1_000L, "CARD", null));

        assertThatThrownBy(() -> service.reconcile(PAYMENT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_RECONCILE_MISMATCH));
        assertThat(payment.getStatus().name()).isEqualTo("REQUESTED");
    }

    @Test
    void reconcileTakesNoActionWhenTossStatusIsNotDone() {
        Payment payment = requestedPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.findByOrderId(payment.getProviderOrderId()))
                .thenReturn(new TossPaymentResponse(
                        "payment-key-1", payment.getProviderOrderId(), "READY", 650_000L, "CARD", null));

        PaymentReconcileResponse response = service.reconcile(PAYMENT_ID);

        assertThat(response.getOutcome()).isEqualTo(PaymentReconcileOutcome.NO_ACTION);
        assertThat(payment.getStatus().name()).isEqualTo("REQUESTED");
    }
}
