package com.interview.user.infrastructure;

import com.interview.common.domain.Money;
import com.interview.common.event.DomainEvent;
import com.interview.common.exception.AggregateNotFoundException;
import com.interview.common.outbox.OutboxMessage;
import com.interview.common.outbox.OutboxRepository;
import com.interview.user.domain.EventStoreRepository;
import com.interview.user.domain.UserAccount;
import com.interview.user.domain.events.UserAccountEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountRepositoryImplTest {

    @Mock
    private EventStoreRepository eventStoreRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private UserAccountRepositoryImpl userAccountRepository;

    @Test
    void findById_shouldLoadEventsAndReconstructAggregate() {
        String userId = "user-1";
        UserAccountEvents.AccountCreated event = new UserAccountEvents.AccountCreated(userId, "alice");
        event.setVersion(1L);

        when(eventStoreRepository.loadEvents(userId)).thenReturn(List.of(event));

        UserAccount result = userAccountRepository.findById(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("alice", result.getUsername());
        verify(eventStoreRepository).loadEvents(userId);
    }

    @Test
    void findById_shouldThrowAggregateNotFoundWhenNoEvents() {
        String userId = "user-1";

        when(eventStoreRepository.loadEvents(userId)).thenReturn(Collections.emptyList());

        AggregateNotFoundException ex = assertThrows(AggregateNotFoundException.class,
                () -> userAccountRepository.findById(userId));

        assertEquals("Aggregate not found: " + userId, ex.getMessage());
    }

    @Test
    void save_shouldDelegateToEventStoreAndSaveOutboxMessages() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.topUp(Money.of(100.00));

        userAccountRepository.save(account);

        verify(eventStoreRepository).save(account);
        verify(outboxRepository).saveAll(argThat((List<OutboxMessage> list) -> list.size() == 2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_shouldCreateCorrectOutboxMessage() {
        UserAccount account = UserAccount.create("user-1", "alice");

        userAccountRepository.save(account);

        ArgumentCaptor<List<OutboxMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).saveAll(captor.capture());

        List<OutboxMessage> messages = captor.getValue();
        assertEquals(1, messages.size());
        OutboxMessage message = messages.get(0);
        assertNotNull(message.getEventId());
        assertEquals("UserAccount", message.getAggregateType());
        assertEquals("AccountCreated", message.getEventType());
        assertNotNull(message.getPayload());
        assertEquals("user-account.events", message.getTopic());
    }

    @Test
    void existsById_shouldReturnTrueWhenEventsExist() {
        String userId = "user-1";
        UserAccountEvents.AccountCreated event = new UserAccountEvents.AccountCreated(userId, "alice");

        when(eventStoreRepository.loadEvents(userId)).thenReturn(List.of(event));

        boolean result = userAccountRepository.existsById(userId);

        assertTrue(result);
    }

    @Test
    void existsById_shouldReturnFalseWhenNoEvents() {
        String userId = "user-1";

        when(eventStoreRepository.loadEvents(userId)).thenReturn(Collections.emptyList());

        boolean result = userAccountRepository.existsById(userId);

        assertFalse(result);
    }

    @Test
    void existsById_shouldReturnFalseOnException() {
        String userId = "user-1";

        when(eventStoreRepository.loadEvents(userId)).thenThrow(new RuntimeException("DB error"));

        boolean result = userAccountRepository.existsById(userId);

        assertFalse(result);
    }
}
