package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.testsupport.AbstractMySqlIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인된 모델로 상품을 등록할 수 있는지 확인한다.
 *
 * <p>이 테스트가 지키는 것은 <b>{@code device_model.model_id == category.id}</b>라는 계약이다.
 * 상품 등록은 {@code listing.device_model_id}에 리프 {@code category.id}를 넣고, 그 열에는
 * {@code device_model}을 가리키는 FK가 걸려 있다. 승인 시 {@code category}에만 쓰면 그 모델로
 * 등록하는 순간 FK 위반으로 실패한다 — 관리자는 승인했는데 아무도 쓸 수 없는 모델이 된다.
 */
@SpringBootTest(
        properties =
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration")
class DeviceCatalogRegistrarIntegrationTests extends AbstractMySqlIntegrationTest {

    @Autowired DeviceCatalogRegistrar registrar;
    @Autowired CategoryRepository categoryRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void registersApprovedModelIntoTheNewCatalogWithTheSameId() {
        Category leaf = approveLeaf("SM-TEST-01", "Galaxy Test 1");

        registrar.register(leaf);

        Long modelId = jdbcTemplate.queryForObject(
                "SELECT model_id FROM device_model WHERE model_code = ?",
                Long.class,
                "SM-TEST-01");
        assertThat(modelId).isEqualTo(leaf.getId());
    }

    /** 조합이 없는 모델은 검색에서 제외되므로 승인된 모델이 목록에서 사라진다. */
    @Test
    @Transactional
    void givesTheApprovedModelASelectableVariant() {
        Category leaf = approveLeaf("SM-TEST-02", "Galaxy Test 2");

        registrar.register(leaf);

        List<String> keys = jdbcTemplate.queryForList(
                "SELECT variant_key FROM device_variant WHERE model_id = ?",
                String.class,
                leaf.getId());
        assertThat(keys).containsExactly("SM-TEST-02-BASE");
    }

    /** 이 테스트가 실패하면 승인된 모델로 상품을 등록할 수 없다는 뜻이다. */
    @Test
    @Transactional
    void allowsRegisteringAListingWithTheApprovedModel() {
        Category leaf = approveLeaf("SM-TEST-03", "Galaxy Test 3");
        registrar.register(leaf);

        insertListing(leaf.getId());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM listing WHERE device_model_id = ?",
                Long.class,
                leaf.getId());
        assertThat(count).isEqualTo(1L);
    }

    /** 반대 방향 확인 — 카탈로그 반영을 건너뛰면 실제로 FK에서 막힌다. */
    @Test
    @Transactional
    void rejectsListingWhenTheModelWasNotRegisteredIntoTheCatalog() {
        Category leaf = approveLeaf("SM-TEST-04", "Galaxy Test 4");

        assertThatThrownBy(() -> insertListing(leaf.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Category approveLeaf(String modelCode, String modelName) {
        Category parent = categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .filter(category -> category.getDeviceType() == DeviceType.SMARTPHONE)
                .findFirst()
                .orElseThrow();
        return categoryRepository.saveAndFlush(
                Category.createLeaf(
                        parent,
                        modelName,
                        parent.getDeviceType(),
                        "Samsung",
                        OsFamily.ANDROID,
                        modelCode,
                        List.of(),
                        90));
    }

    private void insertListing(Long deviceModelId) {
        jdbcTemplate.update(
                """
                INSERT INTO listing (
                    seller_id, category_id, device_model_id, title, price,
                    checklist_template_id, precheck_completed, draft_step, status,
                    version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, b'0', 1, 'DRAFT', 0,
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                1L,
                deviceModelId,
                deviceModelId,
                "카탈로그 동기화 검증용 매물",
                100000L,
                1L);
    }
}
