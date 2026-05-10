package com.interview.settlement.application;

import com.interview.settlement.domain.SettlementReport;
import com.interview.settlement.domain.SettlementReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementReportRepository settlementReportRepository;

    public SettlementService(SettlementReportRepository settlementReportRepository) {
        this.settlementReportRepository = settlementReportRepository;
    }

    public SettlementReport getLatestSettlement(String merchantId) {
        List<SettlementReport> settlements = settlementReportRepository
                .findByMerchantIdOrderBySettlementDateDesc(merchantId);
        if (settlements.isEmpty()) {
            return null;
        }
        return settlements.get(0);
    }

    public List<SettlementReport> getSettlements(String merchantId) {
        return settlementReportRepository.findByMerchantIdOrderBySettlementDateDesc(merchantId);
    }
}
