package com.c203.limit.domain.seller.repository;
import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository; import com.c203.limit.domain.seller.entity.Seller;
public interface SellerRepository extends JpaRepository<Seller,Long>{Optional<Seller> findByMemberId(Long memberId); boolean existsByMemberId(Long memberId);}
