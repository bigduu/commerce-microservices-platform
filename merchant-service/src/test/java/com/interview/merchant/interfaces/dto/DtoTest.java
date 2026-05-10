package com.interview.merchant.interfaces.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DtoTest {

    @Test
    void createMerchantRequestHasWorkingGetterAndSetter() {
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setName("Test Merchant");
        assertEquals("Test Merchant", request.getName());
    }

    @Test
    void createProductRequestHasWorkingGettersAndSetters() {
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SKU-001");
        request.setName("Test Product");
        request.setPrice(new BigDecimal("29.99"));
        request.setQuantity(100);

        assertEquals("SKU-001", request.getSku());
        assertEquals("Test Product", request.getName());
        assertEquals(new BigDecimal("29.99"), request.getPrice());
        assertEquals(100, request.getQuantity());
    }

    @Test
    void addInventoryRequestHasWorkingGetterAndSetter() {
        AddInventoryRequest request = new AddInventoryRequest();
        request.setQuantity(50);
        assertEquals(50, request.getQuantity());
    }

    @Test
    void merchantResponseHasWorkingConstructorAndGetters() {
        MerchantResponse response = new MerchantResponse("merchant-123", "Test Merchant", new BigDecimal("100.50"));

        assertEquals("merchant-123", response.getMerchantId());
        assertEquals("Test Merchant", response.getMerchantName());
        assertEquals(new BigDecimal("100.50"), response.getBalance());
    }

    @Test
    void productResponseHasWorkingConstructorAndGetters() {
        ProductResponse response = new ProductResponse("SKU-001", "merchant-123", "Test Product", new BigDecimal("29.99"), 100);

        assertEquals("SKU-001", response.getSku());
        assertEquals("merchant-123", response.getMerchantId());
        assertEquals("Test Product", response.getName());
        assertEquals(new BigDecimal("29.99"), response.getPrice());
        assertEquals(100, response.getQuantity());
    }

    @Test
    void balanceResponseHasWorkingConstructorAndGetters() {
        BalanceResponse response = new BalanceResponse("merchant-123", new BigDecimal("100.50"));

        assertEquals("merchant-123", response.getMerchantId());
        assertEquals(new BigDecimal("100.50"), response.getBalance());
    }
}
