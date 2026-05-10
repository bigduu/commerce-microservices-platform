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
    void getUnitPrice_shouldReturnPrice_whenProductExists() throws Exception {
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
    void getUnitPrice_shouldThrow_whenServerReturns404() {
        server.createContext("/api/v1/merchants/M001/products/SKU-999", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> client.getUnitPrice("M001", "SKU-999"));
        assertTrue(ex.getMessage().contains("Unable to resolve product price"));
    }

    @Test
    void getUnitPrice_shouldThrow_whenProductHasNullPrice() throws Exception {
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
    void getUnitPrice_shouldThrow_whenProductHasZeroPrice() throws Exception {
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
    void getUnitPrice_shouldThrow_whenProductHasNegativePrice() throws Exception {
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
}
