package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.Category;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentIsNullOrderByDisplayOrderAsc();

    @EntityGraph(attributePaths = "parent")
    List<Category> findByParentIdOrderByDisplayOrderAsc(Long parentId);

    List<Category> findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();

    @EntityGraph(attributePaths = "parent")
    List<Category> findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(Long parentId);

    @EntityGraph(attributePaths = "parent")
    @Query(
            """
            SELECT category
              FROM Category category
             WHERE category.modelCode IS NOT NULL
               AND (:categoryId IS NULL OR category.parent.id = :categoryId OR category.id = :categoryId)
               AND (:manufacturerId IS NULL OR category.manufacturerId = :manufacturerId)
               AND (:keyword IS NULL OR LOWER(category.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(category.manufacturer) LIKE LOWER(CONCAT('%', :keyword, '%')))
             ORDER BY category.displayOrder ASC, category.id ASC
            """)
    Page<Category> findModels(
            @Param("categoryId") Long categoryId,
            @Param("manufacturerId") Long manufacturerId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
