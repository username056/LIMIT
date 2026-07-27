package com.c203.limit.domain.product.dto.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
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
    private BigDecimal price;

    @Schema(description = "색상", example = "Onyx Black", nullable = true)
    @Size(max = 50)
    private String color;

    @JsonIgnore
    private boolean colorSpecified;

    @Schema(description = "저장 용량(GB)", example = "256", nullable = true)
    @Min(1)
    private Integer storageGb;

    @JsonIgnore
    private boolean storageGbSpecified;

    @Schema(description = "거래 지역", example = "서울 송파구")
    @Size(max = 100)
    private String tradeRegion;

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
}
