package com.interview.merchant.interfaces;

import com.interview.merchant.application.ProductService;
import com.interview.merchant.domain.Product;
import com.interview.merchant.interfaces.dto.AddInventoryRequest;
import com.interview.merchant.interfaces.dto.CreateProductRequest;
import com.interview.merchant.interfaces.dto.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @PathVariable String merchantId,
            @Valid @RequestBody CreateProductRequest request) {
        Product product = productService.createProduct(
                merchantId, request.getSku(), request.getName(), request.getPrice(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> listProducts(@PathVariable String merchantId) {
        List<Product> products = productService.getProductsByMerchant(merchantId);
        return ResponseEntity.ok(products.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable String merchantId,
            @PathVariable String sku) {
        Product product = productService.getProduct(merchantId, sku);
        return ResponseEntity.ok(toResponse(product));
    }

    @PatchMapping("/{sku}/inventory")
    public ResponseEntity<Void> addInventory(
            @PathVariable String merchantId,
            @PathVariable String sku,
            @Valid @RequestBody AddInventoryRequest request) {
        productService.addInventory(merchantId, sku, request.getQuantity());
        return ResponseEntity.ok().build();
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getSku(),
                product.getMerchantId(),
                product.getName(),
                product.getPrice(),
                product.getQuantity()
        );
    }
}
