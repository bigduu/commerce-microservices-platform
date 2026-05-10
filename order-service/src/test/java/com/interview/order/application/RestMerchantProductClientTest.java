package com.interview.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class RestMerchantProductClientTest {

    private HttpServer server;
    private RestMerchantProductClient client;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(null);
        server.start();
        int port = server.getAddress().getPort();
        client = new RestMerchantProductClient(
                RestClient.builder(),
                "http://localhost:" + port,
                2000,
                3000,
                30,
                1000
        );
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void getUnitPriceShouldReturnPriceWhenProductExists() throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of(
                "sku", "SKU-001",
                "merchantId", "M001",
                "name", "Widget",
                "price", 29.99,
                "quantity", 10
        ));

        server.createContext("/api/v1/merchants/M001/products/SKU-001", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] response = json.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        BigDecimal price = client.getUnitPrice("M001", "SKU-001");

        assertEquals(new BigDecimal("29.99"), price);
    }

    @Test
    void getUnitPriceShouldThrowWhenServerReturns404() {
        server.createContext("/api/v1/merchants/M001/products/SKU-999", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> client.getUnitPrice("M001", "SKU-999"));
        assertTrue(ex.getMessage().contains("Unable to resolve product price"));
    }

    @Test
    void getUnitPriceShouldThrowWhenProductHasNullPrice() throws Exception {
        String json = "{\"sku\":\"SKU-001\",\"merchantId\":\"M001\",\"name\":\"Widget\",\"price\":null,\"quantity\":10}";

        server.createContext("/api/v1/merchants/M001/products/SKU-001", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] response = json.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> client.getUnitPrice("M001", "SKU-001"));
        assertTrue(ex.getMessage().contains("Resolved product price must be positive"));
    }

    @Test
    void getUnitPriceShouldThrowWhenProductHasZeroPrice() throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of(
                "sku", "SKU-001",
                "merchantId", "M001",
                "name", "Widget",
                "price", 0,
                "quantity", 10
        ));

        server.createContext("/api/v1/merchants/M001/products/SKU-001", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] response = json.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> client.getUnitPrice("M001", "SKU-001"));
        assertTrue(ex.getMessage().contains("Resolved product price must be positive"));
    }

    @Test
    void getUnitPriceShouldThrowWhenProductHasNegativePrice() throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of(
                "sku", "SKU-001",
                "merchantId", "M001",
                "name", "Widget",
                "price", -5.00,
                "quantity", 10
        ));

        server.createContext("/api/v1/merchants/M001/products/SKU-001", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] response = json.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> client.getUnitPrice("M001", "SKU-001"));
        assertTrue(ex.getMessage().contains("Resolved product price must be positive"));
    }

    @Test
    void getUnitPriceShouldUseCacheOnSecondCall() throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of(
                "sku", "SKU-CACHE",
                "merchantId", "M001",
                "name", "CachedWidget",
                "price", 10.00,
                "quantity", 5
        ));

        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        server.createContext("/api/v1/merchants/M001/products/SKU-CACHE", exchange -> {
            callCount.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] response = json.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        BigDecimal price1 = client.getUnitPrice("M001", "SKU-CACHE");
        assertEquals(0, new BigDecimal("10.00").compareTo(price1));

        BigDecimal price2 = client.getUnitPrice("M001", "SKU-CACHE");
        assertEquals(0, new BigDecimal("10.00").compareTo(price2));

        assertEquals(1, callCount.get());
    }

    @Test
    void getUnitPriceShouldThrowWhenServerReturns500() {
        server.createContext("/api/v1/merchants/M001/products/SKU-500", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> client.getUnitPrice("M001", "SKU-500"));
        assertTrue(ex.getMessage().contains("Unable to resolve product price"));
    }
}
