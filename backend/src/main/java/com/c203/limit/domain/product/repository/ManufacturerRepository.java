package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.Manufacturer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {

    List<Manufacturer> findByIsActiveTrueOrderByNameAsc();

    Optional<Manufacturer> findByNormalizedName(String normalizedName);
}
