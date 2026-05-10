package com.interview.settlement.application;

import com.interview.settlement.domain.MerchantCreditReadModel;
import com.interview.settlement.domain.MerchantCreditReadModelRepository;
import com.interview.settlement.domain.OrderReadModel;
import com.interview.settlement.domain.OrderReadModelRepository;
import com.interview.settlement.domain.SettlementReport;
import com.interview.settlement.domain.SettlementReportRepository;
import com.interview.settlement.domain.SettlementStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class SettlementJob {

    private static final Logger logger = LoggerFactory.getLogger(SettlementJob.class);

    private final OrderReadModelRepository orderReadModelRepository;
    private final MerchantCreditReadModelRepository merchantCreditReadModelRepository;
    private final SettlementReportRepository settlementReportRepository;

    public SettlementJob(OrderReadModelRepository orderReadModelRepository,
                         MerchantCreditReadModelRepository merchantCreditReadModelRepository,
                         SettlementReportRepository settlementReportRepository) {
        this.orderReadModelRepository = orderReadModelRepository;
        this.merchantCreditReadModelRepository = merchantCreditReadModelRepository;
        this.settlementReportRepository = settlementReportRepository;
    }

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void runDailySettlement() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Instant startOfDay = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        logger.info("Starting daily settlement job for date: {}", yesterday);

        // Find all merchants that have orders in the read model
        Set<String> merchantIds = orderReadModelRepository.findDistinctMerchantIds();

        int processedCount = 0;
        List<SettlementReport> reports = new ArrayList<>();
        for (String merchantId : merchantIds) {
            // Skip if already processed for this date
            if (settlementReportRepository.existsByMerchantIdAndSettlementDate(merchantId, yesterday)) {
                logger.info("Settlement already exists for merchant {} on date {}", merchantId, yesterday);
                continue;
            }

            // Query yesterday's completed orders
            List<OrderReadModel> orders = orderReadModelRepository
                    .findByMerchantIdAndCompletedAtBetween(merchantId, startOfDay, endOfDay);
            BigDecimal expectedRevenue = orders.stream()
                    .map(OrderReadModel::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int orderCount = orders.size();

            // Query yesterday's merchant credits
            List<MerchantCreditReadModel> credits = merchantCreditReadModelRepository
                    .findByMerchantIdAndCreditedAtBetween(merchantId, startOfDay, endOfDay);
            BigDecimal actualRevenue = credits.stream()
                    .map(MerchantCreditReadModel::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Determine status
            SettlementStatus status = expectedRevenue.compareTo(actualRevenue) == 0
                    ? SettlementStatus.MATCHED
                    : SettlementStatus.DISCREPANCY;

            // Create settlement report
            SettlementReport report = new SettlementReport(
                    UUID.randomUUID().toString(),
                    merchantId,
                    yesterday,
                    expectedRevenue,
                    actualRevenue,
                    status,
                    orderCount
            );
            reports.add(report);

            logger.info("Settlement created for merchant {}: expected={}, actual={}, status={}, orders={}",
                    merchantId, expectedRevenue, actualRevenue, status, orderCount);
            processedCount++;
        }

        if (!reports.isEmpty()) {
            settlementReportRepository.saveAll(reports);
        }

        logger.info("Daily settlement job completed. Processed {} merchants for date {}",
                processedCount, yesterday);
    }
}
