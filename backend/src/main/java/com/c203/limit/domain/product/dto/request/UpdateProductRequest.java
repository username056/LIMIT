package com.c203.limit.domain.product.dto.request;

import java.math.BigDecimal;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(name = "UpdateProductRequest", description = "중고 전자기기 상품 부분 수정 요청")
public class UpdateProductRequest {

    @Schema(description = "상품명", example = "Galaxy S24 256GB 자급제")
    @Size(max = 100)
    private String name;

    @Schema(description = "상품 설명", example = "생활 흠집이 있습니다. 구성품은 기기 단품입니다.", nullable = true)
    @Size(max = 2000)
    private String description;

    @JsonIgnore
    private boolean descriptionSpecified;

    @Schema(description = "판매 가격", example = "630000")
    @Positive
    @Digits(integer = 12, fraction = 0)
    private BigDecimal price;

    @Schema(description = "색상", example = "Onyx Black", nullable = true)
    @Size(max = 50)
    private String color;

    @JsonIgnore
    private boolean colorSpecified;

    @Schema(description = "저장 용량(GB)", example = "256", nullable = true)
    @Min(1)
    @Max(16384)
    private Integer storageGb;

    @JsonIgnore
    private boolean storageGbSpecified;

    @Schema(description = "거래 지역", example = "서울 송파구")
    @Size(max = 100)
    private String tradeRegion;

    @Schema(
            description = "직접 입력 제조사. '기타 (직접 입력)' 모델로 등록한 매물에서만 의미가 있다.",
            example = "Samsung")
    @Size(max = 50)
    private String customManufacturer;

    @Schema(description = "직접 입력 모델명", example = "Galaxy Book4 Pro")
    @Size(max = 100)
    private String customModelName;

    @Schema(
            description =
                    "판매자가 확인한 모델별 지원 기능 코드. 최대 5개. 값을 보내면 기존 선택을 대체하고,"
                            + " 빈 배열을 보내면 선택 기능을 모두 해제한다. 직접 입력 모델에는 적용할 수 없다.")
    @Size(max = 5)
    private Set<@NotBlank String> confirmedFeatures;

    @JsonIgnore
    private boolean confirmedFeaturesSpecified;

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSpecified = true;
    }

    @JsonSetter("color")
    public void setColor(String color) {
        this.color = color;
        this.colorSpecified = true;
    }

    @JsonSetter("storageGb")
    public void setStorageGb(Integer storageGb) {
        this.storageGb = storageGb;
        this.storageGbSpecified = true;
    }

    @JsonSetter("price")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @JsonSetter("tradeRegion")
    public void setTradeRegion(String tradeRegion) {
        this.tradeRegion = tradeRegion;
    }

    @JsonSetter("customManufacturer")
    public void setCustomManufacturer(String customManufacturer) {
        this.customManufacturer = customManufacturer;
    }

    @JsonSetter("customModelName")
    public void setCustomModelName(String customModelName) {
        this.customModelName = customModelName;
    }

    @JsonSetter("confirmedFeatures")
    public void setConfirmedFeatures(Set<String> confirmedFeatures) {
        this.confirmedFeatures = confirmedFeatures;
        this.confirmedFeaturesSpecified = true;
    }
}
