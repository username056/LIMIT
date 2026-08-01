package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.DeviceVariant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceVariantRepository extends JpaRepository<DeviceVariant, Long> {

    /**
     * 모델의 활성 조합 전체. 옵션 화면은 이 목록을 메모리에서 좁혀 축별 값을 뽑는다. 모델 하나의
     * 조합 수는 많아야 수백 개라 축 조합마다 쿼리를 던지는 것보다 단순하고 왕복도 한 번이다.
     */
    List<DeviceVariant> findByModelIdAndIsActiveTrueOrderByIdAsc(Long modelId);

    Optional<DeviceVariant> findByModelIdAndVariantKey(Long modelId, String variantKey);

    boolean existsByModelIdAndIsActiveTrue(Long modelId);
}
