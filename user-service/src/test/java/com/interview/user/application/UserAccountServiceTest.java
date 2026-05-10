package com.interview.user.application;

import com.interview.common.domain.Money;
import com.interview.common.outbox.OutboxRepository;
import com.interview.user.domain.UserAccount;
import com.interview.user.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    void createUser_shouldCreateAndSaveUserAccount() {
        String userId = "user-1";
        String username = "alice";

        when(userAccountRepository.existsById(userId)).thenReturn(false);

        UserAccount result = userAccountService.createUser(userId, username);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(username, result.getUsername());
        assertEquals(Money.zero(), result.getBalance());
        verify(userAccountRepository).save(result);
    }

    @Test
    void createUser_whenUserAlreadyExists_shouldThrowIllegalArgumentException() {
        String userId = "user-1";
        String username = "alice";

        when(userAccountRepository.existsById(userId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userAccountService.createUser(userId, username));

        assertEquals("User already exists: " + userId, exception.getMessage());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void topUp_shouldLoadAccountCallTopUpAndSave() {
        String userId = "user-1";
        BigDecimal amount = new BigDecimal("50.00");
        UserAccount account = UserAccount.create(userId, "alice");
        account.clearPendingEvents();

        when(userAccountRepository.findById(userId)).thenReturn(account);

        userAccountService.topUp(userId, amount);

        assertEquals(Money.of(amount), account.getBalance());
        verify(userAccountRepository).save(account);
    }

    @Test
    void deductPayment_shouldLoadAccountCallDeductPaymentAndSave() {
        String userId = "user-1";
        String orderId = "order-1";
        BigDecimal amount = new BigDecimal("30.00");
        UserAccount account = UserAccount.create(userId, "alice");
        account.topUp(Money.of(100.00));
        account.clearPendingEvents();

        when(userAccountRepository.findById(userId)).thenReturn(account);

        userAccountService.deductPayment(userId, orderId, amount);

        assertEquals(Money.of(70.00), account.getBalance());
        verify(userAccountRepository).save(account);
    }

    @Test
    void getUser_shouldReturnUserAccount() {
        String userId = "user-1";
        UserAccount account = UserAccount.create(userId, "alice");

        when(userAccountRepository.findById(userId)).thenReturn(account);

        UserAccount result = userAccountService.getUser(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("alice", result.getUsername());
    }

    @Test
    void getBalance_shouldReturnAccountBalance() {
        String userId = "user-1";
        UserAccount account = UserAccount.create(userId, "alice");
        account.topUp(Money.of(100.00));

        when(userAccountRepository.findById(userId)).thenReturn(account);

        BigDecimal balance = userAccountService.getBalance(userId);

        assertEquals(Money.of(100.00).amount(), balance);
    }
}
