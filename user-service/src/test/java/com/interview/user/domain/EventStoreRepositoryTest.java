package com.interview.user.domain;

import com.interview.common.event.DomainEvent;
import com.interview.common.exception.OptimisticLockException;
import com.interview.user.domain.events.UserAccountEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.PreparedStatementSetter;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventStoreRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private EventStoreRepository eventStoreRepository;

    @Test
    void loadEventsShouldQueryDomainEventsOrderedByVersion() {
        String aggregateId = "user-1";
        UserAccountEvents.AccountCreated event = new UserAccountEvents.AccountCreated(aggregateId, "alice");
        event.setVersion(1L);
        event.setOccurredAt(Instant.now());

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(aggregateId)))
                .thenReturn(List.of(event));

        List<DomainEvent> result = eventStoreRepository.loadEvents(aggregateId);

        assertEquals(1, result.size());
        assertEquals("AccountCreated", result.get(0).getEventType());
        verify(jdbcTemplate).query(
                contains("ORDER BY version ASC"),
                any(RowMapper.class),
                eq(aggregateId)
        );
    }

    @Test
    void loadEventsShouldReturnEmptyListWhenNoEvents() {
        String aggregateId = "user-1";

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(aggregateId)))
                .thenReturn(Collections.emptyList());

        List<DomainEvent> result = eventStoreRepository.loadEvents(aggregateId);

        assertTrue(result.isEmpty());
    }

    @Test
    void saveShouldInsertPendingEvents() {
        UserAccount account = UserAccount.create("user-1", "alice");

        when(jdbcTemplate.batchUpdate(anyString(), any(List.class), anyInt(), any())).thenReturn(new int[][]{{1}});

        eventStoreRepository.save(account);

        verify(jdbcTemplate).batchUpdate(contains("INSERT INTO domain_events"), any(List.class), anyInt(), any());
    }

    @Test
    void saveShouldDoNothingWhenNoPendingEvents() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.clearPendingEvents();

        eventStoreRepository.save(account);

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class), anyInt(), any());
    }

    @Test
    void saveShouldClearPendingEventsAfterInsert() {
        UserAccount account = UserAccount.create("user-1", "alice");

        eventStoreRepository.save(account);

        assertTrue(account.getPendingEvents().isEmpty());
    }

    @Test
    void saveShouldThrowOptimisticLockExceptionOnDuplicateKey() {
        UserAccount account = UserAccount.create("user-1", "alice");

        when(jdbcTemplate.batchUpdate(anyString(), any(List.class), anyInt(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        OptimisticLockException ex = assertThrows(OptimisticLockException.class,
                () -> eventStoreRepository.save(account));

        assertTrue(ex.getMessage().contains("user-1"));
    }

    @Test
    void eventRowMapperShouldMapAccountCreatedEvent() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Instant now = Instant.now();

        // Create a real event and serialize it to get proper JSON
        UserAccountEvents.AccountCreated realEvent = new UserAccountEvents.AccountCreated("user-1", "alice");
        String eventData = com.interview.common.event.EventJsonMapper.toJson(realEvent);

        when(rs.getString("event_type")).thenReturn("AccountCreated");
        when(rs.getString("event_data")).thenReturn(eventData);
        when(rs.getString("aggregate_id")).thenReturn("user-1");
        when(rs.getString("aggregate_type")).thenReturn("UserAccount");
        when(rs.getLong("version")).thenReturn(1L);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(now));

        // Capture the RowMapper from loadEvents and call it directly
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-1")))
                .thenAnswer(invocation -> {
                    RowMapper<DomainEvent> rowMapper = invocation.getArgument(1);
                    DomainEvent event = rowMapper.mapRow(rs, 0);
                    return List.of(event);
                });

        List<DomainEvent> result = eventStoreRepository.loadEvents("user-1");

        assertEquals(1, result.size());
        assertEquals("user-1", result.get(0).getAggregateId());
        assertEquals("UserAccount", result.get(0).getAggregateType());
        assertEquals("AccountCreated", result.get(0).getEventType());
        assertEquals(1L, result.get(0).getVersion());
    }

    @Test
    void eventRowMapperShouldMapAccountToppedUpEvent() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Instant now = Instant.now();
        UserAccountEvents.AccountToppedUp realEvent = new UserAccountEvents.AccountToppedUp("user-1", new BigDecimal("50.00"));
        realEvent.setVersion(2L);
        realEvent.setOccurredAt(now);
        String eventData = com.interview.common.event.EventJsonMapper.toJson(realEvent);

        when(rs.getString("event_type")).thenReturn("AccountToppedUp");
        when(rs.getString("event_data")).thenReturn(eventData);
        when(rs.getString("aggregate_id")).thenReturn("user-1");
        when(rs.getString("aggregate_type")).thenReturn("UserAccount");
        when(rs.getLong("version")).thenReturn(2L);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(now));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-1")))
                .thenAnswer(invocation -> {
                    RowMapper<DomainEvent> rowMapper = invocation.getArgument(1);
                    return List.of(rowMapper.mapRow(rs, 0));
                });

        List<DomainEvent> result = eventStoreRepository.loadEvents("user-1");
        assertEquals(1, result.size());
        assertEquals("AccountToppedUp", result.get(0).getEventType());
        assertEquals(2L, result.get(0).getVersion());
    }

    @Test
    void eventRowMapperShouldMapPaymentDeductedAndRefundedEvents() throws Exception {
        ResultSet rs1 = mock(ResultSet.class);
        Instant now = Instant.now();
        UserAccountEvents.PaymentDeducted deducted = new UserAccountEvents.PaymentDeducted("user-1", "order-1", new BigDecimal("30.00"));
        deducted.setVersion(3L);
        deducted.setOccurredAt(now);
        String json1 = com.interview.common.event.EventJsonMapper.toJson(deducted);

        when(rs1.getString("event_type")).thenReturn("PaymentDeducted");
        when(rs1.getString("event_data")).thenReturn(json1);
        when(rs1.getString("aggregate_id")).thenReturn("user-1");
        when(rs1.getString("aggregate_type")).thenReturn("UserAccount");
        when(rs1.getLong("version")).thenReturn(3L);
        when(rs1.getTimestamp("created_at")).thenReturn(Timestamp.from(now));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-1")))
                .thenAnswer(invocation -> {
                    RowMapper<DomainEvent> rowMapper = invocation.getArgument(1);
                    return List.of(rowMapper.mapRow(rs1, 0));
                });

        List<DomainEvent> result = eventStoreRepository.loadEvents("user-1");
        assertEquals(1, result.size());
        assertEquals("PaymentDeducted", result.get(0).getEventType());
        assertEquals(3L, result.get(0).getVersion());
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventRowMapperShouldMapPaymentRefundFailedEvent() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Instant now = Instant.now();
        UserAccountEvents.PaymentRefundFailed realEvent = new UserAccountEvents.PaymentRefundFailed("user-1", "order-1", new BigDecimal("50.00"));
        realEvent.setVersion(4L);
        realEvent.setOccurredAt(now);
        String eventData = com.interview.common.event.EventJsonMapper.toJson(realEvent);

        when(rs.getString("event_type")).thenReturn("PaymentRefundFailed");
        when(rs.getString("event_data")).thenReturn(eventData);
        when(rs.getString("aggregate_id")).thenReturn("user-1");
        when(rs.getString("aggregate_type")).thenReturn("UserAccount");
        when(rs.getLong("version")).thenReturn(4L);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(now));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-1")))
                .thenAnswer(invocation -> {
                    RowMapper<DomainEvent> rowMapper = invocation.getArgument(1);
                    return List.of(rowMapper.mapRow(rs, 0));
                });

        List<DomainEvent> result = eventStoreRepository.loadEvents("user-1");
        assertEquals(1, result.size());
        assertEquals("PaymentRefundFailed", result.get(0).getEventType());
        assertEquals(4L, result.get(0).getVersion());
    }

    @Test
    void eventRowMapperShouldThrowForUnknownEventType() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        String eventData = "{}";

        when(rs.getString("event_type")).thenReturn("UnknownEvent");
        when(rs.getString("event_data")).thenReturn(eventData);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-1")))
                .thenAnswer(invocation -> {
                    RowMapper<DomainEvent> rowMapper = invocation.getArgument(1);
                    rowMapper.mapRow(rs, 0);
                    return Collections.emptyList();
                });

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> eventStoreRepository.loadEvents("user-1"));
        assertTrue(ex.getMessage().contains("Unknown event type: UnknownEvent"));
    }
}
