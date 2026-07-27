package com.c203.limit.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 결제 요청 생성/조회 유스케이스를 실제 MySQL 위에서 검증한다. 멱등키 재사용, 본인 매물 결제
 * 시도의 예약 롤백은 Mockito로는 확인할 수 없는 실제 트랜잭션·유니크 제약 동작이라 여기서 다룬다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        properties =
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration")
class PaymentIntegrationTests {

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("limit")
                    .withUsername("limit")
                    .withPassword("test-only-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired ListingStatusHistoryRepository listingStatusHistoryRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;

    private Long sellerId;
    private Long buyerId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        sellerId = memberRepository
                .save(Member.createLocal(unique("seller"), "encoded", unique("seller-nick"), null))
                .getId();
        buyerId = memberRepository
                .save(Member.createLocal(unique("buyer"), "encoded", unique("buyer-nick"), null))
                .getId();
        categoryId = categoryRepository
                .save(Category.createTopLevel(unique("category"), DeviceType.SMARTPHONE, 0))
                .getId();
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        listingStatusHistoryRepository.deleteAll();
        listingRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private Listing onSaleListing(Long sellerId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        Listing listing = Listing.createDraft(sellerId, category, "갤럭시 S24", "설명", 650_000, 10L);
        listing.completePrecheck();
        listing.publish();
        return listingRepository.save(listing);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    @Test
    void requestReservesListingAndPersistsPayment() {
        Listing listing = onSaleListing(sellerId);
        CreatePaymentRequest request =
                new CreatePaymentRequest(listing.getId(), PaymentMethod.CARD, unique("idem"));

        PaymentResponse response = paymentService.request(buyerId, request);

        assertThat(response.getStatus()).isEqualTo("REQUESTED");
        assertThat(response.getRequestedAmount()).isEqualByComparingTo("650000");

        Listing reloaded = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(reloaded.getBuyerId()).isEqualTo(buyerId);

        Payment saved = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertThat(saved.getBuyer().getId()).isEqualTo(buyerId);
    }

    @Test
    void requestWithSameIdempotencyKeyDoesNotReReserve() {
        Listing listing = onSaleListing(sellerId);
        String idempotencyKey = unique("idem");
        CreatePaymentRequest request =
                new CreatePaymentRequest(listing.getId(), PaymentMethod.CARD, idempotencyKey);

        PaymentResponse first = paymentService.request(buyerId, request);
        PaymentResponse second = paymentService.request(buyerId, request);

        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void requestRejectsSelfPurchaseAndRollsBackReservation() {
        Listing listing = onSaleListing(sellerId);
        Long listingId = listing.getId();
        CreatePaymentRequest request =
                new CreatePaymentRequest(listingId, PaymentMethod.CARD, unique("idem"));

        assertThatThrownBy(() -> paymentService.request(sellerId, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SELF_PURCHASE_NOT_ALLOWED));

        Listing reloaded = listingRepository.findById(listingId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.ON_SALE);
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void getRejectsAccessFromNonOwner() {
        Listing listing = onSaleListing(sellerId);
        PaymentResponse response = paymentService.request(
                buyerId,
                new CreatePaymentRequest(listing.getId(), PaymentMethod.CARD, unique("idem")));

        Long strangerId = memberRepository
                .save(Member.createLocal(unique("stranger"), "encoded", unique("stranger-nick"), null))
                .getId();

        assertThatThrownBy(() -> paymentService.get(strangerId, response.getPaymentId()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
    }

    @Test
    void requestRejectsSameKeyReusedByAnotherBuyer() {
        Listing listing = onSaleListing(sellerId);
        String idempotencyKey = unique("idem");
        paymentService.request(
                buyerId, new CreatePaymentRequest(listing.getId(), PaymentMethod.CARD, idempotencyKey));

        Long otherBuyerId = memberRepository
                .save(Member.createLocal(unique("other-buyer"), "encoded", unique("ob-nick"), null))
                .getId();
        Listing otherListing = onSaleListing(sellerId);
        CreatePaymentRequest reusedKeyRequest =
                new CreatePaymentRequest(otherListing.getId(), PaymentMethod.CARD, idempotencyKey);

        assertThatThrownBy(() -> paymentService.request(otherBuyerId, reusedKeyRequest))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void requestRejectsSameKeyWithDifferentListing() {
        Listing listing = onSaleListing(sellerId);
        Listing anotherListing = onSaleListing(sellerId);
        String idempotencyKey = unique("idem");
        paymentService.request(
                buyerId, new CreatePaymentRequest(listing.getId(), PaymentMethod.CARD, idempotencyKey));

        CreatePaymentRequest reusedKeyRequest =
                new CreatePaymentRequest(anotherListing.getId(), PaymentMethod.CARD, idempotencyKey);

        assertThatThrownBy(() -> paymentService.request(buyerId, reusedKeyRequest))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithSameKeyPersistExactlyOnePayment() throws InterruptedException {
        Listing listing = onSaleListing(sellerId);
        String idempotencyKey = unique("idem");
        CreatePaymentRequest request =
                new CreatePaymentRequest(listing.getId(), PaymentMethod.CARD, idempotencyKey);

        int attempts = 2;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<AtomicReference<Throwable>> failures = List.of(new AtomicReference<>(), new AtomicReference<>());

        try {
            for (int i = 0; i < attempts; i++) {
                int index = i;
                executor.submit(
                        () -> {
                            ready.countDown();
                            try {
                                start.await();
                                paymentService.request(buyerId, request);
                            } catch (Throwable throwable) {
                                failures.get(index).set(throwable);
                            }
                        });
            }
            ready.await();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        for (AtomicReference<Throwable> failure : failures) {
            assertThat(failure.get()).isNull();
        }
        assertThat(paymentRepository.count()).isEqualTo(1);
        Listing reloaded = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(reloaded.getBuyerId()).isEqualTo(buyerId);
    }
}
