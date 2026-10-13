package com.lifeinbox.repository;

import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    Optional<Reminder> findByIdAndUserId(UUID id, UUID userId);

    List<Reminder> findAllByUserIdOrderByDueAtAsc(UUID userId);

    List<Reminder> findAllByUserIdAndStatusOrderByDueAtAsc(UUID userId, ReminderStatus status);

    List<Reminder> findAllByUserIdAndDueAtBetweenOrderByDueAtAsc(UUID userId, Instant start, Instant end);

    List<Reminder> findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(UUID userId, ReminderStatus status, Instant start, Instant end);

    List<Reminder> findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(UUID userId, ReminderStatus status, Instant dueAt);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Reminder r JOIN FETCH r.user u WHERE r.status = :status AND r.dueAt <= :dueBefore ORDER BY r.dueAt ASC")
    List<Reminder> findAllByStatusAndDueAtLessThanEqualWithUser(
            @org.springframework.data.repository.query.Param("status") ReminderStatus status,
            @org.springframework.data.repository.query.Param("dueBefore") Instant dueBefore
    );
}
