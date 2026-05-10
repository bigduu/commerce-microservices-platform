package com.interview.settlement.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementReportRepository extends JpaRepository<SettlementReport, String> {

    Optional<SettlementReport> findByMerchantIdAndSettlementDate(String merchantId, LocalDate date);

    List<SettlementReport> findByMerchantIdOrderBySettlementDateDesc(String merchantId);

    boolean existsByMerchantIdAndSettlementDate(String merchantId, LocalDate date);
}
