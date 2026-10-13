package com.lifeinbox.repository;

import com.lifeinbox.entity.UserNotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationPreferencesRepository extends JpaRepository<UserNotificationPreferences, UUID> {

    Optional<UserNotificationPreferences> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @Query("SELECT p FROM UserNotificationPreferences p JOIN FETCH p.user u WHERE p.emailNotificationsEnabled = true AND p.dailyDigestEnabled = true")
    List<UserNotificationPreferences> findAllEnabledForDailyDigest();
}
