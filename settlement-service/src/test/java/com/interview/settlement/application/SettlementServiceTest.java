package com.interview.settlement.application;

import com.interview.settlement.domain.SettlementReport;
import com.interview.settlement.domain.SettlementReportRepository;
import com.interview.settlement.domain.SettlementStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementReportRepository settlementReportRepository;

    @InjectMocks
    private SettlementService settlementService;

    @Test
    void getLatestSettlementReturnsLatestReport() {
        String merchantId = "merchant-1";
        SettlementReport older = new SettlementReport(
                "rpt-1", merchantId, LocalDate.of(2024, 1, 10),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                SettlementStatus.MATCHED, 5
        );
        SettlementReport latest = new SettlementReport(
                "rpt-2", merchantId, LocalDate.of(2024, 1, 15),
                new BigDecimal("200.00"), new BigDecimal("190.00"),
                SettlementStatus.DISCREPANCY, 8
        );

        when(settlementReportRepository.findByMerchantIdOrderBySettlementDateDesc(merchantId))
                .thenReturn(List.of(latest, older));

        SettlementReport result = settlementService.getLatestSettlement(merchantId);

        assertNotNull(result);
        assertEquals("rpt-2", result.getReportId());
        assertEquals(LocalDate.of(2024, 1, 15), result.getSettlementDate());
        verify(settlementReportRepository).findByMerchantIdOrderBySettlementDateDesc(merchantId);
    }

    @Test
    void getSettlementsReturnsListSortedByDateDesc() {
        String merchantId = "merchant-1";
        SettlementReport report1 = new SettlementReport(
                "rpt-1", merchantId, LocalDate.of(2024, 1, 20),
                new BigDecimal("300.00"), new BigDecimal("300.00"),
                SettlementStatus.MATCHED, 10
        );
        SettlementReport report2 = new SettlementReport(
                "rpt-2", merchantId, LocalDate.of(2024, 1, 18),
                new BigDecimal("150.00"), new BigDecimal("150.00"),
                SettlementStatus.MATCHED, 5
        );
        SettlementReport report3 = new SettlementReport(
                "rpt-3", merchantId, LocalDate.of(2024, 1, 15),
                new BigDecimal("100.00"), new BigDecimal("95.00"),
                SettlementStatus.DISCREPANCY, 3
        );

        when(settlementReportRepository.findByMerchantIdOrderBySettlementDateDesc(merchantId))
                .thenReturn(List.of(report1, report2, report3));

        List<SettlementReport> result = settlementService.getSettlements(merchantId);

        assertEquals(3, result.size());
        assertEquals(LocalDate.of(2024, 1, 20), result.get(0).getSettlementDate());
        assertEquals(LocalDate.of(2024, 1, 18), result.get(1).getSettlementDate());
        assertEquals(LocalDate.of(2024, 1, 15), result.get(2).getSettlementDate());
        verify(settlementReportRepository).findByMerchantIdOrderBySettlementDateDesc(merchantId);
    }

    @Test
    void getLatestSettlementWhenNoReportsReturnsNull() {
        String merchantId = "merchant-1";

        when(settlementReportRepository.findByMerchantIdOrderBySettlementDateDesc(merchantId))
                .thenReturn(Collections.emptyList());

        SettlementReport result = settlementService.getLatestSettlement(merchantId);

        assertNull(result);
        verify(settlementReportRepository).findByMerchantIdOrderBySettlementDateDesc(merchantId);
    }

    @Test
    void getSettlementsWhenNoReportsReturnsEmptyList() {
        String merchantId = "merchant-1";

        when(settlementReportRepository.findByMerchantIdOrderBySettlementDateDesc(merchantId))
                .thenReturn(Collections.emptyList());

        List<SettlementReport> result = settlementService.getSettlements(merchantId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(settlementReportRepository).findByMerchantIdOrderBySettlementDateDesc(merchantId);
    }

    @Test
    void getLatestSettlementWithSingleReportReturnsThatReport() {
        String merchantId = "merchant-1";
        SettlementReport report = new SettlementReport(
                "rpt-1", merchantId, LocalDate.of(2024, 3, 1),
                new BigDecimal("50.00"), new BigDecimal("50.00"),
                SettlementStatus.MATCHED, 2
        );

        when(settlementReportRepository.findByMerchantIdOrderBySettlementDateDesc(merchantId))
                .thenReturn(List.of(report));

        SettlementReport result = settlementService.getLatestSettlement(merchantId);

        assertNotNull(result);
        assertEquals("rpt-1", result.getReportId());
        assertEquals(LocalDate.of(2024, 3, 1), result.getSettlementDate());
    }
}
