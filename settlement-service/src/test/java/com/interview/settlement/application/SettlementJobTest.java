package com.interview.settlement.application;

import com.interview.settlement.domain.MerchantCreditReadModel;
import com.interview.settlement.domain.MerchantCreditReadModelRepository;
import com.interview.settlement.domain.OrderReadModel;
import com.interview.settlement.domain.OrderReadModelRepository;
import com.interview.settlement.domain.SettlementReport;
import com.interview.settlement.domain.SettlementReportRepository;
import com.interview.settlement.domain.SettlementStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementJobTest {

    @Mock
    private OrderReadModelRepository orderReadModelRepository;

    @Mock
    private MerchantCreditReadModelRepository merchantCreditReadModelRepository;

    @Mock
    private SettlementReportRepository settlementReportRepository;

    @InjectMocks
    private SettlementJob settlementJob;

    @Test
    void whenOrdersAndCreditsMatchCreatesMatchedReport() {
        String merchantId = "merchant-1";
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Instant startOfDay = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        OrderReadModel order = new OrderReadModel(
                "order-1", merchantId, "user-1", "sku-1", 2,
                new BigDecimal("50.00"), new BigDecimal("100.00"),
                startOfDay.plusSeconds(3600)
        );
        MerchantCreditReadModel credit = new MerchantCreditReadModel(
                "evt-1", "order-1", merchantId,
                new BigDecimal("100.00"), startOfDay.plusSeconds(7200)
        );

        when(orderReadModelRepository.findDistinctMerchantIds()).thenReturn(Set.of(merchantId));
        when(settlementReportRepository.existsByMerchantIdAndSettlementDate(merchantId, yesterday))
                .thenReturn(false);
        when(orderReadModelRepository.findByMerchantIdAndCompletedAtBetween(merchantId, startOfDay, endOfDay))
                .thenReturn(List.of(order));
        when(merchantCreditReadModelRepository.findByMerchantIdAndCreditedAtBetween(merchantId, startOfDay, endOfDay))
                .thenReturn(List.of(credit));

        settlementJob.runDailySettlement();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettlementReport>> captor = ArgumentCaptor.forClass(List.class);
        verify(settlementReportRepository).saveAll(captor.capture());

        List<SettlementReport> saved = captor.getValue();
        assertEquals(1, saved.size());
        SettlementReport report = saved.get(0);
        assertEquals(merchantId, report.getMerchantId());
        assertEquals(yesterday, report.getSettlementDate());
        assertEquals(0, new BigDecimal("100.00").compareTo(report.getExpectedRevenue()));
        assertEquals(0, new BigDecimal("100.00").compareTo(report.getActualRevenue()));
        assertEquals(SettlementStatus.MATCHED, report.getStatus());
        assertEquals(1, report.getOrderCount());
        assertNotNull(report.getReportId());
    }

    @Test
    void whenOrdersAndCreditsDoNotMatchCreatesDiscrepancyReport() {
        String merchantId = "merchant-1";
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Instant startOfDay = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        OrderReadModel order = new OrderReadModel(
                "order-1", merchantId, "user-1", "sku-1", 1,
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                startOfDay.plusSeconds(3600)
        );
        MerchantCreditReadModel credit = new MerchantCreditReadModel(
                "evt-1", "order-1", merchantId,
                new BigDecimal("90.00"), startOfDay.plusSeconds(7200)
        );

        when(orderReadModelRepository.findDistinctMerchantIds()).thenReturn(Set.of(merchantId));
        when(settlementReportRepository.existsByMerchantIdAndSettlementDate(merchantId, yesterday))
                .thenReturn(false);
        when(orderReadModelRepository.findByMerchantIdAndCompletedAtBetween(merchantId, startOfDay, endOfDay))
                .thenReturn(List.of(order));
        when(merchantCreditReadModelRepository.findByMerchantIdAndCreditedAtBetween(merchantId, startOfDay, endOfDay))
                .thenReturn(List.of(credit));

        settlementJob.runDailySettlement();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettlementReport>> captor = ArgumentCaptor.forClass(List.class);
        verify(settlementReportRepository).saveAll(captor.capture());

        List<SettlementReport> saved = captor.getValue();
        assertEquals(1, saved.size());
        SettlementReport report = saved.get(0);
        assertEquals(SettlementStatus.DISCREPANCY, report.getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(report.getExpectedRevenue()));
        assertEquals(0, new BigDecimal("90.00").compareTo(report.getActualRevenue()));
        assertEquals(1, report.getOrderCount());
    }

    @Test
    void whenNoOrdersExistCreatesReportWithZeroExpected() {
        String merchantId = "merchant-1";
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Instant startOfDay = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        when(orderReadModelRepository.findDistinctMerchantIds()).thenReturn(Set.of(merchantId));
        when(settlementReportRepository.existsByMerchantIdAndSettlementDate(merchantId, yesterday))
                .thenReturn(false);
        when(orderReadModelRepository.findByMerchantIdAndCompletedAtBetween(merchantId, startOfDay, endOfDay))
                .thenReturn(Collections.emptyList());
        when(merchantCreditReadModelRepository.findByMerchantIdAndCreditedAtBetween(merchantId, startOfDay, endOfDay))
                .thenReturn(Collections.emptyList());

        settlementJob.runDailySettlement();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettlementReport>> captor = ArgumentCaptor.forClass(List.class);
        verify(settlementReportRepository).saveAll(captor.capture());

        List<SettlementReport> saved = captor.getValue();
        assertEquals(1, saved.size());
        SettlementReport report = saved.get(0);
        assertEquals(BigDecimal.ZERO, report.getExpectedRevenue());
        assertEquals(BigDecimal.ZERO, report.getActualRevenue());
        assertEquals(SettlementStatus.MATCHED, report.getStatus());
        assertEquals(0, report.getOrderCount());
    }

    @Test
    void whenMultipleMerchantsHaveOrdersCreatesReportForEach() {
        String merchantA = "merchant-a";
        String merchantB = "merchant-b";
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Instant startOfDay = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        OrderReadModel orderA = new OrderReadModel(
                "order-a", merchantA, "user-1", "sku-1", 1,
                new BigDecimal("75.00"), new BigDecimal("75.00"),
                startOfDay.plusSeconds(3600)
        );
        OrderReadModel orderB = new OrderReadModel(
                "order-b", merchantB, "user-2", "sku-2", 1,
                new BigDecimal("120.00"), new BigDecimal("120.00"),
                startOfDay.plusSeconds(7200)
        );

        MerchantCreditReadModel creditA = new MerchantCreditReadModel(
                "evt-a", "order-a", merchantA,
                new BigDecimal("75.00"), startOfDay.plusSeconds(4000)
        );
        MerchantCreditReadModel creditB = new MerchantCreditReadModel(
                "evt-b", "order-b", merchantB,
                new BigDecimal("120.00"), startOfDay.plusSeconds(8000)
        );

        when(orderReadModelRepository.findDistinctMerchantIds()).thenReturn(Set.of(merchantA, merchantB));
        when(settlementReportRepository.existsByMerchantIdAndSettlementDate(any(), eq(yesterday)))
                .thenReturn(false);
        when(orderReadModelRepository.findByMerchantIdAndCompletedAtBetween(merchantA, startOfDay, endOfDay))
                .thenReturn(List.of(orderA));
        when(orderReadModelRepository.findByMerchantIdAndCompletedAtBetween(merchantB, startOfDay, endOfDay))
                .thenReturn(List.of(orderB));
        when(merchantCreditReadModelRepository.findByMerchantIdAndCreditedAtBetween(merchantA, startOfDay, endOfDay))
                .thenReturn(List.of(creditA));
        when(merchantCreditReadModelRepository.findByMerchantIdAndCreditedAtBetween(merchantB, startOfDay, endOfDay))
                .thenReturn(List.of(creditB));

        settlementJob.runDailySettlement();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettlementReport>> captor = ArgumentCaptor.forClass(List.class);
        verify(settlementReportRepository).saveAll(captor.capture());

        List<SettlementReport> savedReports = captor.getValue();
        assertEquals(2, savedReports.size());

        SettlementReport reportA = savedReports.stream()
                .filter(r -> r.getMerchantId().equals(merchantA))
                .findFirst()
                .orElseThrow();
        SettlementReport reportB = savedReports.stream()
                .filter(r -> r.getMerchantId().equals(merchantB))
                .findFirst()
                .orElseThrow();

        assertEquals(SettlementStatus.MATCHED, reportA.getStatus());
        assertEquals(0, new BigDecimal("75.00").compareTo(reportA.getExpectedRevenue()));
        assertEquals(1, reportA.getOrderCount());

        assertEquals(SettlementStatus.MATCHED, reportB.getStatus());
        assertEquals(0, new BigDecimal("120.00").compareTo(reportB.getExpectedRevenue()));
        assertEquals(1, reportB.getOrderCount());
    }

    @Test
    void whenSettlementAlreadyExistsSkipsMerchant() {
        String merchantId = "merchant-1";
        LocalDate yesterday = LocalDate.now().minusDays(1);

        when(orderReadModelRepository.findDistinctMerchantIds()).thenReturn(Set.of(merchantId));
        when(settlementReportRepository.existsByMerchantIdAndSettlementDate(merchantId, yesterday))
                .thenReturn(true);

        settlementJob.runDailySettlement();

        verify(settlementReportRepository, never()).save(any());
        verify(settlementReportRepository, never()).saveAll(anyList());
    }

    @Test
    void whenNoOrdersAtAllDoesNotSaveAnything() {
        when(orderReadModelRepository.findDistinctMerchantIds()).thenReturn(Set.of());

        settlementJob.runDailySettlement();

        verify(settlementReportRepository, never()).save(any());
        verify(settlementReportRepository, never()).saveAll(anyList());
    }

    @Test
    void multipleOrdersForSameMerchantAreSummedCorrectly() {
        String merchantId = "merchant-1";
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Instant startOfDay = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        OrderReadModel order1 = new OrderReadModel(
                "order-1", merchantId, "user-1", "sku-1", 1,
                new BigDecimal("30.00"), new BigDecimal("30.00"),
                startOfDay.plusSeconds(3600)
        );
        OrderReadModel order2 = new OrderReadModel(
                "order-2", merchantId, "user-2", "sku-2", 2,
                new BigDecimal("20.00"), new BigDecimal("40.00"),
                startOfDay.plusSeconds(7200)
        );

        MerchantCreditReadModel credit1 = new MerchantCreditReadModel(
                "evt-1", "order-1", merchantId,
                new BigDecimal("30.00"), startOfDay.plusSeconds(4000)
        );
        MerchantCreditReadModel credit2 = new MerchantCreditReadModel(
                "evt-2", "order-2", merchantId,
                new BigDecimal("40.00"), startOfDay.plusSeconds(8000)
        );

        when(orderReadModelRepository.findDistinctMerchantIds()).thenReturn(Set.of(merchantId));
        when(settlementReportRepository.existsByMerchantIdAndSettlementDate(merchantId, yesterday))
                .thenReturn(false);
        when(orderReadModelRepository.findByMerchantIdAndCompletedAtBetween(merchantId, startOfDay, endOfDay))
                .thenReturn(List.of(order1, order2));
        when(merchantCreditReadModelRepository.findByMerchantIdAndCreditedAtBetween(merchantId, startOfDay, endOfDay))
                .thenReturn(List.of(credit1, credit2));

        settlementJob.runDailySettlement();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettlementReport>> captor = ArgumentCaptor.forClass(List.class);
        verify(settlementReportRepository).saveAll(captor.capture());

        List<SettlementReport> saved = captor.getValue();
        assertEquals(1, saved.size());
        SettlementReport report = saved.get(0);
        assertEquals(0, new BigDecimal("70.00").compareTo(report.getExpectedRevenue()));
        assertEquals(0, new BigDecimal("70.00").compareTo(report.getActualRevenue()));
        assertEquals(SettlementStatus.MATCHED, report.getStatus());
        assertEquals(2, report.getOrderCount());
    }
}
