package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceCategory;
import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.entity.DeviceVariant;
import com.c203.limit.domain.product.entity.Manufacturer;
import com.c203.limit.domain.product.repository.DeviceCategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelRepository;
import com.c203.limit.domain.product.repository.DeviceVariantRepository;
import com.c203.limit.domain.product.repository.ManufacturerRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code category} 리프로 등록된 기기 모델을 신규 카탈로그({@code device_model}/{@code
 * device_variant})에 함께 반영한다.
 *
 * <p>두 구조가 병행하는 동안 {@code category}에만 쓰면 그 모델로는 상품을 등록할 수 없다.
 * {@code listing.device_model_id}에 {@code listing.category_id}와 같은 값이 들어가고 FK가
 * {@code device_model}을 가리키기 때문이다. 즉 한쪽만 쓰는 것은 선택이 아니라 결함이다.
 *
 * <p>호출자의 트랜잭션에 참여한다({@link Propagation#MANDATORY}). 승인은 카탈로그 등록까지
 * 끝나야 완료된 것이고, 따로 커밋되면 어긋난 상태가 남는다.
 */
@Service
public class DeviceCatalogRegistrar {

    private static final Logger log = LoggerFactory.getLogger(DeviceCatalogRegistrar.class);

    private final DeviceCategoryRepository deviceCategoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final DeviceModelRepository modelRepository;
    private final DeviceVariantRepository variantRepository;

    public DeviceCatalogRegistrar(
            DeviceCategoryRepository deviceCategoryRepository,
            ManufacturerRepository manufacturerRepository,
            DeviceModelRepository modelRepository,
            DeviceVariantRepository variantRepository) {
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.manufacturerRepository = manufacturerRepository;
        this.modelRepository = modelRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * 리프 {@link Category}를 신규 카탈로그에 반영한다. 이미 반영돼 있으면 아무것도 하지 않는다.
     *
     * @param leaf 이미 저장돼 id가 확정된 리프 카테고리
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public DeviceModel register(Category leaf) {
        return register(leaf, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DeviceModel registerReported(Category leaf, Long memberId) {
        return register(leaf, memberId);
    }

    private DeviceModel register(Category leaf, Long reportedByMemberId) {
        if (leaf == null || leaf.getId() == null) {
            throw new IllegalArgumentException("leaf category must be persisted");
        }
        if (leaf.getParent() == null || leaf.getModelCode() == null) {
            throw new IllegalArgumentException("category is not a device model leaf");
        }
        Optional<DeviceModel> existing = modelRepository.findById(leaf.getId());
        if (existing.isPresent()) return existing.get();

        DeviceCategory category = deviceCategoryRepository
                .findById(leaf.getParent().getId())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "device category not migrated: categoryId="
                                                + leaf.getParent().getId()));
        Manufacturer manufacturer = manufacturer(leaf.getManufacturer());
        DeviceModel model = modelRepository.saveAndFlush(reportedByMemberId == null
                ? DeviceModel.create(
                        leaf.getId(),
                        category,
                        manufacturer,
                        leaf.getName(),
                        leaf.getModelCode(),
                        leaf.getOsFamily(),
                        null,
                        leaf.getDisplayOrder())
                : DeviceModel.createReported(
                        leaf.getId(),
                        category,
                        manufacturer,
                        leaf.getName(),
                        leaf.getModelCode(),
                        leaf.getOsFamily(),
                        leaf.getDisplayOrder(),
                        reportedByMemberId));

        // 조합이 하나도 없는 모델은 검색에서 제외된다(DeviceModelRepository.search). 사용자가
        // 요청해 승인까지 난 모델이 그 규칙에 걸려 사라지면 승인 자체가 무의미해진다.
        variantRepository.save(
                DeviceVariant.create(
                        model, model.getModelCode() + "-BASE", model.getModelName()));
        log.info(
                "device model registered in catalog: modelId={}, modelCode={}",
                model.getId(),
                model.getModelCode());
        return model;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DeviceModel update(Category leaf) {
        DeviceModel model = modelRepository
                .findByIdForUpdate(leaf.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "device model not registered: modelId=" + leaf.getId()));
        DeviceCategory category = deviceCategoryRepository
                .findById(leaf.getParent().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "device category not migrated: categoryId="
                                + leaf.getParent().getId()));
        model.updateCatalog(
                category,
                manufacturer(leaf.getManufacturer()),
                leaf.getName(),
                leaf.getModelCode(),
                leaf.getOsFamily());
        log.info("device model catalog updated: modelId={}", model.getId());
        return model;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeReview(Long modelId, Long adminId, String note) {
        DeviceModel model = modelRepository
                .findByIdForUpdate(modelId)
                .orElseThrow(() -> new IllegalStateException(
                        "device model not registered: modelId=" + modelId));
        model.completeReview(adminId, note);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deactivate(Long modelId) {
        modelRepository.findByIdForUpdate(modelId).ifPresent(DeviceModel::deactivate);
    }

    /**
     * 제조사는 정규화 이름 기준으로 재사용한다. 승인마다 새로 만들면 "Samsung"과 "samsung"이
     * 다른 제조사가 되어 필터가 갈라진다.
     */
    private Manufacturer manufacturer(String name) {
        Long id = Manufacturer.idOf(name);
        if (id == null) return null;
        Optional<Manufacturer> existing = manufacturerRepository.findById(id);
        if (existing.isPresent()) {
            if (!existing.get().hasSameNormalizedName(name)) {
                throw new IllegalStateException("manufacturer id collision: manufacturerId=" + id);
            }
            return existing.get();
        }
        return manufacturerRepository.saveAndFlush(Manufacturer.create(name));
    }
}
