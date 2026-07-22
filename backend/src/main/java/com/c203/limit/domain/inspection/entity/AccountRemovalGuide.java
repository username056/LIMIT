package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.converter.StringListJsonConverter;
import com.c203.limit.domain.inspection.enums.DeviceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "account_removal_guide")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRemovalGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_removal_guide_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    private DeviceType deviceType;

    @Column(name = "manufacturer", nullable = false, length = 50)
    private String manufacturer;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "steps", nullable = false, columnDefinition = "json")
    private List<String> steps;

    @Lob
    @Column(name = "disclaimer_text", nullable = false)
    private String disclaimerText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private AccountRemovalGuide(DeviceType deviceType, String manufacturer, Integer templateVersion,
                                List<String> steps, String disclaimerText, LocalDateTime createdAt) {
        this.deviceType = deviceType;
        this.manufacturer = manufacturer;
        this.templateVersion = templateVersion;
        this.steps = steps;
        this.disclaimerText = disclaimerText;
        this.createdAt = createdAt;
    }
}
