package com.interview.settlement.interfaces;

import com.interview.settlement.application.SettlementService;
import com.interview.settlement.domain.SettlementReport;
import com.interview.settlement.domain.SettlementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SettlementService settlementService;

    @InjectMocks
    private SettlementController settlementController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(settlementController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void getSettlementsReturns200WithList() throws Exception {
        String merchantId = "merchant-1";
        SettlementReport report1 = new SettlementReport(
                "rpt-1", merchantId, LocalDate.of(2024, 1, 15),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                SettlementStatus.MATCHED, 5
        );
        SettlementReport report2 = new SettlementReport(
                "rpt-2", merchantId, LocalDate.of(2024, 1, 10),
                new BigDecimal("200.00"), new BigDecimal("190.00"),
                SettlementStatus.DISCREPANCY, 8
        );

        when(settlementService.getSettlements(merchantId)).thenReturn(List.of(report1, report2));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/settlements", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reportId").value("rpt-1"))
                .andExpect(jsonPath("$[0].merchantId").value(merchantId))
                .andExpect(jsonPath("$[0].settlementDate[0]").value(2024))
                .andExpect(jsonPath("$[0].settlementDate[1]").value(1))
                .andExpect(jsonPath("$[0].settlementDate[2]").value(15))
                .andExpect(jsonPath("$[0].expectedRevenue").value(100.00))
                .andExpect(jsonPath("$[0].actualRevenue").value(100.00))
                .andExpect(jsonPath("$[0].status").value("MATCHED"))
                .andExpect(jsonPath("$[0].orderCount").value(5))
                .andExpect(jsonPath("$[1].reportId").value("rpt-2"))
                .andExpect(jsonPath("$[1].status").value("DISCREPANCY"))
                .andExpect(jsonPath("$[1].orderCount").value(8));
    }

    @Test
    void getSettlementsReturns200WithEmptyList() throws Exception {
        String merchantId = "merchant-1";

        when(settlementService.getSettlements(merchantId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/settlements", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getLatestSettlementReturns200() throws Exception {
        String merchantId = "merchant-1";
        SettlementReport report = new SettlementReport(
                "rpt-latest", merchantId, LocalDate.of(2024, 1, 20),
                new BigDecimal("500.00"), new BigDecimal("500.00"),
                SettlementStatus.MATCHED, 12
        );

        when(settlementService.getLatestSettlement(merchantId)).thenReturn(report);

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/settlements/latest", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value("rpt-latest"))
                .andExpect(jsonPath("$.merchantId").value(merchantId))
                .andExpect(jsonPath("$.settlementDate[0]").value(2024))
                .andExpect(jsonPath("$.settlementDate[1]").value(1))
                .andExpect(jsonPath("$.settlementDate[2]").value(20))
                .andExpect(jsonPath("$.expectedRevenue").value(500.00))
                .andExpect(jsonPath("$.actualRevenue").value(500.00))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.orderCount").value(12));
    }

    @Test
    void getLatestSettlementWhenNotFoundReturns404() throws Exception {
        String merchantId = "merchant-1";

        when(settlementService.getLatestSettlement(merchantId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/settlements/latest", merchantId))
                .andExpect(status().isNotFound());
    }
}
