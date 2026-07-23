package com.c203.limit.domain.member.repository;

import com.c203.limit.domain.member.entity.MemberTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface MemberTermsAgreementRepository extends JpaRepository<MemberTermsAgreement, Long> {}
