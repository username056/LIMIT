package com.c203.limit.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.entity.Wishlist;
import com.c203.limit.testsupport.AbstractMySqlIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties =
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration")
class WishlistRepositoryIntegrationTests extends AbstractMySqlIntegrationTest {
    private static final long MEMBER_ID = 95_001L;
    private static final long OTHER_MEMBER_ID = 95_002L;
    private static final long CONCURRENT_MEMBER_ID = 95_003L;
    private static final long CATEGORY_ID = 96_001L;
    private static final long PARENT_CATEGORY_ID = 96_010L;
    private static final long CHILD_CATEGORY_ID = 96_011L;
    private static final long SECOND_CHILD_CATEGORY_ID = 96_012L;
    private static final long ACTIVE_LISTING_ID = 97_001L;
    private static final long DELETED_LISTING_ID = 97_002L;
    private static final long TEMPLATE_ID = 98_001L;
    private static final long TEMPLATE_ITEM_ID = 98_101L;

    @Autowired WishlistRepository repository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ListingChecklistItemRepository checklistItemRepository;
    @Autowired ListingImageRepository listingImageRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        cleanup();
        insertCategory();
        insertCatalogTree();
        insertListing(ACTIVE_LISTING_ID, null);
        insertListing(DELETED_LISTING_ID, "CURRENT_TIMESTAMP(6)");
        insertChecklistAndThumbnail();
        insertWishlist(MEMBER_ID, ACTIVE_LISTING_ID);
        insertWishlist(MEMBER_ID, DELETED_LISTING_ID);
        insertWishlist(OTHER_MEMBER_ID, ACTIVE_LISTING_ID);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void returnsOnlyCurrentMembersNonDeletedListings() {
        Page<Wishlist> result = repository.findActiveByUserId(
                MEMBER_ID,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .singleElement()
                .satisfies(
                        wishlist -> {
                            assertThat(wishlist.getListing().getId()).isEqualTo(ACTIVE_LISTING_ID);
                            assertThat(wishlist.getListing().getCategory().getName())
                                    .isEqualTo("Galaxy S24");
                        });
    }

    @Test
    @Transactional
    void findsAndDeletesByMemberAndListingWithoutAffectingOtherMember() {
        Optional<Wishlist> favorite =
                repository.findByUserIdAndListingId(MEMBER_ID, ACTIVE_LISTING_ID);

        assertThat(favorite).isPresent();
        assertThat(repository.deleteByUserIdAndListingId(MEMBER_ID, ACTIVE_LISTING_ID))
                .isEqualTo(1L);
        assertThat(repository.findByUserIdAndListingId(MEMBER_ID, ACTIVE_LISTING_ID)).isEmpty();
        assertThat(repository.findByUserIdAndListingId(OTHER_MEMBER_ID, ACTIVE_LISTING_ID))
                .isPresent();
    }

    @Test
    void concurrentInsertIgnoreCreatesOneFavoriteAndBothRequestsCanReadIt() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Integer> request = () -> transactionTemplate.execute(status -> {
            await(barrier);
            int inserted = repository.insertIgnore(CONCURRENT_MEMBER_ID, ACTIVE_LISTING_ID);
            assertThat(repository.findByUserIdAndListingId(
                            CONCURRENT_MEMBER_ID, ACTIVE_LISTING_ID))
                    .isPresent();
            return inserted;
        });

        try {
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void loadsChecklistCountsAndThumbnailInBatch() {
        var counts = checklistItemRepository.countRequiredByListingIds(
                List.of(ACTIVE_LISTING_ID, DELETED_LISTING_ID),
                ChecklistItemCompletionStatus.COMPLETED);
        var thumbnails = listingImageRepository.findFirstByListingIdsAndImageType(
                List.of(ACTIVE_LISTING_ID, DELETED_LISTING_ID),
                com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL);

        assertThat(counts)
                .singleElement()
                .satisfies(count -> {
                    assertThat(count.getListingId()).isEqualTo(ACTIVE_LISTING_ID);
                    assertThat(count.getRequiredCount()).isEqualTo(1L);
                    assertThat(count.getCompletedRequiredCount()).isEqualTo(1L);
                });
        assertThat(thumbnails)
                .singleElement()
                .satisfies(thumbnail -> {
                    assertThat(thumbnail.getListingId()).isEqualTo(ACTIVE_LISTING_ID);
                    assertThat(thumbnail.getCdnUrl()).isEqualTo("https://cdn.example.com/active.jpg");
                });
    }

    @Test
    @Transactional
    void loadsModelParentsWithoutPerModelQueries() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var models = categoryRepository.findModels(
                PARENT_CATEGORY_ID, null, null, PageRequest.of(0, 100));
        models.getContent().forEach(category -> {
            if (category.getParent() != null) {
                assertThat(category.getParent().getId()).isEqualTo(PARENT_CATEGORY_ID);
            }
        });

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2L);
    }

    @Test
    @Transactional
    void loadsChildCategoryParentsInOneQuery() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var children = categoryRepository.findByParentIdOrderByDisplayOrderAsc(PARENT_CATEGORY_ID);
        children.forEach(category ->
                assertThat(category.getParent().getId()).isEqualTo(PARENT_CATEGORY_ID));

