package com.interview.merchant.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MerchantAccountTest {

    @Test
    void constructorShouldSetFieldsAndBalanceDefaultsToZero() {
        MerchantAccount account = new MerchantAccount("M001", "Acme Corp");

        assertEquals("M001", account.getMerchantId());
        assertEquals("Acme Corp", account.getMerchantName());
        assertEquals(BigDecimal.ZERO, account.getBalance());
    }

    @Test
    void creditShouldIncreaseBalance() {
        MerchantAccount account = new MerchantAccount("M002", "Test Merchant");

        account.credit(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), account.getBalance());
    }

    @Test
    void creditWithMultipleAmountsShouldAccumulate() {
        MerchantAccount account = new MerchantAccount("M003", "Test Merchant");

        account.credit(new BigDecimal("50.00"));
        account.credit(new BigDecimal("75.50"));

        assertEquals(new BigDecimal("125.50"), account.getBalance());
    }

    @Test
    void debitShouldDecreaseBalance() {
        MerchantAccount account = new MerchantAccount("M004", "Test Merchant");
        account.credit(new BigDecimal("200.00"));

        account.debit(new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), account.getBalance());
    }

    @Test
    void debitWithMultipleAmountsShouldAccumulate() {
        MerchantAccount account = new MerchantAccount("M005", "Test Merchant");
        account.credit(new BigDecimal("500.00"));

        account.debit(new BigDecimal("100.00"));
        account.debit(new BigDecimal("50.00"));

        assertEquals(new BigDecimal("350.00"), account.getBalance());
    }

    @Test
    void creditAndDebitCombinedShouldResultInCorrectBalance() {
        MerchantAccount account = new MerchantAccount("M006", "Test Merchant");

        account.credit(new BigDecimal("1000.00"));
        account.debit(new BigDecimal("250.00"));
        account.credit(new BigDecimal("100.00"));
        account.debit(new BigDecimal("50.00"));

        assertEquals(new BigDecimal("800.00"), account.getBalance());
    }

    @Test
    void debitToZeroShouldResultInZeroBalance() {
        MerchantAccount account = new MerchantAccount("M007", "Test Merchant");
        account.credit(new BigDecimal("100.00"));

        account.debit(new BigDecimal("100.00"));

        assertEquals(0, account.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void debitMoreThanBalanceShouldResultInNegativeBalance() {
        MerchantAccount account = new MerchantAccount("M008", "Test Merchant");
        account.credit(new BigDecimal("100.00"));

        account.debit(new BigDecimal("150.00"));

        assertEquals(new BigDecimal("-50.00"), account.getBalance());
    }

    @Test
    void getVersionShouldReturnNullForNewAccount() {
        MerchantAccount account = new MerchantAccount("M009", "Test Merchant");

        assertNull(account.getVersion());
    }
}
