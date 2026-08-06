package com.c203.limit.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.dto.request.CompleteListingImageRequest;
import com.c203.limit.domain.product.dto.request.CreateListingImageUploadUrlRequest;
import com.c203.limit.domain.product.dto.request.UpdateListingImageOrderRequest;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import com.c203.limit.domain.product.service.ListingImageUploadService;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProductImageControllerTests {
    @Mock ListingImageUploadService service;
    @Mock CurrentUser currentUser;
    @Mock SellerStatusReader sellerStatusReader;

    ProductImageController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductImageController(service, currentUser, sellerStatusReader);
    }

    @Test
    void sellerImageCommandsRequireActiveSellerAndDelegate() {
        var uploadRequest = mock(CreateListingImageUploadUrlRequest.class);
        var completeRequest = mock(CompleteListingImageRequest.class);
        var orderRequest = mock(UpdateListingImageOrderRequest.class);
        var uploadResponse = mock(EvidenceUploadUrlResponse.class);
        var imageResponse = mock(ListingImageResponse.class);
        List<ListingImageResponse> ordered = List.of(imageResponse);
        when(currentUser.memberId()).thenReturn(55L);
        when(service.createUploadUrl(55L, 1001L, uploadRequest)).thenReturn(uploadResponse);
        when(service.complete(55L, 1001L, completeRequest)).thenReturn(imageResponse);
        when(service.updateOrder(55L, 1001L, orderRequest)).thenReturn(ordered);

        var upload = controller.createImageUploadUrl(1001L, uploadRequest);
        var completed = controller.completeImage(1001L, completeRequest);
        var updated = controller.updateImageOrder(1001L, orderRequest);
        var deleted = controller.deleteImage(1001L, 101L);

        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upload.getBody().data()).isSameAs(uploadResponse);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(completed.getBody().data()).isSameAs(imageResponse);
        assertThat(updated.getBody().data()).isSameAs(ordered);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sellerStatusReader, times(4)).requireActiveSeller(55L);
        verify(service).delete(55L, 1001L, 101L);
    }

    @Test
    void imageQueryPassesOptionalViewerIdentity() {
        ListingImageResponse image = mock(ListingImageResponse.class);
        List<ListingImageResponse> images = List.of(image);
        when(currentUser.memberIdOrNull()).thenReturn(77L);
        when(service.findAll(1001L, 77L)).thenReturn(images);

        var result = controller.getImages(1001L);

        assertThat(result.getBody().data()).isSameAs(images);
    }
}
