package com.interview.merchant.application;

import com.interview.common.exception.AggregateNotFoundException;
import com.interview.merchant.domain.MerchantAccount;
import com.interview.merchant.domain.MerchantAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantAccountRepository merchantAccountRepository;

    @InjectMocks
    private MerchantService merchantService;

    @Test
    void createMerchant_shouldSaveAndReturnNewAccount() {
        MerchantAccount account = new MerchantAccount("M001", "Acme Corp");
        when(merchantAccountRepository.save(any(MerchantAccount.class))).thenReturn(account);

        MerchantAccount result = merchantService.createMerchant("M001", "Acme Corp");

        assertNotNull(result);
        assertEquals("M001", result.getMerchantId());
        assertEquals("Acme Corp", result.getMerchantName());
        verify(merchantAccountRepository).save(any(MerchantAccount.class));
    }

    @Test
    void getMerchant_whenFound_shouldReturnAccount() {
        MerchantAccount account = new MerchantAccount("M002", "Test Merchant");
        when(merchantAccountRepository.findById("M002")).thenReturn(Optional.of(account));

        MerchantAccount result = merchantService.getMerchant("M002");

        assertNotNull(result);
        assertEquals("M002", result.getMerchantId());
        assertEquals("Test Merchant", result.getMerchantName());
        verify(merchantAccountRepository).findById("M002");
    }

    @Test
    void getMerchant_whenNotFound_shouldThrowAggregateNotFoundException() {
        when(merchantAccountRepository.findById("M999")).thenReturn(Optional.empty());

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> merchantService.getMerchant("M999")
        );
        assertEquals("Aggregate not found: M999", exception.getMessage());
        verify(merchantAccountRepository).findById("M999");
    }

    @Test
    void getBalance_shouldReturnBalance() {
        MerchantAccount account = new MerchantAccount("M003", "Test Merchant");
        account.credit(new BigDecimal("500.00"));
        when(merchantAccountRepository.findById("M003")).thenReturn(Optional.of(account));

        BigDecimal balance = merchantService.getBalance("M003");

        assertEquals(new BigDecimal("500.00"), balance);
        verify(merchantAccountRepository).findById("M003");
    }

    @Test
    void creditMerchant_shouldAddAmountToBalance() {
        MerchantAccount account = new MerchantAccount("M004", "Test Merchant");
        account.credit(new BigDecimal("100.00"));
        when(merchantAccountRepository.findById("M004")).thenReturn(Optional.of(account));
        when(merchantAccountRepository.save(any(MerchantAccount.class))).thenReturn(account);

        merchantService.creditMerchant("M004", new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), account.getBalance());
        verify(merchantAccountRepository).findById("M004");
        verify(merchantAccountRepository).save(account);
    }

    @Test
    void debitMerchant_shouldSubtractAmountFromBalance() {
        MerchantAccount account = new MerchantAccount("M005", "Test Merchant");
        account.credit(new BigDecimal("200.00"));
        when(merchantAccountRepository.findById("M005")).thenReturn(Optional.of(account));
        when(merchantAccountRepository.save(any(MerchantAccount.class))).thenReturn(account);

        merchantService.debitMerchant("M005", new BigDecimal("75.00"));

        assertEquals(new BigDecimal("125.00"), account.getBalance());
        verify(merchantAccountRepository).findById("M005");
        verify(merchantAccountRepository).save(account);
    }

    @Test
    void getBalance_whenMerchantNotFound_shouldThrowAggregateNotFoundException() {
        when(merchantAccountRepository.findById("M999")).thenReturn(Optional.empty());

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> merchantService.getBalance("M999")
        );
        assertEquals("Aggregate not found: M999", exception.getMessage());
    }

    @Test
    void creditMerchant_whenMerchantNotFound_shouldThrowAggregateNotFoundException() {
        when(merchantAccountRepository.findById("M999")).thenReturn(Optional.empty());

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> merchantService.creditMerchant("M999", new BigDecimal("100.00"))
        );
        assertEquals("Aggregate not found: M999", exception.getMessage());
    }

    @Test
    void debitMerchant_whenMerchantNotFound_shouldThrowAggregateNotFoundException() {
        when(merchantAccountRepository.findById("M999")).thenReturn(Optional.empty());

        AggregateNotFoundException exception = assertThrows(
                AggregateNotFoundException.class,
                () -> merchantService.debitMerchant("M999", new BigDecimal("50.00"))
        );
        assertEquals("Aggregate not found: M999", exception.getMessage());
    }
}
