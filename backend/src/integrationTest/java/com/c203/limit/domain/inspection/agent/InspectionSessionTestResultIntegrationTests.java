package com.c203.limit.domain.inspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SubmitTestResultRequest;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.InspectionUserResult;
import com.c203.limit.domain.inspection.enums.MeasurementStatus;
import com.c203.limit.domain.inspection.enums.TestType;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.testsupport.AbstractMySqlIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties =
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration")
class InspectionSessionTestResultIntegrationTests extends AbstractMySqlIntegrationTest {
    // 값은 짧게 둔다. .githooks/pre-commit의 Secret 스캔이 token = "여덟 자 이상"을
    // 하드코딩된 비밀로 보고 커밋을 막는다. 페어링만 구분하면 되는 가짜 값이다.
    private static final String AGENT_TOKEN = "agent1";

    @Autowired InspectionSessionService service;
    @Autowired InspectionSessionRepository sessionRepository;
    @Autowired InspectionSessionTestResultRepository testResultRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;

    private Long sellerId;
    private Long categoryId;
    private Long listingId;
    private String sessionKey;

    @BeforeEach
    void setUp() {
        Member seller = memberRepository.save(Member.createLocal(
                shortUnique("inspection") + "@example.com",
                "encoded",
                shortUnique("seller"),
                null));
        sellerId = seller.getId();
        Category category = categoryRepository.save(
                Category.createTopLevel(unique("inspection-laptop"), DeviceType.LAPTOP, 0));
        categoryId = category.getId();
        Listing listing = listingRepository.save(
                Listing.createDraft(sellerId, category, "Windows laptop", "inspection", 1000, 10L));
        listingId = listing.getId();
        sessionKey = createPairedSession(AGENT_TOKEN);
    }

    @AfterEach
    void tearDown() {
        testResultRepository.deleteAll();
        sessionRepository.deleteAll();
        listingRepository.deleteById(listingId);
        categoryRepository.deleteById(categoryId);
        memberRepository.deleteById(sellerId);
    }

    @Test
    void idempotentReplayPersistsOneResultAndRetestIncrementsAttempt() {
        UUID clientResultId = UUID.randomUUID();
        SubmitTestResultRequest firstRequest = confirmedCamera(clientResultId);

        var first = service.submitTestResult(
                bearer(AGENT_TOKEN), sessionKey, firstRequest);
        var replay = service.submitTestResult(
                bearer(AGENT_TOKEN), sessionKey, firstRequest);
        var retest = service.submitTestResult(
                bearer(AGENT_TOKEN), sessionKey, confirmedCamera(UUID.randomUUID()));

        assertThat(first.created()).isTrue();
        assertThat(first.response().attemptNo()).isEqualTo(1);
        assertThat(first.response().listingId()).isEqualTo(listingId);
        assertThat(first.response().checklistItemId()).isNull();
        assertThat(replay.created()).isFalse();
        assertThat(replay.response().attemptNo()).isEqualTo(1);
        assertThat(retest.response().attemptNo()).isEqualTo(2);
        assertThat(testResultRepository.count()).isEqualTo(2);
        assertThat(service.listTestResults(sellerId, sessionKey))
                .extracting(result -> result.attemptNo())
                .containsExactly(1, 2);
    }

    @Test
    void conflictingReplayAndTokenFromAnotherSessionAreRejected() {
        UUID clientResultId = UUID.randomUUID();
        SubmitTestResultRequest original = confirmedCamera(clientResultId);
        service.submitTestResult(bearer(AGENT_TOKEN), sessionKey, original);

        SubmitTestResultRequest changed = new SubmitTestResultRequest(
                clientResultId,
                TestType.CAMERA,
                MeasurementStatus.DETECTED,
                InspectionUserResult.USER_CONFIRMED,
                java.util.Map.of("width", 640),
                original.testedAt(),
                null);
        assertThatThrownBy(() -> service.submitTestResult(
                        bearer(AGENT_TOKEN), sessionKey, changed))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.INSPECTION_TEST_RESULT_IDEMPOTENCY_CONFLICT));

        String otherToken = "agent2";
        createPairedSession(otherToken);
        assertThatThrownBy(() -> service.submitTestResult(
                        bearer(otherToken), sessionKey, confirmedCamera(UUID.randomUUID())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INSPECTION_AGENT_UNAUTHORIZED));
        assertThat(testResultRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentReplayPersistsExactlyOneResult() throws InterruptedException {
        SubmitTestResultRequest request = confirmedCamera(UUID.randomUUID());
        int calls = 2;
        var executor = Executors.newFixedThreadPool(calls);
        var ready = new CountDownLatch(calls);
        var start = new CountDownLatch(1);
        List<AtomicReference<InspectionSessionDtos.TestResultSubmission>> responses =
                List.of(new AtomicReference<>(), new AtomicReference<>());
        List<AtomicReference<Throwable>> failures =
                List.of(new AtomicReference<>(), new AtomicReference<>());

        try {
            for (int index = 0; index < calls; index++) {
                int responseIndex = index;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        responses.get(responseIndex)
                                .set(service.submitTestResult(
                                        bearer(AGENT_TOKEN), sessionKey, request));
                    } catch (Throwable throwable) {
                        failures.get(responseIndex).set(throwable);
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures).allSatisfy(failure -> assertThat(failure.get()).isNull());
        assertThat(responses)
                .extracting(response -> response.get().created())
                .containsExactlyInAnyOrder(true, false);
        assertThat(testResultRepository.count()).isEqualTo(1);
    }

    private String createPairedSession(String token) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        InspectionSession session = InspectionSession.create(
                UUID.randomUUID().toString(),
                hash(unique("pairing-code")),
                sellerId,
                listingId,
                now.plusMinutes(10),
                now);
        session.pair(hash(token), "integration-test", now.plusHours(1), now);
        return sessionRepository.saveAndFlush(session).getSessionKey();
    }

    private SubmitTestResultRequest confirmedCamera(UUID clientResultId) {
        return new SubmitTestResultRequest(
                clientResultId,
                TestType.CAMERA,
                MeasurementStatus.DETECTED,
                InspectionUserResult.USER_CONFIRMED,
                java.util.Map.of("width", 1280, "height", 720),
                OffsetDateTime.now(ZoneOffset.UTC),
                null);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String shortUnique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
