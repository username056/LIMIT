package com.c203.limit.domain.auth.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.c203.limit.domain.auth.entity.SocialAccount;
import com.c203.limit.domain.auth.entity.SocialProvider;
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
    List<SocialAccount> findAllByMemberId(Long memberId);
    Optional<SocialAccount> findByIdAndMemberId(Long id, Long memberId);
    long countByMemberId(Long memberId);
}
