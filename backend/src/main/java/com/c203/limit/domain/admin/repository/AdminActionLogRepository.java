package com.c203.limit.domain.admin.repository;

import com.c203.limit.domain.admin.entity.AdminActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdminActionLogRepository
        extends JpaRepository<AdminActionLog, Long>, JpaSpecificationExecutor<AdminActionLog> {}
