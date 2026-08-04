package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SubmitTestResultRequest;
import com.c203.limit.domain.inspection.enums.InspectionUserResult;
import com.c203.limit.domain.inspection.enums.MeasurementStatus;
import com.c203.limit.domain.inspection.enums.TestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(
        name = "inspection_session_test_result",
        indexes = {
            @Index(
                    name = "idx_inspection_test_result_session_created",
                    columnList = "session_key,created_at,id"),
            @Index(
                    name = "idx_inspection_test_result_checklist_item",
                    columnList = "listing_checklist_item_id")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_inspection_test_result_client",
                    columnNames = {"session_key", "client_result_id"}),
            @UniqueConstraint(
                    name = "uk_inspection_test_result_attempt",
                    columnNames = {"session_key", "test_type", "attempt_no"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionSessionTestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", nullable = false, length = 36)
    private String sessionKey;

    @Column(name = "listing_checklist_item_id")
    private Long checklistItemId;

    @Column(name = "client_result_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientResultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 30)
    private TestType testType;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_status", nullable = false, length = 30)
    private MeasurementStatus measurementStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_result", length = 30)
    private InspectionUserResult userResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "measured_values")
    private Map<String, Object> measuredValues;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "raw_data_saved", nullable = false)
    private boolean rawDataSaved;

    @Column(name = "tested_at", nullable = false)
    private LocalDateTime testedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    public static InspectionSessionTestResult create(
            String sessionKey,
            Long checklistItemId,
            SubmitTestResultRequest request,
            int attemptNo,
            LocalDateTime createdAt) {
        InspectionSessionTestResult result = new InspectionSessionTestResult();
        result.sessionKey = sessionKey;
        result.checklistItemId = checklistItemId;
        result.clientResultId = request.clientResultId();
        result.testType = request.testType();
        result.measurementStatus = request.measurementStatus();
        result.userResult = request.userResult();
        result.measuredValues = copy(request.measuredValues());
        result.attemptNo = attemptNo;
        result.rawDataSaved = false;
        result.testedAt = normalizedTestedAt(request);
        result.createdAt = createdAt;
        result.errorCode = request.errorCode();
        return result;
    }

    public boolean hasSamePayload(SubmitTestResultRequest request) {
        return testType == request.testType()
                && measurementStatus == request.measurementStatus()
                && userResult == request.userResult()
                && Objects.equals(measuredValues, request.measuredValues())
                && testedAt.equals(normalizedTestedAt(request))
                && Objects.equals(errorCode, request.errorCode());
    }

    private static Map<String, Object> copy(Map<String, Object> values) {
        return values == null ? null : new LinkedHashMap<>(values);
    }

    private static LocalDateTime normalizedTestedAt(SubmitTestResultRequest request) {
        return LocalDateTime.ofInstant(request.testedAt().toInstant(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }
}
