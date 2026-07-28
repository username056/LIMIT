package com.c203.limit.domain.inspection.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.dto.response.DiagnosisSummaryItem;
import com.c203.limit.domain.inspection.dto.response.ProductDiagnosisSummaryResponse;
import com.c203.limit.domain.inspection.enums.DiagnosisSummaryStatus;
import com.c203.limit.domain.inspection.service.ProductDiagnosisSummaryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InspectionProductDiagnosisSummaryControllerTests {

    private static final Long PRODUCT_ID = 1L;

    @Mock ProductDiagnosisSummaryService productDiagnosisSummaryService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new InspectionProductDiagnosisSummaryController(productDiagnosisSummaryService))
                        .build();
    }

    @Test
    void returns200WithoutAuthentication() throws Exception {
        ProductDiagnosisSummaryResponse response =
                new ProductDiagnosisSummaryResponse(
                        PRODUCT_ID,
                        List.of(
                                new DiagnosisSummaryItem(
                                        "CPU",
                                        "https://cdn.example.com/evidence/5.txt",
                                        "file cpu",
                                        DiagnosisSummaryStatus.AVAILABLE)),
                        "자동 추출값은 참고 정보이며 상품의 정상 여부를 보증하지 않습니다.");
        when(productDiagnosisSummaryService.getSummary(eq(PRODUCT_ID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/inspections/products/{productId}/diagnosis-summary", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.items[0].fieldName").value("CPU"))
                .andExpect(jsonPath("$.data.items[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.disclaimer")
                        .value("자동 추출값은 참고 정보이며 상품의 정상 여부를 보증하지 않습니다."))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
