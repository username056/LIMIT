package com.c203.limit.domain.chat.repository;

import com.c203.limit.domain.chat.entity.ReinspectionRequestMessage;
import com.c203.limit.domain.chat.entity.ReinspectionRequestMessageId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReinspectionRequestMessageRepository
        extends JpaRepository<ReinspectionRequestMessage, ReinspectionRequestMessageId> {}
