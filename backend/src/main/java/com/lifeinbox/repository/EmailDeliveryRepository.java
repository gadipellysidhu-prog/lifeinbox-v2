package com.lifeinbox.repository;

import com.lifeinbox.entity.EmailDelivery;
import com.lifeinbox.entity.EmailDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {

    Optional<EmailDelivery> findByDeduplicationKey(String deduplicationKey);

    boolean existsByDeduplicationKey(String deduplicationKey);

    @Query("SELECT d FROM EmailDelivery d " +
            "JOIN FETCH d.user " +
            "LEFT JOIN FETCH d.reminder " +
            "WHERE d.status = :status AND d.attemptCount < :maxAttempts AND (d.lastAttemptAt IS NULL OR d.lastAttemptAt <= :eligibleBefore)")
    List<EmailDelivery> findEligibleForRetry(
            @Param("status") EmailDeliveryStatus status,
            @Param("maxAttempts") int maxAttempts,
            @Param("eligibleBefore") Instant eligibleBefore
    );

    List<EmailDelivery> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM EmailDelivery d WHERE d.user.id = :userId AND d.deduplicationKey LIKE CONCAT(:prefix, '%')")
    void deleteByUserIdAndDeduplicationKeyStartingWith(@Param("userId") UUID userId, @Param("prefix") String prefix);
}
