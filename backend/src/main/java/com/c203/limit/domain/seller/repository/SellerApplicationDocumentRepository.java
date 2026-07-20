package com.c203.limit.domain.seller.repository;
import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository; import com.c203.limit.domain.seller.entity.SellerApplicationDocument;
public interface SellerApplicationDocumentRepository extends JpaRepository<SellerApplicationDocument,Long>{
 List<SellerApplicationDocument> findAllByApplicationId(Long applicationId); boolean existsByApplicationId(Long applicationId);
 Optional<SellerApplicationDocument> findByIdAndApplicationId(Long id,Long applicationId);
}
