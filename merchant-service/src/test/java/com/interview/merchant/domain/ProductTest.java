package com.interview.merchant.domain;

import com.interview.common.exception.InsufficientInventoryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    @Test
    void create_shouldSetAllFieldsCorrectly() {
        Product product = Product.create("SKU-001", "M001", "Test Product", new BigDecimal("19.99"), 100);

        assertEquals("SKU-001", product.getSku());
        assertEquals("M001", product.getMerchantId());
        assertEquals("Test Product", product.getName());
        assertEquals(new BigDecimal("19.99"), product.getPrice());
        assertEquals(100, product.getQuantity());
    }

    @Test
    void addInventory_withValidQty_shouldIncreaseQuantity() {
        Product product = Product.create("SKU-002", "M002", "Widget", new BigDecimal("9.99"), 10);

        product.addInventory(5);

        assertEquals(15, product.getQuantity());
    }

    @Test
    void addInventory_withZeroQty_shouldThrowIllegalArgumentException() {
        Product product = Product.create("SKU-003", "M003", "Gadget", new BigDecimal("29.99"), 20);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> product.addInventory(0));
        assertEquals("Quantity must be positive", exception.getMessage());
    }

    @Test
    void addInventory_withNegativeQty_shouldThrowIllegalArgumentException() {
        Product product = Product.create("SKU-004", "M004", "Thing", new BigDecimal("5.00"), 30);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> product.addInventory(-5));
        assertEquals("Quantity must be positive", exception.getMessage());
    }

    @Test
    void deductInventory_withValidQty_shouldDecreaseQuantity() {
        Product product = Product.create("SKU-005", "M005", "Item", new BigDecimal("15.00"), 50);

        product.deductInventory(20);

        assertEquals(30, product.getQuantity());
    }

    @Test
    void deductInventory_withQtyGreaterThanQuantity_shouldThrowInsufficientInventoryException() {
        Product product = Product.create("SKU-006", "M006", "Item", new BigDecimal("15.00"), 10);

        InsufficientInventoryException exception = assertThrows(
                InsufficientInventoryException.class,
                () -> product.deductInventory(11)
        );
        assertEquals("Insufficient inventory for SKU: SKU-006", exception.getMessage());
    }

    @Test
    void deductInventory_toZero_shouldWorkCorrectly() {
        Product product = Product.create("SKU-007", "M007", "Item", new BigDecimal("15.00"), 5);

        product.deductInventory(5);

        assertEquals(0, product.getQuantity());
    }

    @Test
    void getVersion_shouldReturnNullForNewProduct() {
        Product product = Product.create("SKU-008", "M008", "Item", new BigDecimal("15.00"), 5);

        assertNull(product.getVersion());
    }
}
