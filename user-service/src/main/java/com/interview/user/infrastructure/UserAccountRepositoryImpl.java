package com.interview.user.infrastructure;

import com.interview.common.domain.AggregateRoot;
import com.interview.common.event.DomainEvent;
import com.interview.common.event.EventJsonMapper;
import com.interview.common.exception.AggregateNotFoundException;
import com.interview.common.outbox.OutboxMessage;
import com.interview.common.outbox.OutboxRepository;
import com.interview.user.domain.EventStoreRepository;
import com.interview.user.domain.UserAccount;
import com.interview.user.domain.UserAccountRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public class UserAccountRepositoryImpl implements UserAccountRepository {

    private final EventStoreRepository eventStoreRepository;
    private final OutboxRepository outboxRepository;

    public UserAccountRepositoryImpl(EventStoreRepository eventStoreRepository,
                                     OutboxRepository outboxRepository) {
        this.eventStoreRepository = eventStoreRepository;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public UserAccount findById(String userId) {
        List<DomainEvent> events = eventStoreRepository.loadEvents(userId);
        if (events.isEmpty()) {
            throw new AggregateNotFoundException(userId);
        }
        UserAccount account = new UserAccount();
        account.loadFromEvents(events);
        return account;
    }

    @Override
    @Transactional
    public void save(UserAccount account) {
        List<DomainEvent> pendingEvents = List.copyOf(account.getPendingEvents());

        eventStoreRepository.save(account);

        List<OutboxMessage> messages = new ArrayList<>(pendingEvents.size());
        for (DomainEvent event : pendingEvents) {
            String payload = EventJsonMapper.toJson(event);
            messages.add(new OutboxMessage(
                    event.getEventId(),
                    event.getAggregateType(),
                    event.getEventType(),
                    payload,
                    "user-account.events"
            ));
        }
        outboxRepository.saveAll(messages);
    }

    @Override
    public boolean existsById(String userId) {
        try {
            List<DomainEvent> events = eventStoreRepository.loadEvents(userId);
            return !events.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
