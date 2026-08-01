package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.dto.response.DeviceModelOptionsResponse;
import com.c203.limit.domain.product.dto.response.DeviceVariantAxisResponse;
import com.c203.limit.domain.product.dto.response.DeviceVariantResponse;
import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.entity.DeviceVariant;
import com.c203.limit.domain.product.entity.DeviceVariantAxis;
import com.c203.limit.domain.product.repository.DeviceModelRepository;
import com.c203.limit.domain.product.repository.DeviceVariantRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록 화면의 단계별 옵션 조회.
 *
 * <p>모델 하나의 조합을 한 번에 읽어 메모리에서 좁힌다. 조합 수는 많아야 수백 개이고, 축마다
 * DISTINCT 쿼리를 던지면 축 개수만큼 왕복이 늘어난다.
 */
@Service
public class DeviceCatalogOptionService {

    private final DeviceModelRepository modelRepository;
    private final DeviceVariantRepository variantRepository;

    public DeviceCatalogOptionService(
            DeviceModelRepository modelRepository, DeviceVariantRepository variantRepository) {
        this.modelRepository = modelRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * @param selection 이미 선택된 축과 값. 값이 비어 있는 축은 선택되지 않은 것으로 본다.
     */
    @Transactional(readOnly = true)
    public DeviceModelOptionsResponse options(
            Long modelId, Map<DeviceVariantAxis, String> selection) {
        DeviceModel model = modelRepository
                .findWithCatalogById(modelId)
                .filter(DeviceModel::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        List<DeviceVariant> variants =
                variantRepository.findByModelIdAndIsActiveTrueOrderByIdAsc(modelId);

        Map<DeviceVariantAxis, String> effective = normalize(selection);
        validateSelection(variants, effective);

        List<DeviceVariant> matched = filter(variants, effective, null);
        List<DeviceVariantAxisResponse> axes = new ArrayList<>();
        for (DeviceVariantAxis axis : DeviceVariantAxis.values()) {
            if (!isPresent(variants, axis)) continue;
            axes.add(
                    new DeviceVariantAxisResponse(
                            axis.name(), values(filter(variants, effective, axis), axis),
                            effective.get(axis)));
        }
        return new DeviceModelOptionsResponse(
                model.getId(),
                model.getModelName(),
                model.getModelCode(),
                matched.size() == 1 ? matched.get(0).getId() : null,
                axes,
                matched.stream()
                        .map(
                                variant ->
                                        new DeviceVariantResponse(
                                                variant.getId(),
                                                variant.getVariantKey(),
                                                variant.getDisplayName()))
                        .toList());
    }

    private Map<DeviceVariantAxis, String> normalize(Map<DeviceVariantAxis, String> selection) {
        Map<DeviceVariantAxis, String> normalized = new EnumMap<>(DeviceVariantAxis.class);
        if (selection == null) return normalized;
        selection.forEach(
                (axis, value) -> {
                    if (axis != null && value != null && !value.isBlank()) {
                        normalized.put(axis, value.trim());
                    }
                });
        return normalized;
    }

    /**
     * 어떤 조합과도 맞지 않는 선택은 400으로 끊는다. 빈 목록을 돌려주면 프론트가 '이 모델에는
     * 고를 수 있는 옵션이 없다'로 읽어 존재하지 않는 조합을 사용자에게 계속 보여주게 된다.
     */
    private void validateSelection(
            List<DeviceVariant> variants, Map<DeviceVariantAxis, String> selection) {
        if (selection.isEmpty()) return;
        if (filter(variants, selection, null).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * {@code excluded} 축의 선택은 무시하고 나머지 선택만 적용한다. 그 축에서 고를 수 있는 값을
     * 뽑을 때 자기 자신의 선택으로 후보를 좁히면 이미 고른 값 하나만 남아, 사용자가 선택을
     * 바꿀 방법이 사라진다.
     */
    private List<DeviceVariant> filter(
            List<DeviceVariant> variants,
            Map<DeviceVariantAxis, String> selection,
            DeviceVariantAxis excluded) {
        return variants.stream()
                .filter(
                        variant ->
                                selection.entrySet().stream()
                                        .filter(entry -> entry.getKey() != excluded)
                                        .allMatch(
                                                entry ->
                                                        entry.getKey()
                                                                .matches(
                                                                        variant,
                                                                        entry.getValue())))
                .toList();
    }

    private boolean isPresent(List<DeviceVariant> variants, DeviceVariantAxis axis) {
        return variants.stream().anyMatch(variant -> axis.valueOf(variant) != null);
    }

    /**
     * 숫자 축은 숫자 순으로, 나머지는 사전순으로 정렬한다. 용량 목록이 "128, 256, 512"가 아니라
     * "128, 512, 256"으로 나오면 사용자가 목록을 신뢰하지 않는다.
     */
    private List<String> values(List<DeviceVariant> variants, DeviceVariantAxis axis) {
        boolean numeric =
                variants.stream()
                        .map(axis::valueOf)
                        .anyMatch(value -> value instanceof Number || value instanceof BigDecimal);
        TreeSet<String> sorted =
                new TreeSet<>(
                        numeric
                                ? (left, right) ->
                                        new BigDecimal(left).compareTo(new BigDecimal(right))
                                : String.CASE_INSENSITIVE_ORDER);
        variants.stream()
                .map(axis::valueOf)
                .filter(value -> value != null)
                .map(this::render)
                .forEach(sorted::add);
        return List.copyOf(sorted);
    }

    /** DECIMAL(4,2)는 15.60처럼 뒤에 0이 붙어 읽히므로 표시용으로 다듬는다. */
    private String render(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }
}
