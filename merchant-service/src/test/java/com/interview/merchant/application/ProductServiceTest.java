package com.interview.merchant.application;

import com.interview.common.exception.AggregateNotFoundException;
import com.interview.merchant.domain.Product;
import com.interview.merchant.domain.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProductShouldCreateAndSaveProduct() {
        Product product = Product.create("SKU-001", "M001", "Test Product", new BigDecimal("19.99"), 100);
        when(productRepository.save(any(Product.class))).thenReturn(product);

        Product result = productService.createProduct("M001", "SKU-001", "Test Product", new BigDecimal("19.99"), 100);

        assertNotNull(result);
        assertEquals("SKU-001", result.getSku());
        assertEquals("M001", result.getMerchantId());
        assertEquals("Test Product", result.getName());
        assertEquals(new BigDecimal("19.99"), result.getPrice());
        assertEquals(100, result.getQuantity());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void getProductWhenFoundShouldReturnProduct() {
        Product product = Product.create("SKU-002", "M002", "Widget", new BigDecimal("9.99"), 50);
        when(productRepository.findBySku("SKU-002")).thenReturn(Optional.of(product));

        Product result = productService.getProduct("M002", "SKU-002");

        assertNotNull(result);
        assertEquals("SKU-002", result.getSku());
        assertEquals("Widget", result.getName());
        verify(productRepository).findBySku("SKU-002");
    }

    @Test
    void getProductWhenMerchantDoesNotOwnProductShouldThrowIllegalArgumentException() {
        Product product = Product.create("SKU-002", "OTHER-MERCHANT", "Widget", new BigDecimal("9.99"), 50);
        when(productRepository.findBySku("SKU-002")).thenReturn(Optional.of(product));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> productService.getProduct("M002", "SKU-002")
        );
        assertEquals("Product SKU-002 does not belong to merchant M002", exception.getMessage());
        verify(productRepository).findBySku("SKU-002");
    }

    @Test
    void getProductWhenNotFoundShouldThrowAggregateNotFoundException() {
        when(productRepository.findBySku("SKU-999")).thenReturn(Optional.empty());

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> productService.getProduct("M002", "SKU-999")
        );
        assertEquals("Aggregate not found: SKU-999", exception.getMessage());
        verify(productRepository).findBySku("SKU-999");
    }

    @Test
    void getProductsByMerchantShouldReturnListOfProducts() {
        Product product1 = Product.create("SKU-003", "M003", "Product A", new BigDecimal("10.00"), 20);
        Product product2 = Product.create("SKU-004", "M003", "Product B", new BigDecimal("20.00"), 30);
        when(productRepository.findByMerchantId("M003")).thenReturn(Arrays.asList(product1, product2));

        List<Product> result = productService.getProductsByMerchant("M003");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("SKU-003", result.get(0).getSku());
        assertEquals("SKU-004", result.get(1).getSku());
        verify(productRepository).findByMerchantId("M003");
    }

    @Test
    void addInventoryShouldCallRepositoryUpdate() {
        Product product = Product.create("SKU-005", "M005", "Inventory Product", new BigDecimal("19.99"), 100);
        when(productRepository.findBySku("SKU-005")).thenReturn(Optional.of(product));
        when(productRepository.addInventory("SKU-005", 10)).thenReturn(1);

        productService.addInventory("M005", "SKU-005", 10);

        verify(productRepository).findBySku("SKU-005");
        verify(productRepository).addInventory("SKU-005", 10);
    }

    @Test
    void addInventoryWhenMerchantDoesNotOwnProductShouldThrowIllegalArgumentException() {
        Product product = Product.create("SKU-005", "OTHER-MERCHANT", "Inventory Product", new BigDecimal("19.99"), 100);
        when(productRepository.findBySku("SKU-005")).thenReturn(Optional.of(product));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> productService.addInventory("M005", "SKU-005", 10)
        );
        assertEquals("Product SKU-005 does not belong to merchant M005", exception.getMessage());
        verify(productRepository).findBySku("SKU-005");
        verify(productRepository, never()).addInventory("SKU-005", 10);
    }

    @Test
    void addInventoryWhenProductNotFoundShouldThrowAggregateNotFoundException() {
        when(productRepository.findBySku("SKU-999")).thenReturn(Optional.empty());

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> productService.addInventory("M999", "SKU-999", 10)
        );
        assertEquals("Aggregate not found: SKU-999", exception.getMessage());
        verify(productRepository).findBySku("SKU-999");
        verify(productRepository, never()).addInventory("SKU-999", 10);
    }

    @Test
    void deductInventoryShouldCallRepositoryUpdate() {
        when(productRepository.deductInventory("SKU-006", 5)).thenReturn(1);

        productService.deductInventory("SKU-006", 5);

        verify(productRepository).deductInventory("SKU-006", 5);
    }

    @Test
    void deductInventoryWhenProductNotFoundShouldThrowAggregateNotFoundException() {
        when(productRepository.deductInventory("SKU-999", 5)).thenReturn(0);

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> productService.deductInventory("SKU-999", 5)
        );
        assertEquals("Aggregate not found: SKU-999", exception.getMessage());
        verify(productRepository).deductInventory("SKU-999", 5);
    }

    @Test
    void addInventoryWhenUpdateReturnsZeroShouldThrowAggregateNotFoundException() {
        Product product = Product.create("SKU-005", "M005", "Inventory Product", new BigDecimal("19.99"), 100);
        when(productRepository.findBySku("SKU-005")).thenReturn(Optional.of(product));
        when(productRepository.addInventory("SKU-005", 10)).thenReturn(0);

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> productService.addInventory("M005", "SKU-005", 10)
        );
        assertEquals("Aggregate not found: SKU-005", exception.getMessage());
    }

    @Test
    void releaseInventoryShouldCallRepositoryAddInventory() {
        when(productRepository.addInventory("SKU-007", 3)).thenReturn(1);

        productService.releaseInventory("SKU-007", 3);

        verify(productRepository).addInventory("SKU-007", 3);
    }

    @Test
    void releaseInventoryWhenProductNotFoundShouldThrowAggregateNotFoundException() {
        when(productRepository.addInventory("SKU-999", 3)).thenReturn(0);

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> productService.releaseInventory("SKU-999", 3)
        );
        assertEquals("Aggregate not found: SKU-999", exception.getMessage());
        verify(productRepository).addInventory("SKU-999", 3);
    }
}
