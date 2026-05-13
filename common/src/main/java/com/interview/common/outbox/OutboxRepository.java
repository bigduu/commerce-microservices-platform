package com.interview.common.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM OutboxMessage o WHERE o.publishedAt IS NOT NULL")
    int deleteByPublishedAtIsNotNull();
}
