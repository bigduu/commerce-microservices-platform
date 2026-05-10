package com.interview.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findById(String orderId);

    List<Order> findByUserId(String userId);

    List<Order> findByMerchantIdAndStatus(String merchantId, OrderStatus status);
}
