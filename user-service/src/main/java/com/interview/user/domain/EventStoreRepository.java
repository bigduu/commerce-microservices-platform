package com.interview.user.domain;

import com.interview.common.domain.AggregateRoot;
import com.interview.common.event.DomainEvent;
import com.interview.common.event.EventJsonMapper;
import com.interview.common.exception.OptimisticLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Repository
public class EventStoreRepository {

    private static final Map<String, Class<? extends DomainEvent>> EVENT_CLASS_CACHE = Map.of(
            "AccountCreated", com.interview.user.domain.events.UserAccountEvents.AccountCreated.class,
            "AccountToppedUp", com.interview.user.domain.events.UserAccountEvents.AccountToppedUp.class,
            "PaymentDeducted", com.interview.user.domain.events.UserAccountEvents.PaymentDeducted.class,
            "PaymentDeductFailed", com.interview.user.domain.events.UserAccountEvents.PaymentDeductFailed.class,
            "PaymentRefunded", com.interview.user.domain.events.UserAccountEvents.PaymentRefunded.class,
            "PaymentRefundFailed", com.interview.user.domain.events.UserAccountEvents.PaymentRefundFailed.class
    );

    private final JdbcTemplate jdbcTemplate;
    private final EventRowMapper eventRowMapper = new EventRowMapper();

    public EventStoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DomainEvent> loadEvents(String aggregateId) {
        String sql = "SELECT aggregate_id, aggregate_type, event_type, event_data, version, created_at " +
                     "FROM domain_events WHERE aggregate_id = ? ORDER BY version ASC";
        return jdbcTemplate.query(sql, eventRowMapper, aggregateId);
    }

    public void save(AggregateRoot aggregate) {
        List<DomainEvent> pendingEvents = aggregate.getPendingEvents();
        if (pendingEvents.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO domain_events (aggregate_id, aggregate_type, event_type, event_data, version, created_at) " +
                     "VALUES (?, ?, ?, ?::jsonb, ?, ?)";

        try {
            jdbcTemplate.batchUpdate(sql, pendingEvents, pendingEvents.size(),
                    (ps, event) -> {
                        ps.setString(1, event.getAggregateId());
                        ps.setString(2, event.getAggregateType());
                        ps.setString(3, event.getEventType());
                        ps.setString(4, EventJsonMapper.toJson(event));
                        ps.setLong(5, event.getVersion());
                        ps.setTimestamp(6, Timestamp.from(event.getOccurredAt()));
                    });
        } catch (DuplicateKeyException e) {
            DomainEvent event = pendingEvents.get(0);
            throw new OptimisticLockException(event.getAggregateId(), event.getVersion());
        }

        aggregate.clearPendingEvents();
    }

    private static class EventRowMapper implements RowMapper<DomainEvent> {
        @Override
        public DomainEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            String eventType = rs.getString("event_type");
            String eventData = rs.getString("event_data");

            try {
                Class<? extends DomainEvent> clazz = EVENT_CLASS_CACHE.get(eventType);
                if (clazz == null) {
                    throw new RuntimeException("Unknown event type: " + eventType);
                }
                DomainEvent event = EventJsonMapper.fromJson(eventData, clazz);
                event.setAggregateId(rs.getString("aggregate_id"));
                event.setAggregateType(rs.getString("aggregate_type"));
                event.setVersion(rs.getLong("version"));
                event.setOccurredAt(rs.getTimestamp("created_at").toInstant());
                return event;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize event: " + eventType, e);
            }
        }
    }
}
