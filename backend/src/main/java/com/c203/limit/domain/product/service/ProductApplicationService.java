package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.checklist.GeneratedChecklist;
import com.c203.limit.domain.inspection.checklist.GeneratedChecklistItem;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistCountProjection;
import com.c203.limit.domain.product.dto.request.CreateProductRequest;
import com.c203.limit.domain.product.dto.request.TransitionProductStatusRequest;
import com.c203.limit.domain.product.dto.request.UpdateProductRequest;
import com.c203.limit.domain.product.dto.response.ChecklistSummaryResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceInfoResponse;
import com.c203.limit.domain.product.dto.response.MyProductSummaryResponse;
import com.c203.limit.domain.product.dto.response.ProductCreatedResponse;
import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.dto.response.ProductStatusTransitionResponse;
import com.c203.limit.domain.product.dto.response.ProductSummaryResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingImageType;
import com.c203.limit.domain.product.entity.ListingSpecSnapshot;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.ListingStatusHistory;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.ListingThumbnailProjection;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ProductApplicationService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId PRODUCT_TIME_ZONE = ZoneId.of("Asia/Seoul");
    // 중복 조회 방지 전에는 조작 가능한 viewCount 정렬을 공개하지 않는다.
    private static final Set<String> PUBLIC_SORT_FIELDS = Set.of("createdAt", "price");
    private static final Set<String> MY_SORT_FIELDS = Set.of("updatedAt", "createdAt", "price");

    private final ListingRepository listingRepository;
    private final CategoryRepository categoryRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final ListingChecklistItemRepository checklistItemRepository;
    private final ListingStatusHistoryRepository statusHistoryRepository;
    private final ListingImageRepository imageRepository;
    private final MediaUrlResolver mediaUrlResolver;

    private final ProductViewCountDispatcher viewCountDispatcher;

    public ProductApplicationService(
            ListingRepository listingRepository,
            CategoryRepository categoryRepository,
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            ListingChecklistItemRepository checklistItemRepository,
            ListingStatusHistoryRepository statusHistoryRepository,
            ListingImageRepository imageRepository,
            MediaUrlResolver mediaUrlResolver,
            ProductViewCountDispatcher viewCountDispatcher) {
        this.viewCountDispatcher = viewCountDispatcher;
        this.listingRepository = listingRepository;
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.imageRepository = imageRepository;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @Transactional
    public ProductCreatedResponse create(Long sellerId, CreateProductRequest request) {
        return create(sellerId, request, null);
    }

    @Transactional
    public ProductCreatedResponse create(
            Long sellerId, CreateProductRequest request, GeneratedChecklist generatedChecklist) {
        Category model = categoryRepository
                .findById(request.getDeviceModelId())
                .filter(Category::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        validateModelCategory(model, request.getCategoryId());
        ChecklistTemplate template;
        List<ChecklistTemplateItem> templateItems;
        if (generatedChecklist == null) {
            template = templateRepository
                    .findFirstByCategoryIdAndStatusOrderByVersionDesc(
                            model.getId(), ChecklistTemplateStatus.PUBLISHED)
                    .orElseThrow(
                            () -> new BusinessException(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
            templateItems = templateItemRepository
                    .findByChecklistTemplateIdOrderByDisplayOrderAsc(template.getId());
        } else {
            validateGeneratedChecklist(model, generatedChecklist);
            template = templateRepository.saveAndFlush(ChecklistTemplate.createDraft(
                    model.getId(), generatedChecklist.templateVersion()));
            List<ChecklistTemplateItem> generatedItems = generatedChecklist.items().stream()
                    .map(item -> generatedTemplateItem(template, item))
                    .toList();
            templateItems = templateItemRepository.saveAllAndFlush(generatedItems);
        }
        Listing draft = Listing.createDraft(
                sellerId,
                model,
                request.getName(),
                request.getDescription(),
                price(request.getPrice()),
                request.getColor(),
                request.getStorageGb(),
                request.getTradeRegion(),
                template.getId());
        // '기타 (직접 입력)' 모델 한 행에 여러 기기가 매달리므로 실제 제조사·모델명은 매물에 남긴다.
        draft.applyCustomModel(request.getCustomManufacturer(), request.getCustomModelName());
        // 카탈로그 참조와 등록 시점 사양을 확정한다. device_model.model_id는 이관 시 리프
        // category.id를 그대로 물려받았으므로 model.getId()가 곧 모델 참조다. variant 선택은
        // 등록 화면 개편(4단계) 전까지 들어오지 않아 아직 비워 둔다.
        draft.applyCatalogSelection(
                model.getId(),
                null,
                ListingSpecSnapshot.of(
                        request.getCustomManufacturer() == null
                                ? model.getManufacturer()
                                : request.getCustomManufacturer(),
                        request.getCustomModelName() == null
                                ? model.getName()
                                : request.getCustomModelName(),
                        model.getModelCode(),
                        request.getColor(),
                        request.getStorageGb(),
                        null,
                        null,
                        null,
                        null,
                        null));
        Listing listing = listingRepository.saveAndFlush(draft);
        List<ListingChecklistItem> snapshots = templateItems.stream()
                .map(item -> ListingChecklistItem.createFromTemplateItem(listing.getId(), item))
                .toList();
        checklistItemRepository.saveAll(snapshots);
        int required = (int) snapshots.stream().filter(ListingChecklistItem::isRequired).count();
        log.info(
                "product draft created: productId={}, modelId={}, checklistVersion={}",
                listing.getId(),
                model.getId(),
                template.getVersion());
        return new ProductCreatedResponse(
                listing.getId(),
                listing.getStatus().name(),
                model.getId(),
                template.getVersion(),
                required,
                0,
                offset(listing.getCreatedAt()));
    }

    private void validateGeneratedChecklist(
            Category model, GeneratedChecklist generatedChecklist) {
        if (!model.getId().equals(generatedChecklist.deviceModelId())
                || generatedChecklist.items().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ChecklistTemplateItem generatedTemplateItem(
            ChecklistTemplate template, GeneratedChecklistItem item) {
        return ChecklistTemplateItem.createGenerated(
                template,
                item.itemCode(),
                item.name(),
                item.purpose(),
                item.guide(),
                item.evidenceType(),
                item.automationType(),
                item.parserType(),
                item.required(),
                item.displayOrder());
    }

    @Transactional
    public ProductDetailResponse update(Long sellerId, Long productId, UpdateProductRequest request) {
        Listing listing = owned(productId, sellerId);
        listing.updateBySeller(
                request.getName(),
                request.getDescription(),
                request.isDescriptionSpecified(),
                request.getPrice() == null ? null : price(request.getPrice()),
                request.getColor(),
                request.isColorSpecified(),
                request.getStorageGb(),
                request.isStorageGbSpecified(),
                request.getTradeRegion());
        // 직접 입력 모델은 오타를 고칠 수 있어야 한다. 값을 보내지 않으면 기존 값을 유지한다.
        if (request.getCustomManufacturer() != null || request.getCustomModelName() != null) {
            listing.applyCustomModel(
                    request.getCustomManufacturer() != null
                            ? request.getCustomManufacturer()
                            : listing.getCustomManufacturer(),
                    request.getCustomModelName() != null
                            ? request.getCustomModelName()
                            : listing.getCustomModelName());
        }
        log.info(
                "product updated by seller: productId={}, status={}",
                productId,
                listing.getStatus());
        return detail(listing);
    }

    @Transactional
    public void delete(Long sellerId, Long productId) {
        Listing listing = owned(productId, sellerId);
        if (!List.of(ListingStatus.DRAFT, ListingStatus.ON_SALE, ListingStatus.HIDDEN)
                .contains(listing.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_DELETE_NOT_ALLOWED);
        }
        ListingStatus previous = listing.getStatus();
        listing.softDelete();
        log.info(
                "product soft deleted: productId={}, previousStatus={}",
                productId,
                previous);
    }

    @Transactional(readOnly = true)
    public ProductPage findPublic(
            String keyword,
            Long categoryId,
            Long manufacturerId,
            Long deviceModelId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String tradeRegion,
            String verificationStatus,
            Integer minVerifiedCount,
            Integer maxVerifiedCount,
            Long sellerId,
            int page,
            int size,
            String sort) {
        validatePage(page, size);
        Specification<Listing> spec = Specification.where(notDeleted())
                .and(hasStatus(ListingStatus.ON_SALE))
                .and(keyword(keyword))
                .and(category(categoryId, deviceModelId))
                .and(manufacturer(manufacturerId))
                .and(seller(sellerId))
                .and(priceRange(minPrice, maxPrice))
                .and(tradeRegion(tradeRegion))
                .and(verificationStatus(verificationStatus))
                .and(verifiedCountBetween(minVerifiedCount, maxVerifiedCount));
        Page<Listing> result = listingRepository.findAll(
                spec, PageRequest.of(page, size, sort(sort, "createdAt", PUBLIC_SORT_FIELDS)));
        Map<Long, ProductMetrics> metrics = loadMetrics(result.getContent());
        return new ProductPage(
                result.getContent().stream()
                        .map(listing -> summary(listing, metrics.get(listing.getId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    /** 공개 상세 조회가 정상 구성된 뒤 조회수 집계를 비동기로 예약한다. */
    @Transactional(readOnly = true)
    public ProductDetailResponse findPublicDetail(Long productId) {
        ProductDetailResponse response = detail(listingRepository
                .findByIdAndStatusAndDeletedAtIsNull(productId, ListingStatus.ON_SALE)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND)));
        try {
            viewCountDispatcher.dispatch(productId);
        } catch (TaskRejectedException exception) {
            log.warn("product view count task rejected: productId={}", productId);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse findOwnedDetail(Long sellerId, Long productId) {
        return detail(owned(productId, sellerId));
    }

    @Transactional(readOnly = true)
    public MyProductPage findMine(Long sellerId, String status, int page, int size, String sort) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(page, size, sort(sort, "updatedAt", MY_SORT_FIELDS));
        Page<Listing> result;
        try {
            result = status == null
                    ? listingRepository.findBySellerIdAndDeletedAtIsNull(sellerId, pageable)
                    : listingRepository.findBySellerIdAndStatusAndDeletedAtIsNull(
                            sellerId, ListingStatus.valueOf(status), pageable);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Map<Long, ProductMetrics> metrics = loadMetrics(result.getContent());
        List<MyProductSummaryResponse> content = result.getContent().stream()
                .map(
                        listing -> {
                            ProductMetrics itemMetrics = metrics.get(listing.getId());
                            // 판매자도 목록에서 어떤 기기인지 알아볼 수 있어야 한다. 공개 목록과
                            // 같은 값을 같은 방식으로 채운다.
                            Category model = listing.getCategory();
                            return
                                new MyProductSummaryResponse(
                                        listing.getId(),
                                        listing.getTitle(),
                                        listing.getStatus().name(),
                                        displayManufacturer(listing, model),
                                        displayModelName(listing, model),
                                        BigDecimal.valueOf(listing.getPrice()),
                                        itemMetrics.thumbnailUrl(),
                                        itemMetrics.completedRequired(),
                                        itemMetrics.required(),
                                        itemMetrics.hasPendingConfirmation(),
                                        offset(listing.getUpdatedAt()));
                        })
                .toList();
        return new MyProductPage(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @Transactional
    public ProductStatusTransitionResponse transition(
            Long sellerId, Long productId, TransitionProductStatusRequest request) {
        Listing listing = owned(productId, sellerId);
        ListingStatus previous = listing.getStatus();
        ListingStatus target;
        try {
            target = ListingStatus.valueOf(request.getTargetStatus());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION);
        }
        if (target == ListingStatus.ON_SALE && previous == ListingStatus.SOLD) {
            // 직거래가 깨졌을 때 원래 판매글로 되돌아가는 경로다. publish는 DRAFT만 허용하므로 따로 다룬다.
            listing.reopenSoldBySeller();
        } else if (target == ListingStatus.ON_SALE) {
            // 판매 시작은 체크리스트 완료 여부로 막지 않는다. 등록을 마친 판매자가 다시 '판매 시작'을
            // 눌러야 하는 흐름을 없애기 위한 정책이며, 남은 항목은 상세의 검증 진행률로 구매자에게 드러난다.
            listing.completePrecheck();
            listing.publish();
        } else if (target == ListingStatus.HIDDEN) {
            listing.hide();
        } else if (target == ListingStatus.SOLD) {
            // 직거래로 팔린 매물을 판매자가 직접 닫는 경로다. 결제 흐름을 거치지 않는다.
            listing.markSoldBySeller();
        } else {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION);
        }
        ListingStatusHistory history = statusHistoryRepository.saveAndFlush(
                ListingStatusHistory.record(
                        listing, previous, listing.getStatus(), request.getReason(), sellerId));
        log.info(
                "product status changed: productId={}, from={}, to={}",
                productId,
                previous,
                listing.getStatus());
        return new ProductStatusTransitionResponse(
                history.getId(),
                listing.getId(),
                previous.name(),
                listing.getStatus().name(),
                request.getReason(),
                offset(history.getCreatedAt()));
    }

    /**
     * 판매자가 직접 입력한 값이 있으면 그것을 보여준다. 카탈로그의 '기타 (직접 입력)' 행은 여러 기기가
     * 공유하는 자리표시자여서 그 이름을 그대로 노출하면 구매자가 기기를 특정할 수 없다.
     */
    private String displayManufacturer(Listing listing, Category model) {
        return listing.getCustomManufacturer() != null
                ? listing.getCustomManufacturer()
                : model.getManufacturer();
    }

    private String displayModelName(Listing listing, Category model) {
        return listing.getCustomModelName() != null
                ? listing.getCustomModelName()
                : model.getName();
    }

    private Listing owned(Long productId, Long sellerId) {
        return listingRepository
                .findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseGet(
                        () -> {
                            if (listingRepository.findByIdAndDeletedAtIsNull(productId).isPresent()) {
                                throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
                            }
                            throw new BusinessException(ErrorCode.LISTING_NOT_FOUND);
                        });
    }

    private ProductDetailResponse detail(Listing listing) {
        Category model = listing.getCategory();
        Category parent = model.getParent();
        ProductMetrics metrics = loadMetrics(List.of(listing)).get(listing.getId());
        return new ProductDetailResponse(
                listing.getId(),
                listing.getSellerId(),
                categoryResponse(parent == null ? model : parent),
                new DeviceInfoResponse(
                        model.getId(),
                        displayManufacturer(listing, model),
                        displayModelName(listing, model),
                        model.getOsFamily() == null ? null : model.getOsFamily().name(),
                        listing.getColor(),
                        listing.getStorageGb()),
                listing.getTitle(),
                listing.getDescription(),
                BigDecimal.valueOf(listing.getPrice()),
                listing.getStatus().name(),
                listing.getTradeRegion(),
                checklistSummary(metrics),
                metrics.thumbnailUrl(),
                offset(listing.getCreatedAt()),
                offset(listing.getUpdatedAt()));
    }

    private ProductSummaryResponse summary(Listing listing, ProductMetrics metrics) {
        int required = metrics.required();
        int completed = metrics.completedRequired();
        String verification = required > 0 && required == completed ? "COMPLETED" : "IN_PROGRESS";
        return new ProductSummaryResponse(
                listing.getId(),
                listing.getTitle(),
                displayManufacturer(listing, listing.getCategory()),
                displayModelName(listing, listing.getCategory()),
                BigDecimal.valueOf(listing.getPrice()),
                listing.getStatus().name(),
                verification,
                metrics.completedRequired(),
                metrics.required(),
                metrics.thumbnailUrl(),
                listing.getTradeRegion(),
                listing.getViewCount());
    }

    private ChecklistSummaryResponse checklistSummary(ProductMetrics metrics) {
        return new ChecklistSummaryResponse(metrics.required(), metrics.completedRequired(), 0);
    }

    private int required(Long listingId) {
        return Math.toIntExact(checklistItemRepository.countByListingIdAndIsRequiredTrue(listingId));
    }

    private int completedRequired(Long listingId) {
        return Math.toIntExact(
                checklistItemRepository.countByListingIdAndIsRequiredTrueAndCompletionStatus(
                        listingId, ChecklistItemCompletionStatus.COMPLETED));
    }


    private Map<Long, ProductMetrics> loadMetrics(List<Listing> listings) {
        if (listings.isEmpty()) return Map.of();
        List<Long> listingIds = listings.stream().map(Listing::getId).toList();
        Map<Long, ListingChecklistCountProjection> counts = checklistItemRepository
                .countRequiredByListingIds(
                        listingIds,
                        ChecklistItemCompletionStatus.COMPLETED,
                        EvidenceType.SELLER_CONFIRMATION)
                .stream()
                .collect(Collectors.toMap(ListingChecklistCountProjection::getListingId, Function.identity()));
        Map<Long, String> thumbnails = imageRepository
                .findFirstByListingIdsAndImageType(listingIds, ListingImageType.THUMBNAIL)
                .stream()
                .collect(Collectors.toMap(
                        ListingThumbnailProjection::getListingId,
                        image -> mediaUrlResolver == null
                                ? image.getCdnUrl()
                                : mediaUrlResolver.resolve(image.getS3Key(), image.getCdnUrl())));
        Map<Long, ProductMetrics> result = new HashMap<>();
        listingIds.forEach(listingId -> {
            ListingChecklistCountProjection count = counts.get(listingId);
            int required = count == null ? 0 : Math.toIntExact(count.getRequiredCount());
            int completed = count == null ? 0 : Math.toIntExact(count.getCompletedRequiredCount());
            int requiredConfirmation =
                    count == null ? 0 : Math.toIntExact(count.getRequiredConfirmationCount());
            int completedConfirmation =
                    count == null ? 0 : Math.toIntExact(count.getCompletedConfirmationCount());
            result.put(
                    listingId,
                    new ProductMetrics(
                            required,
                            completed,
                            requiredConfirmation,
                            completedConfirmation,
                            thumbnails.get(listingId)));
        });
        return result;
    }

    private DeviceCategoryResponse categoryResponse(Category category) {
        return new DeviceCategoryResponse(
                category.getId(),
                category.getDeviceType().name(),
                category.getName(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.isActive());
    }

    private void validateModelCategory(Category model, Long categoryId) {
        Long actual = model.getParent() == null ? model.getId() : model.getParent().getId();
        if (!actual.equals(categoryId)) {
            throw new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND);
        }
    }

    private long price(BigDecimal price) {
        try {
            long value = price.longValueExact();
            if (value < 1 || value > 999_999_999_999L) {
                throw new ArithmeticException("price out of contract");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(PRODUCT_TIME_ZONE).toOffsetDateTime();
    }

    private Specification<Listing> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private Specification<Listing> hasStatus(ListingStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private Specification<Listing> keyword(String value) {
        return (root, query, cb) -> value == null || value.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("title")), "%" + value.toLowerCase() + "%");
    }

    private Specification<Listing> category(Long categoryId, Long modelId) {
        return (root, query, cb) -> {
            if (modelId != null) return cb.equal(root.get("category").get("id"), modelId);
            if (categoryId == null) return cb.conjunction();
            return cb.or(
                    cb.equal(root.get("category").get("id"), categoryId),
                    cb.equal(root.get("category").get("parent").get("id"), categoryId));
        };
    }

    /** 판매자 공개 프로필에서 그 사람의 판매 목록만 보여 줄 때 쓴다. */
    private Specification<Listing> seller(Long sellerId) {
        return (root, query, cb) -> sellerId == null
                ? cb.conjunction()
                : cb.equal(root.get("sellerId"), sellerId);
    }

    private Specification<Listing> manufacturer(Long manufacturerId) {
        return (root, query, cb) -> manufacturerId == null
                ? cb.conjunction()
                : cb.equal(root.get("category").get("manufacturerId"), manufacturerId);
    }

    /**
     * 완료한 필수 검증 항목 개수로 거른다.
     *
     * <p>목록의 '검증 개수' 필터가 쓰는 조건이다. 예전에는 개수 조건이 없어서 프런트가 구간
     * 선택(1~3개, 4~6개 …)을 COMPLETED/IN_PROGRESS 둘로 뭉개 보냈고, 그래서 고른 구간과 결과가
     * 맞지 않았다.
     */
    private Specification<Listing> verifiedCountBetween(Integer min, Integer max) {
        if (min == null && max == null) return (root, query, cb) -> cb.conjunction();
        if (min != null && min < 0) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (min != null && max != null && min > max) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return (root, query, cb) -> {
            var completedQuery = query.subquery(Long.class);
            var completedItem = completedQuery.from(ListingChecklistItem.class);
            completedQuery.select(cb.count(completedItem)).where(
                    cb.equal(completedItem.get("listingId"), root.get("id")),
                    cb.isTrue(completedItem.get("isRequired")),
                    cb.equal(
                            completedItem.get("completionStatus"),
                            ChecklistItemCompletionStatus.COMPLETED));
            var predicate = cb.conjunction();
            if (min != null) {
                predicate = cb.and(
                        predicate, cb.greaterThanOrEqualTo(completedQuery, min.longValue()));
            }
            if (max != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(completedQuery, max.longValue()));
            }
            return predicate;
        };
    }

    private Specification<Listing> verificationStatus(String value) {
        if (value == null || value.isBlank()) return (root, query, cb) -> cb.conjunction();
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!Set.of("COMPLETED", "IN_PROGRESS").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return (root, query, cb) -> {
            var requiredQuery = query.subquery(Long.class);
            var requiredItem = requiredQuery.from(ListingChecklistItem.class);
            requiredQuery.select(cb.count(requiredItem)).where(
                    cb.equal(requiredItem.get("listingId"), root.get("id")),
                    cb.isTrue(requiredItem.get("isRequired")));

            var incompleteQuery = query.subquery(Long.class);
            var incompleteItem = incompleteQuery.from(ListingChecklistItem.class);
            incompleteQuery.select(cb.count(incompleteItem)).where(
                    cb.equal(incompleteItem.get("listingId"), root.get("id")),
                    cb.isTrue(incompleteItem.get("isRequired")),
                    cb.notEqual(
                            incompleteItem.get("completionStatus"),
                            ChecklistItemCompletionStatus.COMPLETED));
            if ("COMPLETED".equals(normalized)) {
                return cb.and(cb.greaterThan(requiredQuery, 0L), cb.equal(incompleteQuery, 0L));
            }
            return cb.or(cb.equal(requiredQuery, 0L), cb.greaterThan(incompleteQuery, 0L));
        };
    }

    private Specification<Listing> priceRange(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("price"), price(min), price(max));
            }
            if (min != null) return cb.greaterThanOrEqualTo(root.get("price"), price(min));
            if (max != null) return cb.lessThanOrEqualTo(root.get("price"), price(max));
            return cb.conjunction();
        };
    }

    private Specification<Listing> tradeRegion(String value) {
        return (root, query, cb) -> value == null || value.isBlank()
                ? cb.conjunction()
                : cb.like(root.get("tradeRegion"), "%" + value + "%");
    }

    private Sort sort(String value, String defaultField, Set<String> allowedFields) {
        String effective = value == null || value.isBlank() ? defaultField + ",desc" : value;
        String[] parts = effective.split(",");
        if (parts.length > 2 || !allowedFields.contains(parts[0])) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 1
                    ? Sort.Direction.ASC
                    : Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return Sort.by(direction, parts[0]);
    }

    private record ProductMetrics(
            int required,
            int completedRequired,
            int requiredConfirmation,
            int completedConfirmation,
            String thumbnailUrl) {

        /** 개인정보 정리 확인이 남아 있는지. 판매 시작 전에 반드시 끝나야 하는 항목이다. */
        boolean hasPendingConfirmation() {
            return completedConfirmation < requiredConfirmation;
        }
    }

    public record ProductPage(
            List<ProductSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    public record MyProductPage(
            List<MyProductSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
