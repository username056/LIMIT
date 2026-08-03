package com.c203.limit.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.repository.ModelChecklistResearchRepository;
import com.c203.limit.domain.product.entity.Manufacturer;
import com.c203.limit.testsupport.AbstractMySqlIntegrationTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 이관 마이그레이션(V20260812~V20260814)이 실제 MySQL에서 의도대로 도는지 검증한다.
 *
 * <p>이 마이그레이션의 위험은 문법 오류가 아니라 <b>조용한 데이터 손실</b>이다. 리프 판별 조건이
 * 하나라도 어긋나면 일부 모델이 새 카탈로그에 아예 넘어오지 않고, 그 상태로 배포되면 해당 모델의
 * 상품 등록 경로가 사라진다. 개수와 ID 일치를 직접 확인한다.
 */
@SpringBootTest(
        properties =
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration")
class DeviceCatalogMigrationIntegrationTests extends AbstractMySqlIntegrationTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ModelChecklistResearchRepository researchRepository;

    @Test
    void createsCatalogTables() {
        assertThat(tableExists("device_category")).isTrue();
        assertThat(tableExists("manufacturer")).isTrue();
        assertThat(tableExists("device_model")).isTrue();
        assertThat(tableExists("device_variant")).isTrue();
    }

    /** 최상위 카테고리는 하나도 빠지지 않고, id를 그대로 물려받아야 한다. */
    @Test
    void migratesEveryTopLevelCategoryPreservingId() {
        assertThat(count("SELECT COUNT(*) FROM category WHERE parent_id IS NULL"))
                .isEqualTo(count("SELECT COUNT(*) FROM device_category"));
        assertThat(
                        count(
                                """
                                SELECT COUNT(*) FROM category c
                                 WHERE c.parent_id IS NULL
                                   AND NOT EXISTS (
                                       SELECT 1 FROM device_category d
                                        WHERE d.category_id = c.id AND d.code = c.device_type)
                                """))
                .isZero();
    }

    /**
     * 리프(= 기기 모델) 역시 id 보존이 전제다. 프론트가 쓰는 deviceModelId와 이미 저장된
     * listing.category_id가 전부 이 값이다.
     */
    @Test
    void migratesEveryLeafModelPreservingId() {
        long leafCount =
                count("SELECT COUNT(*) FROM category WHERE parent_id IS NOT NULL AND model_code IS NOT NULL");

        assertThat(leafCount).isPositive();
        assertThat(count("SELECT COUNT(*) FROM device_model")).isEqualTo(leafCount);
        assertThat(
                        count(
                                """
                                SELECT COUNT(*) FROM category c
                                 WHERE c.parent_id IS NOT NULL
                                   AND c.model_code IS NOT NULL
                                   AND NOT EXISTS (
                                       SELECT 1 FROM device_model m
                                        WHERE m.model_id = c.id
                                          AND m.category_id = c.parent_id
                                          AND m.model_code = c.model_code)
                                """))
                .isZero();
    }

    /** SQL의 CRC32와 {@link Manufacturer#idOf}가 같은 값을 내야 제조사 필터가 양쪽에서 통한다. */
    @Test
    void derivesManufacturerIdConsistentlyWithApplicationCode() {
        List<String> names =
                jdbcTemplate.queryForList("SELECT name FROM manufacturer", String.class);

        assertThat(names).isNotEmpty();
        for (String name : names) {
            Long stored =
                    jdbcTemplate.queryForObject(
                            "SELECT manufacturer_id FROM manufacturer WHERE name = ?",
                            Long.class,
                            name);
            assertThat(stored).isEqualTo(Manufacturer.idOf(name));
        }
    }

    /**
     * variant가 하나도 없는 모델은 등록 화면에서 고를 조합이 없어 검색 결과에서 제외된다.
     * '기타 (직접 입력)' 모델까지 사라지면 카탈로그에 없는 기기의 등록 경로가 통째로 막힌다.
     */
    @Test
    void givesEveryModelAtLeastOneSelectableVariant() {
        assertThat(
                        count(
                                """
                                SELECT COUNT(*) FROM device_model m
                                 WHERE NOT EXISTS (
                                     SELECT 1 FROM device_variant v
                                      WHERE v.model_id = m.model_id AND v.is_active = b'1')
                                """))
                .isZero();
    }

    /** "128,256,512" 콤마 문자열이 용량별 조합으로 정확히 전개돼야 한다. */
    @Test
    void expandsCommaSeparatedStorageIntoVariants() {
        Long modelId =
                jdbcTemplate.queryForObject(
                        "SELECT model_id FROM device_model WHERE model_code = ?",
                        Long.class,
                        "SM-S921N");

        List<Integer> storages =
                jdbcTemplate.queryForList(
                        "SELECT storage_gb FROM device_variant WHERE model_id = ? ORDER BY storage_gb",
                        Integer.class,
                        modelId);

        assertThat(storages).containsExactly(128, 256, 512);
    }

    /** 용량 정보가 없는 모델에는 기본 조합 하나가 만들어진다. */
    @Test
    void createsBaseVariantForModelsWithoutStorageList() {
        Long modelId =
                jdbcTemplate.queryForObject(
                        "SELECT model_id FROM device_model WHERE model_code = ?",
                        Long.class,
                        "ETC-SMARTPHONE");

        List<String> keys =
                jdbcTemplate.queryForList(
                        "SELECT variant_key FROM device_variant WHERE model_id = ?",
                        String.class,
                        modelId);

        assertThat(keys).containsExactly("ETC-SMARTPHONE-BASE");
    }

    @Test
    void addsListingCatalogColumns() {
        assertThat(columnExists("listing", "device_model_id")).isTrue();
        assertThat(columnExists("listing", "device_variant_id")).isTrue();
        assertThat(columnExists("listing", "spec_snapshot")).isTrue();
        assertThat(columnExists("listing", "view_count")).isTrue();
        assertThat(columnExists("listing", "screen_size_inches")).isTrue();
        assertThat(columnExists("listing", "memory_gb")).isTrue();
        assertThat(columnExists("listing", "connectivity")).isTrue();
    }

    @Test
    void addsReversibleModelDeactivationAuditAndSearchIndexes() {
        assertThat(columnExists("device_model", "disabled_at")).isTrue();
        assertThat(columnExists("device_model", "disabled_by_admin_id")).isTrue();
        assertThat(columnExists("device_model", "disable_reason")).isTrue();
        assertThat(columnExists("device_model", "replacement_model_id")).isTrue();
        assertThat(indexExists("listing", "idx_listing_model_updated")).isTrue();
        assertThat(indexExists("listing", "idx_listing_model_created")).isTrue();
        assertThat(indexExists("model_checklist_research", "idx_model_research_model_updated"))
                .isTrue();
    }

    @Test
    @Transactional
    void readsOnlyTheLatestResearchSummaryForEachModel() {
        Long modelId = jdbcTemplate.queryForObject(
                "SELECT MIN(model_id) FROM device_model", Long.class);
        Integer nextVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(research_version), 0) + 1 "
                        + "FROM model_checklist_research WHERE device_model_id = ?",
                Integer.class,
                modelId);
        ModelChecklistResearch previous =
                ModelChecklistResearch.start(modelId, nextVersion, "{}");
        previous.complete("{}");
        researchRepository.saveAndFlush(previous);
        ModelChecklistResearch latest =
                ModelChecklistResearch.start(modelId, nextVersion + 1, "{}");
        latest.fail("{}");
        researchRepository.saveAndFlush(latest);

        var summaries = researchRepository.findLatestSummaries(Set.of(modelId));

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.getDeviceModelId()).isEqualTo(modelId);
            assertThat(summary.getResearchVersion()).isEqualTo(nextVersion + 1);
            assertThat(summary.getStatus()).isEqualTo(ModelChecklistResearchStatus.FAILED);
        });
    }

    /** 기존 매물의 카탈로그 참조가 남김없이 backfill돼야 한다. */
    @Test
    void backfillsDeviceModelIdForExistingListings() {
        assertThat(
                        count(
                                """
                                SELECT COUNT(*) FROM listing l
                                 WHERE l.device_model_id IS NULL
                                   AND EXISTS (
                                       SELECT 1 FROM device_model m WHERE m.model_id = l.category_id)
                                """))
                .isZero();
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private boolean tableExists(String tableName) {
        return count(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = '"
                                + tableName
                                + "'")
                == 1;
    }

    private boolean columnExists(String tableName, String columnName) {
        return count(
                        "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = '"
                                + tableName
                                + "' AND column_name = '"
                                + columnName
                                + "'")
                == 1;
    }

    private boolean indexExists(String tableName, String indexName) {
        return count(
                        "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                                + "WHERE table_schema = DATABASE() AND table_name = '"
                                + tableName
                                + "' AND index_name = '"
                                + indexName
                                + "'")
                == 1;
    }
}
