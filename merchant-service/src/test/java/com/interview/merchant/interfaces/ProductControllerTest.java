package com.interview.merchant.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.merchant.application.ProductService;
import com.interview.merchant.domain.Product;
import com.interview.merchant.interfaces.dto.AddInventoryRequest;
import com.interview.merchant.interfaces.dto.CreateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createProductReturns201() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SKU-001");
        request.setName("Test Product");
        request.setPrice(new BigDecimal("29.99"));
        request.setQuantity(100);

        Product product = Product.create("SKU-001", "merchant-123", "Test Product", new BigDecimal("29.99"), 100);
        when(productService.createProduct(eq("merchant-123"), eq("SKU-001"), eq("Test Product"), eq(new BigDecimal("29.99")), eq(100)))
                .thenReturn(product);

        mockMvc.perform(post("/api/v1/merchants/merchant-123/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.merchantId").value("merchant-123"))
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.price").value(29.99))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    void listProductsReturns200() throws Exception {
        Product product1 = Product.create("SKU-001", "merchant-123", "Product One", new BigDecimal("10.00"), 50);
        Product product2 = Product.create("SKU-002", "merchant-123", "Product Two", new BigDecimal("20.00"), 30);
        when(productService.getProductsByMerchant("merchant-123")).thenReturn(List.of(product1, product2));

        mockMvc.perform(get("/api/v1/merchants/merchant-123/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$[0].name").value("Product One"))
                .andExpect(jsonPath("$[1].sku").value("SKU-002"))
                .andExpect(jsonPath("$[1].name").value("Product Two"));
    }

    @Test
    void getProductBySkuReturns200() throws Exception {
        Product product = Product.create("SKU-001", "merchant-123", "Test Product", new BigDecimal("29.99"), 100);
        when(productService.getProduct("merchant-123", "SKU-001")).thenReturn(product);

        mockMvc.perform(get("/api/v1/merchants/merchant-123/products/SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.merchantId").value("merchant-123"))
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.price").value(29.99))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    void getProductBySkuWhenMerchantMismatchReturns400() throws Exception {
        when(productService.getProduct("merchant-123", "SKU-001"))
                .thenThrow(new IllegalArgumentException("Product SKU-001 does not belong to merchant merchant-123"));

        mockMvc.perform(get("/api/v1/merchants/merchant-123/products/SKU-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Product SKU-001 does not belong to merchant merchant-123"));
    }

    @Test
    void addInventoryReturns200() throws Exception {
        AddInventoryRequest request = new AddInventoryRequest();
        request.setQuantity(50);

        doNothing().when(productService).addInventory("merchant-123", "SKU-001", 50);

        mockMvc.perform(patch("/api/v1/merchants/merchant-123/products/SKU-001/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
