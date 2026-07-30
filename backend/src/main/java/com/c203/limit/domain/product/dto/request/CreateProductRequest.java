package com.c203.limit.domain.product.dto.request;

import java.math.BigDecimal;
import java.util.Set;

import com.c203.limit.domain.product.entity.OsFamily;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(onConstructor_ = @JsonCreator)
@Schema(name = "CreateProductRequest", description = "중고 전자기기 상품 초안 등록 요청")
public class CreateProductRequest {

    @Schema(description = "기기 카테고리 ID", example = "1")
    @NotNull
    private final Long categoryId;

    @Schema(description = "기기 모델 ID", example = "101")
    @NotNull
    private final Long deviceModelId;

    @Schema(description = "상품명", example = "Galaxy S24 256GB")
    @NotBlank
    @Size(max = 100)
    private final String name;

    @Schema(description = "상품 설명", example = "생활 흠집이 있습니다.")
    @Size(max = 2000)
    private final String description;

    @Schema(description = "판매 가격", example = "650000")
    @NotNull
    @Positive
    @Digits(integer = 12, fraction = 0)
    private final BigDecimal price;

    @Schema(description = "색상", example = "Onyx Black")
    @Size(max = 50)
    private final String color;

    @Schema(description = "저장 용량(GB)", example = "256")
    @Min(1)
    @Max(16384)
    private final Integer storageGb;

    @Schema(description = "거래 지역", example = "서울 강남구")
    @NotBlank
    @Size(max = 100)
    private final String tradeRegion;

    @Schema(description = "판매자가 확인한 모델별 지원 기능 코드. 최대 5개")
    @Size(max = 5)
    private final Set<@NotBlank String> confirmedFeatures;

    @Schema(
            description =
                    "카탈로그에 없는 기기를 '기타 (직접 입력)' 모델로 등록할 때 판매자가 적는 제조사."
                            + " 모델명과 함께 보내면 이 정보로 체크리스트를 생성한다.",
            example = "Samsung")
    @Size(max = 50)
    private final String customManufacturer;

    @Schema(description = "직접 입력 모델명", example = "Galaxy Book4 Pro")
    @Size(max = 100)
    private final String customModelName;

    @Schema(description = "직접 입력 모델 코드", example = "NT960XGK-KC51G")
    @Size(max = 50)
    private final String customModelCode;

    @Schema(
            description = "직접 입력 운영체제. 노트북 체크리스트 생성에 필요하다.",
            allowableValues = {"WINDOWS", "LINUX"})
    private final OsFamily customOsFamily;

    public CreateProductRequest(
            Long categoryId,
            Long deviceModelId,
            String name,
            String description,
            BigDecimal price,
            String color,
            Integer storageGb,
            String tradeRegion) {
        this(
                categoryId,
                deviceModelId,
                name,
                description,
                price,
                color,
                storageGb,
                tradeRegion,
                Set.of(),
                null,
                null,
                null,
                null);
    }

    /** 판매자가 카탈로그에 없는 기기를 직접 입력했는지 여부. */
    public boolean hasCustomModel() {
        return hasText(customManufacturer) && hasText(customModelName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
