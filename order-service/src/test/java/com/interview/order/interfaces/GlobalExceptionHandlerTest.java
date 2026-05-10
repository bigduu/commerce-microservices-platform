package com.interview.order.interfaces;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().status());
        assertEquals("bad input", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void handleIllegalState_returns409() {
        IllegalStateException ex = new IllegalStateException("conflict state");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalState(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().status());
        assertEquals("conflict state", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void handleValidation_returns400WithFieldErrors() {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "orderId", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Validation failed"));
        assertTrue(response.getBody().message().contains("orderId"));
        assertTrue(response.getBody().message().contains("must not be blank"));
    }

    @Test
    void handleGeneric_returns500() {
        Exception ex = new RuntimeException("something broke");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().status());
        assertEquals("Internal server error", response.getBody().message());
    }
}
