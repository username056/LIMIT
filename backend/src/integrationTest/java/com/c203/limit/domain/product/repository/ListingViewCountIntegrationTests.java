package com.c203.limit.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.product.service.ProductViewCountRecorder;
import com.c203.limit.testsupport.AbstractMySqlIntegrationTest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 조회수 증가가 동시 요청에서 유실되지 않는지 확인한다.
 *
 * <p>엔티티를 읽어 +1 하고 저장하는 방식이면 여기서 갱신이 유실된다. 상세 조회는 서비스에서 가장
 * 동시성이 높은 경로라 이 유실이 실제로 발생하고, 그대로 두면 인기순 정렬의 근거가 무너진다.
 */
@SpringBootTest(
        properties =
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration")
class ListingViewCountIntegrationTests extends AbstractMySqlIntegrationTest {

    private static final int THREADS = 16;
    private static final int VIEWS_PER_THREAD = 25;

    /**
     * 리포지토리를 직접 부르지 않고 운영에서 실제로 타는 경로를 그대로 쓴다. {@code @Modifying}
     * 쿼리는 트랜잭션이 있어야 하고, 그 트랜잭션 경계를 여는 것이 이 컴포넌트의 책임이다.
     */
    @Autowired ProductViewCountRecorder viewCountRecorder;

    @Autowired JdbcTemplate jdbcTemplate;

    private int recordView(Long listingId) {
        return viewCountRecorder.record(listingId) ? 1 : 0;
    }

    @Test
    void countsEveryConcurrentViewWithoutLostUpdates() throws InterruptedException {
        Long listingId = insertListing("ON_SALE");

        AtomicInteger recorded = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        for (int thread = 0; thread < THREADS; thread++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < VIEWS_PER_THREAD; i++) {
                                recorded.addAndGet(recordView(listingId));
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        int expected = THREADS * VIEWS_PER_THREAD;
        assertThat(recorded.get()).isEqualTo(expected);
        assertThat(viewCount(listingId)).isEqualTo(expected);
    }

    /** 공개 상태가 아니면 집계하지 않는다. 갱신 행 수가 0이어야 호출부가 그 사실을 알 수 있다. */
    @Test
    void doesNotCountHiddenListing() {
        Long listingId = insertListing("HIDDEN");

        assertThat(recordView(listingId)).isZero();
        assertThat(viewCount(listingId)).isZero();
    }

    @Test
    void doesNotCountSoldListing() {
        Long listingId = insertListing("SOLD");

        assertThat(recordView(listingId)).isZero();
    }

    @Test
    void doesNotCountUnknownListing() {
        assertThat(recordView(-1L)).isZero();
    }

    /** 인기순 정렬이 실제로 조회수 내림차순인지 확인한다. */
    @Test
    void ordersByViewCountDescending() {
        Long low = insertListing("ON_SALE");
        Long high = insertListing("ON_SALE");
        recordView(low);
        for (int i = 0; i < 5; i++) recordView(high);

        List<Long> ordered = jdbcTemplate.queryForList(
                """
                SELECT id FROM listing
                 WHERE status = 'ON_SALE' AND deleted_at IS NULL AND id IN (?, ?)
                 ORDER BY view_count DESC
                """,
                Long.class,
                low,
                high);

        assertThat(ordered).containsExactly(high, low);
    }

    private long viewCount(Long listingId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT view_count FROM listing WHERE id = ?", Long.class, listingId);
        return value == null ? 0L : value;
    }

    private Long insertListing(String status) {
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT model_id FROM device_model ORDER BY model_id LIMIT 1", Long.class);
        // LAST_INSERT_ID()는 커넥션마다 다른 값이라 풀에서 다른 커넥션을 받으면 엉뚱한 id가 나온다.
        // 유일한 제목으로 다시 찾는다.
        String title = "조회수 검증용 매물 " + System.nanoTime();
        jdbcTemplate.update(
                """
                INSERT INTO listing (
                    seller_id, category_id, device_model_id, title, price,
                    checklist_template_id, precheck_completed, draft_step, status,
                    version, view_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, b'0', 1, ?, 0, 0,
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                1L,
                categoryId,
                categoryId,
                title,
                100000L,
                1L,
                status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM listing WHERE title = ?", Long.class, title);
    }
}
