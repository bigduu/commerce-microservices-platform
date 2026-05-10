package com.interview.user.interfaces;

import com.interview.common.exception.AggregateNotFoundException;
import com.interview.common.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleInsufficientBalance_shouldReturn400() {
        InsufficientBalanceException ex = new InsufficientBalanceException("Balance too low");

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientBalance(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Insufficient balance", response.getBody().get("error"));
        assertEquals("Balance too low", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleAggregateNotFound_shouldReturn404() {
        AggregateNotFoundException ex = new AggregateNotFoundException("user-123");

        ResponseEntity<Map<String, Object>> response = handler.handleAggregateNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Not found", response.getBody().get("error"));
        assertEquals("Aggregate not found: user-123", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleIllegalArgument_shouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad request", response.getBody().get("error"));
        assertEquals("Invalid input", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleGeneric_shouldReturn500() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal server error", response.getBody().get("error"));
        assertEquals("Something went wrong", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }
}
