package com.lifeinbox.service;

import com.lifeinbox.dto.NotificationPreferencesResponse;
import com.lifeinbox.dto.UpdateNotificationPreferencesRequest;
import com.lifeinbox.entity.User;
import com.lifeinbox.entity.UserNotificationPreferences;
import com.lifeinbox.mapper.NotificationPreferencesMapper;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import com.lifeinbox.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferencesServiceTest {

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private NotificationPreferencesMapper preferencesMapper = new NotificationPreferencesMapper();

    @InjectMocks
    private NotificationPreferencesService preferencesService;

    private UUID userId;
    private User sampleUser;
    private UserNotificationPreferences samplePreferences;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .name("David")
                .email("david@example.com")
                .build();

        samplePreferences = UserNotificationPreferences.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .emailNotificationsEnabled(true)
                .reminderEmailsEnabled(true)
                .dailyDigestEnabled(true)
                .dailyDigestTime(LocalTime.of(8, 0))
                .timezone("Asia/Kolkata")
                .build();
    }

    @Test
    @DisplayName("getPreferences returns existing user preferences")
    void testGetPreferencesExisting() {
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(samplePreferences));

        NotificationPreferencesResponse response = preferencesService.getPreferences(userId);

        assertThat(response.emailNotificationsEnabled()).isTrue();
        assertThat(response.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(response.dailyDigestTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("getPreferences creates defaults if user has no preferences yet")
    void testGetPreferencesCreatesDefault() {
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(preferencesRepository.save(any(UserNotificationPreferences.class))).thenAnswer(i -> i.getArgument(0));

        NotificationPreferencesResponse response = preferencesService.getPreferences(userId);

        assertThat(response).isNotNull();
        assertThat(response.timezone()).isEqualTo("UTC");
        assertThat(response.dailyDigestTime()).isEqualTo(LocalTime.of(8, 0));
        verify(preferencesRepository).save(any(UserNotificationPreferences.class));
    }

    @Test
    @DisplayName("updatePreferences saves valid preference changes")
    void testUpdatePreferencesValid() {
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(samplePreferences));
        when(preferencesRepository.save(any(UserNotificationPreferences.class))).thenAnswer(i -> i.getArgument(0));

        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest(
                false,
                false,
                true,
                LocalTime.of(9, 30),
                "America/New_York"
        );

        NotificationPreferencesResponse response = preferencesService.updatePreferences(userId, request);

        assertThat(response.emailNotificationsEnabled()).isFalse();
        assertThat(response.reminderEmailsEnabled()).isFalse();
        assertThat(response.dailyDigestEnabled()).isTrue();
        assertThat(response.dailyDigestTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(response.timezone()).isEqualTo("America/New_York");
        assertThat(response.pushNotificationsEnabled()).isTrue();
    }

    @Test
    @DisplayName("updatePreferences updates pushNotificationsEnabled toggle independently")
    void testUpdatePreferencesPushNotifications() {
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(samplePreferences));
        when(preferencesRepository.save(any(UserNotificationPreferences.class))).thenAnswer(i -> i.getArgument(0));

        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest(
                true,
                true,
                true,
                LocalTime.of(8, 0),
                "UTC",
                false // push disabled
        );

        NotificationPreferencesResponse response = preferencesService.updatePreferences(userId, request);

        assertThat(response.emailNotificationsEnabled()).isTrue();
        assertThat(response.pushNotificationsEnabled()).isFalse();
    }

    @Test
    @DisplayName("updatePreferences rejects invalid IANA timezone")
    void testUpdatePreferencesInvalidTimezone() {
        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest(
                true,
                true,
                true,
                LocalTime.of(8, 0),
                "Invalid/Planet_Mars"
        );

        assertThatThrownBy(() -> preferencesService.updatePreferences(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid IANA timezone");
    }

    @Test
    @DisplayName("getZoneIdForUser returns ZoneId from stored preferences or UTC fallback")
    void testGetZoneIdForUser() {
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(samplePreferences));

        ZoneId zoneId = preferencesService.getZoneIdForUser(userId);
        assertThat(zoneId).isEqualTo(ZoneId.of("Asia/Kolkata"));

        UUID unknownUser = UUID.randomUUID();
        when(preferencesRepository.findByUserId(unknownUser)).thenReturn(Optional.empty());
        assertThat(preferencesService.getZoneIdForUser(unknownUser)).isEqualTo(ZoneId.of("UTC"));
    }
}
