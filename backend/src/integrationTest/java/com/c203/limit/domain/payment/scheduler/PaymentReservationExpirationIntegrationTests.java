package com.c203.limit.domain.payment.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.payment.dto.request.CreatePaymentRequest;
import com.c203.limit.domain.payment.dto.response.PaymentResponse;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentMethod;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 예약 유예 시간이 지난 미결제 매물을 스케줄러가 실제 MySQL 위에서 정리하는지 검증한다.
 * {@code @Scheduled} 주기 실행 자체는 검증하지 않고, 배치 메서드를 직접 호출해 트랜잭션·영속성
 * 동작만 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                    + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
            "limit.payment.reservation-expiration.initial-delay-ms=999999999"
        })
class PaymentReservationExpirationIntegrationTests {

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
    @Autowired PaymentReservationExpirationScheduler scheduler;
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

    private Listing onSaleListing() {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        Listing listing = Listing.createDraft(sellerId, category, "갤럭시 S24", "설명", 650_000, 10L);
        listing.completePrecheck();
        listing.publish();
        return listingRepository.save(listing);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private void makeOverdue(Long listingId) {
        Listing listing = listingRepository.findById(listingId).orElseThrow();
        ReflectionTestUtils.setField(listing, "reservedUntil", LocalDateTime.now().minusMinutes(1));
        listingRepository.save(listing);
    }

    @Test
    void expireOverdueReservationsReleasesListingAndExpiresPayment() {
        Listing listing = onSaleListing();
        PaymentResponse response = paymentService.request(
                buyerId,
                new CreatePaymentRequest(listing.getId(), PaymentMethod.CARD, unique("idem")));
        makeOverdue(listing.getId());

        scheduler.expireOverdueReservations();

        Listing reloaded = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.ON_SALE);
        assertThat(reloaded.getBuyerId()).isNull();
        assertThat(reloaded.getReservedUntil()).isNull();

        Payment payment = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(payment.getFailedReason()).isNotBlank();
    }

    @Test
    void expireOverdueReservationsIgnoresListingsStillWithinTtl() {
        Listing listing = onSaleListing();
        PaymentResponse response = paymentService.request(
                buyerId,
                new CreatePaymentRequest(listing.getId(), PaymentMethod.CARD, unique("idem")));

        scheduler.expireOverdueReservations();

        Listing reloaded = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.RESERVED);

        Payment payment = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
    }
}
