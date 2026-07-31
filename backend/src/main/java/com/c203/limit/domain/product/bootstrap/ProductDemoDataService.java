package com.c203.limit.domain.product.bootstrap;

import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.product.dto.request.CreateProductRequest;
import com.c203.limit.domain.product.dto.request.TransitionProductStatusRequest;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.service.ProductApplicationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "limit.bootstrap.demo-data.enabled", havingValue = "true")
public class ProductDemoDataService {
    private static final Logger log = LoggerFactory.getLogger(ProductDemoDataService.class);
    private static final List<String> COLORS =
            List.of("미드나이트 블랙", "실버", "네이비", "화이트");
    private static final List<String> REGIONS =
            List.of("서울 강남구", "서울 송파구", "경기 성남시", "인천 연수구");

    private final CategoryRepository categoryRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ListingRepository listingRepository;
    private final ProductApplicationService productApplicationService;

    public ProductDemoDataService(
            CategoryRepository categoryRepository,
            ChecklistTemplateRepository templateRepository,
            ListingRepository listingRepository,
            ProductApplicationService productApplicationService) {
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.listingRepository = listingRepository;
        this.productApplicationService = productApplicationService;
    }

    @Transactional
    public int seedForSeller(Long sellerId, int userSequence, int productsPerUser) {
        if (productsPerUser == 0) {
            return 0;
        }
        List<Category> models =
                categoryRepository.findModels(null, null, null, PageRequest.of(0, 100)).stream()
                        .filter(
                                model ->
                                        templateRepository
                                                .findFirstByCategoryIdAndStatusOrderByVersionDesc(
                                                        model.getId(),
                                                        ChecklistTemplateStatus.PUBLISHED)
                                                .isPresent())
                        .toList();
        if (models.isEmpty()) {
            log.warn("Demo products skipped because no published checklist model exists");
            return 0;
        }

        int created = 0;
        for (int productSequence = 1;
                productSequence <= productsPerUser;
                productSequence++) {
            Category model = models.get((productSequence - 1) % models.size());
            String title =
                    "[데모 %02d-%02d] %s %s"
                            .formatted(
                                    userSequence,
                                    productSequence,
                                    valueOrDefault(model.getManufacturer(), "LIMIT"),
                                    model.getName());
            if (listingRepository.existsBySellerIdAndTitleAndDeletedAtIsNull(sellerId, title)) {
                continue;
            }

            var createdProduct =
                    productApplicationService.create(
                            sellerId,
                            new CreateProductRequest(
                                    model.getParent() == null
                                            ? model.getId()
                                            : model.getParent().getId(),
                                    model.getId(),
                                    title,
                                    "로컬 개발과 화면 확인을 위한 데모 상품입니다.",
                                    BigDecimal.valueOf(
                                            100_000L
                                                    + userSequence * 20_000L
                                                    + productSequence * 30_000L),
                                    COLORS.get((productSequence - 1) % COLORS.size()),
                                    256,
                                    REGIONS.get((userSequence + productSequence - 2) % REGIONS.size()),
                                    Set.of(),
                                    null,
                                    null,
                                    null,
                                    null));
            applyDemoStatus(sellerId, createdProduct.getProductId(), productSequence);
            created++;
        }
        return created;
    }

    private void applyDemoStatus(Long sellerId, Long productId, int productSequence) {
        if (productSequence % 4 == 0) {
            return;
        }
        productApplicationService.transition(
                sellerId,
                productId,
                new TransitionProductStatusRequest("ON_SALE", "데모 상품 판매 시작"));
        if (productSequence % 4 == 3) {
            productApplicationService.transition(
                    sellerId,
                    productId,
                    new TransitionProductStatusRequest("HIDDEN", "데모 숨김 상태"));
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
