package com.interview.settlement.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
public interface OrderReadModelRepository extends JpaRepository<OrderReadModel, Long> {

    List<OrderReadModel> findByMerchantIdAndCompletedAtBetween(String merchantId, Instant start, Instant end);

    boolean existsByOrderId(String orderId);

    @Query("SELECT DISTINCT o.merchantId FROM OrderReadModel o")
    Set<String> findDistinctMerchantIds();
}
