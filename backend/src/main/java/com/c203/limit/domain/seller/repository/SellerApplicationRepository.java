package com.c203.limit.domain.seller.repository;
import java.util.Optional; import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository; import com.c203.limit.domain.seller.entity.*;
public interface SellerApplicationRepository extends JpaRepository<SellerApplication,Long>{
 Page<SellerApplication> findAllByMemberId(Long memberId,Pageable pageable); Optional<SellerApplication> findByIdAndMemberId(Long id,Long memberId);
 boolean existsByMemberIdAndStatusIn(Long memberId,java.util.Collection<SellerApplicationStatus> statuses);
 long countByMemberId(Long memberId);
}
