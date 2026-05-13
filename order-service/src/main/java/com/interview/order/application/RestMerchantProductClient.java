package com.interview.order.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Component
public class RestMerchantProductClient implements MerchantProductClient {

    private final RestClient restClient;
    private final Cache<String, BigDecimal> priceCache;

    public RestMerchantProductClient(
            @Value("${merchant-service.base-url:http://localhost:8082}") String merchantServiceBaseUrl,
            @Value("${merchant-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${merchant-service.read-timeout-ms:3000}") int readTimeoutMs,
            @Value("${merchant-service.price-cache-ttl-seconds:30}") int cacheTtlSeconds,
            @Value("${merchant-service.price-cache-max-size:1000}") int cacheMaxSize
    ) {
        var connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50);
        connectionManager.setDefaultMaxPerRoute(20);
        var requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();
        var httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        this.restClient = RestClient.builder()
                .baseUrl(merchantServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.priceCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    @Override
    public BigDecimal getUnitPrice(String merchantId, String sku) {
        String cacheKey = merchantId + ":" + sku;
        try {
            return priceCache.get(cacheKey, key -> fetchUnitPrice(merchantId, sku));
        } catch (CompletionException e) {
            if (e.getCause() instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException(
                    "Unable to resolve product price for merchant " + merchantId + " and sku " + sku, e
            );
        }
    }

    private BigDecimal fetchUnitPrice(String merchantId, String sku) {
        try {
            ProductView product = restClient.get()
                    .uri("/api/v1/merchants/{merchantId}/products/{sku}", merchantId, sku)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalArgumentException(
                                "Unable to resolve product price for merchant " + merchantId + " and sku " + sku
                        );
                    })
                    .body(ProductView.class);

            if (product == null || product.price() == null || product.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Resolved product price must be positive for merchant " + merchantId + " and sku " + sku
                );
            }
            return product.price();
        } catch (RestClientResponseException ex) {
            throw new IllegalArgumentException(
                    "Unable to resolve product price for merchant " + merchantId + " and sku " + sku,
                    ex
            );
        }
    }

    private record ProductView(
            String sku,
            String merchantId,
            String name,
            BigDecimal price,
            Integer quantity
    ) {
    }
}
