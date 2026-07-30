package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.MediaUploadSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaUploadSessionRepository
        extends JpaRepository<MediaUploadSession, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from MediaUploadSession session where session.uploadId = :uploadId")
    Optional<MediaUploadSession> findByIdForUpdate(@Param("uploadId") String uploadId);

    List<MediaUploadSession> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            com.c203.limit.domain.product.entity.MediaUploadStatus status,
            LocalDateTime expiresAt);
}
