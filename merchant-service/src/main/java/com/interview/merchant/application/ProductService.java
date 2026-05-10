package com.interview.merchant.application;

import com.interview.common.exception.AggregateNotFoundException;
import com.interview.merchant.domain.Product;
import com.interview.merchant.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product createProduct(String merchantId, String sku, String name, BigDecimal price, int quantity) {
        Product product = Product.create(sku, merchantId, name, price, quantity);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProduct(String merchantId, String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new AggregateNotFoundException(sku));
        validateMerchantOwnership(merchantId, product);
        return product;
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByMerchant(String merchantId) {
        return productRepository.findByMerchantId(merchantId);
    }

    public void addInventory(String merchantId, String sku, int quantity) {
        Product product = getProduct(merchantId, sku);
        int updated = productRepository.addInventory(product.getSku(), quantity);
        if (updated == 0) {
            throw new AggregateNotFoundException(sku);
        }
    }

    public void deductInventory(String sku, int quantity) {
        int updated = productRepository.deductInventory(sku, quantity);
        if (updated == 0) {
            throw new AggregateNotFoundException(sku);
        }
    }

    public void releaseInventory(String sku, int quantity) {
        int updated = productRepository.addInventory(sku, quantity);
        if (updated == 0) {
            throw new AggregateNotFoundException(sku);
        }
    }

    private void validateMerchantOwnership(String merchantId, Product product) {
        if (!product.getMerchantId().equals(merchantId)) {
            throw new IllegalArgumentException(
                    "Product " + product.getSku() + " does not belong to merchant " + merchantId
            );
        }
    }
}
