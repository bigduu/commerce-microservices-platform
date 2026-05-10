package com.interview.user.interfaces.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void createUserRequestShouldConstructAndGetUsername() {
        CreateUserRequest request = new CreateUserRequest("alice");

        assertEquals("alice", request.getUsername());
    }

    @Test
    void createUserRequestDefaultConstructorShouldAllowSetAndGet() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("bob");

        assertEquals("bob", request.getUsername());
    }

    @Test
    void topUpRequestShouldConstructAndGetAmount() {
        BigDecimal amount = new BigDecimal("50.00");
        TopUpRequest request = new TopUpRequest(amount);

        assertEquals(amount, request.getAmount());
    }

    @Test
    void topUpRequestDefaultConstructorShouldAllowSetAndGet() {
        TopUpRequest request = new TopUpRequest();
        BigDecimal amount = new BigDecimal("25.00");
        request.setAmount(amount);

        assertEquals(amount, request.getAmount());
    }

    @Test
    void balanceResponseShouldConstructAndGetFields() {
        String userId = "user-1";
        BigDecimal balance = new BigDecimal("100.00");
        BalanceResponse response = new BalanceResponse(userId, balance);

        assertEquals(userId, response.getUserId());
        assertEquals(balance, response.getBalance());
    }

    @Test
    void balanceResponseDefaultConstructorShouldAllowSetAndGet() {
        BalanceResponse response = new BalanceResponse();
        response.setUserId("user-2");
        response.setBalance(new BigDecimal("75.00"));

        assertEquals("user-2", response.getUserId());
        assertEquals(new BigDecimal("75.00"), response.getBalance());
    }

    @Test
    void userResponseShouldConstructAndGetFields() {
        String userId = "user-1";
        String username = "alice";
        BigDecimal balance = new BigDecimal("200.00");
        UserResponse response = new UserResponse(userId, username, balance);

        assertEquals(userId, response.getUserId());
        assertEquals(username, response.getUsername());
        assertEquals(balance, response.getBalance());
    }

    @Test
    void userResponseDefaultConstructorShouldAllowSetAndGet() {
        UserResponse response = new UserResponse();
        response.setUserId("user-3");
        response.setUsername("charlie");
        response.setBalance(new BigDecimal("30.00"));

        assertEquals("user-3", response.getUserId());
        assertEquals("charlie", response.getUsername());
        assertEquals(new BigDecimal("30.00"), response.getBalance());
    }
}
