package com.interview.merchant.interfaces;

import com.interview.common.exception.AggregateNotFoundException;
import com.interview.common.exception.InsufficientInventoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void handleNotFound_returns404WithErrorResponse() {
        AggregateNotFoundException ex = new AggregateNotFoundException("merchant-123");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().status());
        assertEquals("Aggregate not found: merchant-123", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void handleInsufficientInventory_returns409WithErrorResponse() {
        InsufficientInventoryException ex = new InsufficientInventoryException("Not enough inventory for SKU-001");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleInsufficientInventory(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().status());
        assertEquals("Not enough inventory for SKU-001", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void handleIllegalArgument_returns400WithErrorResponse() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument provided");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().status());
        assertEquals("Invalid argument provided", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void handleValidation_returns400WithFieldErrors() {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "name", "Name is required"));
        bindingResult.addError(new FieldError("target", "price", "Price must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Name is required", response.getBody().get("name"));
        assertEquals("Price must be positive", response.getBody().get("price"));
    }

    @Test
    void handleGeneric_returns500WithErrorResponse() {
        Exception ex = new RuntimeException("Unexpected error occurred");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().status());
        assertEquals("Unexpected error occurred", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }
}
