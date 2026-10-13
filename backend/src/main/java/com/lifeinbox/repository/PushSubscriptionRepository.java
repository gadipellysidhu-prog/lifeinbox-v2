package com.lifeinbox.repository;

import com.lifeinbox.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByEndpointAndActiveTrue(String endpoint);

    Optional<PushSubscription> findByIdAndUserId(UUID id, UUID userId);

    Optional<PushSubscription> findByUserIdAndEndpoint(UUID userId, String endpoint);

    List<PushSubscription> findAllByUserId(UUID userId);

    List<PushSubscription> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<PushSubscription> findAllByUserIdAndActiveTrue(UUID userId);

    boolean existsByEndpoint(String endpoint);

    long countByUserIdAndActiveTrue(UUID userId);
}
