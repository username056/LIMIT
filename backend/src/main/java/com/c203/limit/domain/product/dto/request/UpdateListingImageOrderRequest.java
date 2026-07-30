package com.c203.limit.domain.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateListingImageOrderRequest {

    @NotEmpty
    private List<@NotNull Long> imageIds;

    @NotNull
    private Long thumbnailImageId;
}
