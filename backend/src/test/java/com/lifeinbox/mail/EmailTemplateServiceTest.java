package com.lifeinbox.mail;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.EmailMessage;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateServiceTest {

    private EmailTemplateService templateService;
    private LifeInboxProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LifeInboxProperties();
        properties.setAppBaseUrl("http://localhost:5173");
        templateService = new EmailTemplateService(properties);
    }

    @Test
    @DisplayName("HTML escaping neutralizes script tags, quotes, and HTML special characters")
    void testHtmlEscaping() {
        String dangerous = "<script>alert('XSS & harm');</script> \"hello\"";
        String escaped = EmailTemplateService.escapeHtml(dangerous);

        assertThat(escaped)
                .doesNotContain("<script>")
                .doesNotContain("</script>")
                .doesNotContain("\"")
                .doesNotContain("'")
                .contains("&lt;script&gt;")
                .contains("&amp;")
                .contains("&quot;")
                .contains("&#39;");
    }

    @Test
    @DisplayName("Build reminder email generates correct subject, timezone-formatted due date, notes, and CTA link")
    void testBuildReminderEmail() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .email("alice@example.com")
                .build();

        // 2026-10-10 13:30:00 UTC = 19:00:00 in Asia/Kolkata (+05:30)
        Instant dueAt = Instant.parse("2026-10-10T13:30:00Z");

        Reminder reminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Submit Tax Documents <important>")
                .notes("Line 1\nLine 2 & attachments")
                .dueAt(dueAt)
                .priority(ReminderPriority.HIGH)
                .category("Finance")
                .status(ReminderStatus.PENDING)
                .build();

        ZoneId userZone = ZoneId.of("Asia/Kolkata");
        EmailMessage message = templateService.buildReminderEmail(reminder, user, userZone);

        assertThat(message.recipientEmail()).isEqualTo("alice@example.com");
        assertThat(message.recipientName()).isEqualTo("Alice");
        assertThat(message.subject()).isEqualTo("LifeInbox Reminder: Submit Tax Documents <important>");

        // HTML checks
        assertThat(message.htmlBody())
                .contains("Submit Tax Documents &lt;important&gt;")
                .contains("Line 1<br/>Line 2 &amp; attachments")
                .contains("7:00 PM (Asia/Kolkata)")
                .contains("HIGH Priority")
                .contains("Finance")
                .contains("http://localhost:5173/dashboard");

        // Plain text checks
        assertThat(message.textBody())
                .contains("Submit Tax Documents <important>")
                .contains("Line 1\nLine 2 & attachments")
                .contains("http://localhost:5173/dashboard");
    }

    @Test
    @DisplayName("Build daily digest email includes Today, Upcoming, and Overdue sections with timezone accuracy")
    void testBuildDailyDigestEmail() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Bob")
                .email("bob@example.com")
                .build();

        ZoneId userZone = ZoneId.of("America/New_York");

        Reminder todayReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .title("Team Standup")
                .dueAt(Instant.parse("2026-10-10T14:00:00Z")) // 10:00 AM EDT
                .priority(ReminderPriority.MEDIUM)
                .status(ReminderStatus.PENDING)
                .build();

        Reminder upcomingReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .title("Quarterly Review")
                .dueAt(Instant.parse("2026-10-15T18:00:00Z"))
                .priority(ReminderPriority.HIGH)
                .status(ReminderStatus.PENDING)
                .build();

        Reminder overdueReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .title("Submit Invoice")
                .dueAt(Instant.parse("2026-10-08T12:00:00Z"))
                .priority(ReminderPriority.LOW)
                .status(ReminderStatus.PENDING)
                .build();

        EmailMessage message = templateService.buildDailyDigestEmail(
                user,
                userZone,
                List.of(todayReminder),
                List.of(upcomingReminder),
                List.of(overdueReminder)
        );

        assertThat(message.subject()).isEqualTo("LifeInbox — Your Daily Plan");
        assertThat(message.recipientEmail()).isEqualTo("bob@example.com");

        assertThat(message.htmlBody())
                .contains("Good Morning, Bob!")
                .contains("Team Standup")
                .contains("Quarterly Review")
                .contains("Submit Invoice")
                .contains("http://localhost:5173/dashboard");

        assertThat(message.textBody())
                .contains("Team Standup")
                .contains("Quarterly Review")
                .contains("Submit Invoice")
                .contains("http://localhost:5173/dashboard");
    }

    @Test
    @DisplayName("Build daily digest email displays clean empty states when no reminders exist")
    void testBuildDailyDigestEmailEmpty() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Charlie")
                .email("charlie@example.com")
                .build();

        ZoneId userZone = ZoneId.of("UTC");

        EmailMessage message = templateService.buildDailyDigestEmail(
                user,
                userZone,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertThat(message.htmlBody())
                .contains("No tasks scheduled for today")
                .contains("No upcoming tasks")
                .contains("No overdue tasks");

        assertThat(message.textBody())
                .contains("No tasks due today")
                .contains("No upcoming tasks")
                .contains("No overdue tasks");
    }
}