        assertThat(children).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void cleanup() {
        jdbcTemplate.update(
                "DELETE FROM listing_image WHERE listing_id IN (?, ?)",
                ACTIVE_LISTING_ID,
                DELETED_LISTING_ID);
        jdbcTemplate.update(
                "DELETE FROM listing_checklist_item WHERE listing_id IN (?, ?)",
                ACTIVE_LISTING_ID,
                DELETED_LISTING_ID);
        jdbcTemplate.update(
                "DELETE FROM wishlist WHERE listing_id IN (?, ?)",
                ACTIVE_LISTING_ID,
                DELETED_LISTING_ID);
        jdbcTemplate.update(
                "DELETE FROM listing WHERE id IN (?, ?)",
                ACTIVE_LISTING_ID,
                DELETED_LISTING_ID);
        jdbcTemplate.update("DELETE FROM checklist_template_item WHERE id = ?", TEMPLATE_ITEM_ID);
        jdbcTemplate.update("DELETE FROM checklist_template WHERE id = ?", TEMPLATE_ID);
        jdbcTemplate.update(
                "DELETE FROM category WHERE id IN (?, ?)",
                CHILD_CATEGORY_ID,
                SECOND_CHILD_CATEGORY_ID);
        jdbcTemplate.update("DELETE FROM category WHERE id = ?", PARENT_CATEGORY_ID);
        jdbcTemplate.update("DELETE FROM category WHERE id = ?", CATEGORY_ID);
    }

    private void insertChecklistAndThumbnail() {
        jdbcTemplate.update(
                """
                INSERT INTO checklist_template (id, category_id, version, status, created_at)
                VALUES (?, ?, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6))
                """,
                TEMPLATE_ID,
                CATEGORY_ID);
        jdbcTemplate.update(
                """
                INSERT INTO checklist_template_item (
                    id, checklist_template_id, item_code, name, purpose, capture_guide,
                    evidence_type, automation_type, is_required, visible_to_buyer,
                    privacy_masking_required, display_order
                ) VALUES (?, ?, 'BASIC-01', '외관', '외관 확인', '전체 촬영',
                          'PHOTO', 'NONE', b'1', b'1', b'0', 1)
                """,
                TEMPLATE_ITEM_ID,
                TEMPLATE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO listing_checklist_item (
                    listing_id, template_item_id, item_code, name, capture_guide,
                    evidence_type, automation_type, is_required, visible_to_buyer,
                    privacy_masking_required, completion_status, display_order
                ) VALUES (?, ?, 'BASIC-01', '외관', '전체 촬영',
                          'PHOTO', 'NONE', b'1', b'1', b'0', 'COMPLETED', 1)
                """,
                ACTIVE_LISTING_ID,
                TEMPLATE_ITEM_ID);
        jdbcTemplate.update(
                """
                INSERT INTO listing_image (
                    listing_id, image_type, s3_key, cdn_url, mime_type, created_at
                ) VALUES (?, 'THUMBNAIL', 'active.jpg',
                          'https://cdn.example.com/active.jpg', 'image/jpeg', CURRENT_TIMESTAMP(6))
                """,
                ACTIVE_LISTING_ID);
    }

    private void insertCategory() {
        jdbcTemplate.update(
                """
                INSERT INTO category (
                    id, parent_id, name, device_type, manufacturer, os_family,
                    manufacturer_id, model_code, supported_storage_gb, display_order, is_active
                ) VALUES (?, NULL, 'Galaxy S24', 'SMARTPHONE', 'Samsung', 'ANDROID',
                          CRC32('samsung'), 'SM-S921N', '128,256,512', 1, b'1')
                """,
                CATEGORY_ID);
    }

    private void insertCatalogTree() {
        jdbcTemplate.update(
                """
                INSERT INTO category (
                    id, parent_id, name, device_type, display_order, is_active
                ) VALUES (?, NULL, 'Smartphone', 'SMARTPHONE', 10, b'1')
                """,
                PARENT_CATEGORY_ID);
        jdbcTemplate.update(
                """
                INSERT INTO category (
                    id, parent_id, name, device_type, manufacturer, manufacturer_id,
                    os_family, model_code, supported_storage_gb, display_order, is_active
                ) VALUES (?, ?, ?, 'SMARTPHONE', 'Samsung', CRC32('samsung'),
                          'ANDROID', ?, '128,256', ?, b'1')
                """,
                CHILD_CATEGORY_ID,
                PARENT_CATEGORY_ID,
                "Galaxy S23",
                "SM-S911N",
                1);
        jdbcTemplate.update(
                """
                INSERT INTO category (
                    id, parent_id, name, device_type, manufacturer, manufacturer_id,
                    os_family, model_code, supported_storage_gb, display_order, is_active
                ) VALUES (?, ?, ?, 'SMARTPHONE', 'Samsung', CRC32('samsung'),
                          'ANDROID', ?, '256,512', ?, b'1')
                """,
                SECOND_CHILD_CATEGORY_ID,
                PARENT_CATEGORY_ID,
                "Galaxy S24 Plus",
                "SM-S926N",
                2);
    }

    private void insertListing(long listingId, String deletedAtExpression) {
        String deletedAt = deletedAtExpression == null ? "NULL" : deletedAtExpression;
        jdbcTemplate.update(
                """
                INSERT INTO listing (
                    id, seller_id, category_id, title, description, price,
                    checklist_template_id, precheck_completed, status, version,
                    deleted_at, created_at, updated_at
                ) VALUES (?, 98001, ?, 'Galaxy S24', '상태 양호', 650000,
                          99001, b'1', 'ON_SALE', 0, %s,
                          CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """
                        .formatted(deletedAt),
                listingId,
                CATEGORY_ID);
    }

    private void insertWishlist(long memberId, long listingId) {
        jdbcTemplate.update(
                """
                INSERT INTO wishlist (user_id, listing_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP(6))
                """,
                memberId,
                listingId);
    }
}
