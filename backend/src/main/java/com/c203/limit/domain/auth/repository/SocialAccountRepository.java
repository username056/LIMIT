package com.c203.limit.domain.auth.repository;

import com.c203.limit.domain.auth.entity.SocialAccount;
import com.c203.limit.domain.auth.entity.SocialProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider, String providerUserId);

    List<SocialAccount> findAllByMemberId(Long memberId);

    Optional<SocialAccount> findByIdAndMemberId(Long id, Long memberId);

    boolean existsByMemberIdAndProvider(Long memberId, SocialProvider provider);

    long countByMemberId(Long memberId);
}
