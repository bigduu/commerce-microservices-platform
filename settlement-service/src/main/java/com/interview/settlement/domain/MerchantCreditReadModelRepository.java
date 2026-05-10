package com.interview.settlement.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MerchantCreditReadModelRepository extends JpaRepository<MerchantCreditReadModel, Long> {

    List<MerchantCreditReadModel> findByMerchantIdAndCreditedAtBetween(String merchantId, Instant start, Instant end);

    boolean existsByEventId(String eventId);
}
