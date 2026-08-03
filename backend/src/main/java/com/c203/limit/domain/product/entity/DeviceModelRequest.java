package com.c203.limit.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_model_request")
public class DeviceModelRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_by_member_id", nullable = false)
    private Long requestedByMemberId;

    @Column(name = "parent_category_id", nullable = false)
    private Long parentCategoryId;

    @Column(nullable = false, length = 50)
    private String manufacturer;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_code", length = 50)
    private String modelCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "os_family", nullable = false, length = 30)
    private OsFamily osFamily;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeviceModelRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_category_id")
    private Long resolvedCategoryId;

    @Column(name = "resolved_model_id")
    private Long resolvedModelId;

    @Column(name = "provisioned_at")
    private LocalDateTime provisionedAt;

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    public static DeviceModelRequest create(
            Long memberId,
            Long parentCategoryId,
            String manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily) {
        DeviceModelRequest request = new DeviceModelRequest();
        request.requestedByMemberId = memberId;
        request.parentCategoryId = parentCategoryId;
        request.manufacturer = manufacturer.trim();
        request.modelName = modelName.trim();
        request.modelCode = trimToNull(modelCode);
        request.osFamily = osFamily;
        request.status = DeviceModelRequestStatus.PENDING;
        request.createdAt = LocalDateTime.now();
        request.updatedAt = request.createdAt;
        return request;
    }

    public void approve(Long adminId, Long categoryId, String note) {
        requirePending();
        this.status = DeviceModelRequestStatus.APPROVED;
        this.reviewedByAdminId = adminId;
        this.resolvedCategoryId = categoryId;
        this.resolvedModelId = categoryId;
        this.reviewNote = trimToNull(note);
        this.updatedAt = LocalDateTime.now();
    }

    public void provision(Long modelId) {
        requirePending();
        this.resolvedCategoryId = modelId;
        this.resolvedModelId = modelId;
        this.provisionedAt = LocalDateTime.now();
        this.updatedAt = this.provisionedAt;
    }

    public void updateDetails(
            Long parentCategoryId,
            String manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily) {
        requirePending();
        this.parentCategoryId = parentCategoryId;
        this.manufacturer = manufacturer.trim();
        this.modelName = modelName.trim();
        this.modelCode = trimToNull(modelCode);
        this.osFamily = osFamily;
        this.updatedAt = LocalDateTime.now();
    }

    public void reject(Long adminId, String note) {
        requirePending();
        this.status = DeviceModelRequestStatus.REJECTED;
        this.reviewedByAdminId = adminId;
        this.reviewNote = trimToNull(note);
        this.updatedAt = LocalDateTime.now();
    }

    private void requirePending() {
        if (status != DeviceModelRequestStatus.PENDING) {
            throw new IllegalStateException("invalid device model request state");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
