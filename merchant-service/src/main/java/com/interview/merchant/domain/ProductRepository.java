package com.interview.merchant.domain;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends CrudRepository<Product, String> {

    Optional<Product> findBySku(String sku);

    List<Product> findByMerchantId(String merchantId);

    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity - :qty WHERE p.sku = :sku AND p.quantity >= :qty")
    int deductInventory(@Param("sku") String sku, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity + :qty WHERE p.sku = :sku")
    int addInventory(@Param("sku") String sku, @Param("qty") int qty);
}
