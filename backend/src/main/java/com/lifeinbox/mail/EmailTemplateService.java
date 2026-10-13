package com.lifeinbox.mail;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.EmailMessage;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class EmailTemplateService {

    private final LifeInboxProperties properties;

    public EmailTemplateService(LifeInboxProperties properties) {
        this.properties = properties;
    }

    private static final DateTimeFormatter REMINDER_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);

    private static final DateTimeFormatter DIGEST_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private static final DateTimeFormatter DIGEST_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /**
     * Builds the Scheduled Reminder Notification Email.
     */
    public EmailMessage buildReminderEmail(Reminder reminder, User user, ZoneId userZone) {
        String dashboardUrl = properties.getAppBaseUrl() + "/dashboard";
        ZonedDateTime dueZoned = reminder.getDueAt().atZone(userZone);
        String formattedDue = dueZoned.format(REMINDER_FORMATTER) + " (" + userZone.getId() + ")";

        String escapedTitle = escapeHtml(reminder.getTitle());
        String escapedNotes = reminder.getNotes() != null ? escapeHtml(reminder.getNotes()).replace("\n", "<br/>") : null;
        String escapedCategory = reminder.getCategory() != null ? escapeHtml(reminder.getCategory()) : null;
        String priority = reminder.getPriority() != null ? reminder.getPriority().name() : "MEDIUM";

        String subject = "LifeInbox Reminder: " + reminder.getTitle();

        String priorityBadgeColor = switch (priority) {
            case "HIGH" -> "background-color: #ef4444; color: #ffffff;";
            case "LOW" -> "background-color: #3b82f6; color: #ffffff;";
            default -> "background-color: #f59e0b; color: #ffffff;";
        };

        String htmlBody = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #090d16; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #f1f5f9;">
                  <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #090d16; padding: 32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #0f172a; border-radius: 16px; border: 1px solid #1e293b; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5);">
                          <!-- Header -->
                          <tr>
                            <td style="padding: 28px 32px; background: linear-gradient(135deg, #4f46e5 0%%, #7c3aed 100%%); text-align: left;">
                              <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td>
                                    <div style="font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.5px; color: #c7d2fe; margin-bottom: 4px;">LifeInbox Notification</div>
                                    <div style="font-size: 24px; font-weight: 800; color: #ffffff;">🔔 Reminder Notice</div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <!-- Body Content -->
                          <tr>
                            <td style="padding: 32px;">
                              <div style="margin-bottom: 24px;">
                                <div style="display: inline-block; padding: 4px 10px; border-radius: 9999px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; %s margin-bottom: 12px;">
                                  %s Priority
                                </div>
                                <h1 style="margin: 0 0 12px 0; font-size: 22px; font-weight: 700; color: #ffffff; line-height: 1.3;">
                                  %s
                                </h1>
                                %s
                              </div>

                              <!-- Details Box -->
                              <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #1e293b; border-radius: 12px; border: 1px solid #334155; margin-bottom: 28px;">
                                <tr>
                                  <td style="padding: 16px 20px;">
                                    <div style="font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #94a3b8; margin-bottom: 4px;">Scheduled Due Time</div>
                                    <div style="font-size: 15px; font-weight: 600; color: #e2e8f0;">📅 %s</div>
                                  </td>
                                </tr>
                                %s
                              </table>

                              <!-- CTA Button -->
                              <table role="presentation" border="0" cellspacing="0" cellpadding="0" style="margin: 0 auto 12px auto;">
                                <tr>
                                  <td align="center" style="border-radius: 10px; background: linear-gradient(135deg, #4f46e5 0%%, #6366f1 100%%);">
                                    <a href="%s" target="_blank" style="display: inline-block; padding: 14px 32px; font-size: 15px; font-weight: 700; color: #ffffff; text-decoration: none; border-radius: 10px;">
                                      Open LifeInbox →
                                    </a>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <!-- Footer -->
                          <tr>
                            <td style="padding: 20px 32px; background-color: #090d16; border-top: 1px solid #1e293b; text-align: center;">
                              <p style="margin: 0; font-size: 12px; color: #64748b;">
                                Sent by LifeInbox for %s. You can manage reminder settings in your account.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(subject),
                priorityBadgeColor,
                priority,
                escapedTitle,
                escapedCategory != null ? "<div style=\"font-size: 13px; color: #94a3b8; margin-bottom: 8px;\">📁 " + escapedCategory + "</div>" : "",
                formattedDue,
                escapedNotes != null ? "<tr><td style=\"padding: 16px 20px; border-top: 1px solid #334155;\"><div style=\"font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #94a3b8; margin-bottom: 4px;\">Notes</div><div style=\"font-size: 14px; color: #cbd5e1; line-height: 1.5;\">" + escapedNotes + "</div></td></tr>" : "",
                dashboardUrl,
                escapeHtml(user.getEmail())
        );

        String textBody = """
                🔔 LifeInbox Reminder
                --------------------------------------------------
                Task: %s
                Due: %s
                Priority: %s%s%s

                Open LifeInbox to act on this reminder:
                %s

                --------------------------------------------------
                LifeInbox - Stay on top of your life admin
                """.formatted(
                reminder.getTitle(),
                formattedDue,
                priority,
                reminder.getCategory() != null ? "\nCategory: " + reminder.getCategory() : "",
                reminder.getNotes() != null ? "\n\nNotes:\n" + reminder.getNotes() : "",
                dashboardUrl
        );

        return new EmailMessage(user.getEmail(), user.getName(), subject, htmlBody, textBody);
    }

    /**
     * Builds the Daily Morning Digest Email.
     */
    public EmailMessage buildDailyDigestEmail(
            User user,
            ZoneId userZone,
            List<Reminder> todayReminders,
            List<Reminder> upcomingReminders,
            List<Reminder> overdueReminders
    ) {
        String dashboardUrl = properties.getAppBaseUrl() + "/dashboard";
        String userName = user.getName() != null && !user.getName().isBlank() ? user.getName() : "there";
        String escapedName = escapeHtml(userName);
        String subject = "LifeInbox — Your Daily Plan";

        // HTML task list builders
        String todaySectionHtml = buildReminderListHtml(todayReminders, userZone, "due_time", "No tasks scheduled for today. Great time to get ahead!");
        String upcomingSectionHtml = buildReminderListHtml(upcomingReminders, userZone, "due_date", "No upcoming tasks in the next 30 days.");
        String overdueSectionHtml = buildReminderListHtml(overdueReminders, userZone, "due_date", "No overdue tasks. You're completely caught up! 🎉");

        int totalTasks = todayReminders.size() + upcomingReminders.size() + overdueReminders.size();

        String htmlBody = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #090d16; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #f1f5f9;">
                  <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #090d16; padding: 32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #0f172a; border-radius: 16px; border: 1px solid #1e293b; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5);">
                          <!-- Header -->
                          <tr>
                            <td style="padding: 28px 32px; background: linear-gradient(135deg, #1e1b4b 0%%, #312e81 100%%); border-bottom: 1px solid #3730a3; text-align: left;">
                              <div style="font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.5px; color: #a5b4fc; margin-bottom: 4px;">LifeInbox Daily Digest</div>
                              <div style="font-size: 24px; font-weight: 800; color: #ffffff;">🌅 Good Morning, %s!</div>
                              <div style="font-size: 14px; color: #c7d2fe; margin-top: 6px;">Here is your morning overview (%d active items).</div>
                            </td>
                          </tr>

                          <!-- Content -->
                          <tr>
                            <td style="padding: 32px;">
                              <!-- Today's Tasks -->
                              <div style="margin-bottom: 28px;">
                                <div style="display: flex; align-items: center; margin-bottom: 12px; border-bottom: 1px solid #1e293b; padding-bottom: 8px;">
                                  <span style="font-size: 16px; font-weight: 700; color: #60a5fa;">📅 Today's Tasks</span>
                                  <span style="font-size: 12px; font-weight: 600; background-color: #1e3a8a; color: #93c5fd; padding: 2px 8px; border-radius: 9999px; margin-left: 8px;">%d</span>
                                </div>
                                %s
                              </div>

                              <!-- Upcoming Tasks -->
                              <div style="margin-bottom: 28px;">
                                <div style="display: flex; align-items: center; margin-bottom: 12px; border-bottom: 1px solid #1e293b; padding-bottom: 8px;">
                                  <span style="font-size: 16px; font-weight: 700; color: #a78bfa;">⏳ Upcoming</span>
                                  <span style="font-size: 12px; font-weight: 600; background-color: #2e1065; color: #c4b5fd; padding: 2px 8px; border-radius: 9999px; margin-left: 8px;">%d</span>
                                </div>
                                %s
                              </div>

                              <!-- Overdue Tasks -->
                              <div style="margin-bottom: 32px;">
                                <div style="display: flex; align-items: center; margin-bottom: 12px; border-bottom: 1px solid #1e293b; padding-bottom: 8px;">
                                  <span style="font-size: 16px; font-weight: 700; color: #f87171;">⚠️ Overdue</span>
                                  <span style="font-size: 12px; font-weight: 600; background-color: #7f1d1d; color: #fca5a5; padding: 2px 8px; border-radius: 9999px; margin-left: 8px;">%d</span>
                                </div>
                                %s
                              </div>

                              <!-- CTA Button -->
                              <table role="presentation" border="0" cellspacing="0" cellpadding="0" style="margin: 0 auto;">
                                <tr>
                                  <td align="center" style="border-radius: 10px; background: linear-gradient(135deg, #4f46e5 0%%, #6366f1 100%%);">
                                    <a href="%s" target="_blank" style="display: inline-block; padding: 14px 36px; font-size: 15px; font-weight: 700; color: #ffffff; text-decoration: none; border-radius: 10px;">
                                      Open LifeInbox & Review →
                                    </a>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="padding: 20px 32px; background-color: #090d16; border-top: 1px solid #1e293b; text-align: center;">
                              <p style="margin: 0; font-size: 12px; color: #64748b;">
                                Sent to %s according to your daily digest schedule.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(subject),
                escapedName,
                totalTasks,
                todayReminders.size(),
                todaySectionHtml,
                upcomingReminders.size(),
                upcomingSectionHtml,
                overdueReminders.size(),
                overdueSectionHtml,
                dashboardUrl,
                escapeHtml(user.getEmail())
        );

        // Plain text digest
        StringBuilder textBuilder = new StringBuilder();
        textBuilder.append("🌅 Good Morning, ").append(userName).append("!\n\n");
        textBuilder.append("Here is your LifeInbox plan for today:\n\n");

        textBuilder.append("Today's Tasks (").append(todayReminders.size()).append("):\n");
        textBuilder.append("────────────────────────────────────────\n");
        if (todayReminders.isEmpty()) {
            textBuilder.append("• No tasks due today.\n");
        } else {
            for (Reminder r : todayReminders) {
                textBuilder.append("• ").append(r.getTitle())
                        .append(" — ").append(r.getDueAt().atZone(userZone).format(DIGEST_TIME_FORMATTER))
                        .append("\n");
            }
        }
        textBuilder.append("\n");

        textBuilder.append("Upcoming Tasks (").append(upcomingReminders.size()).append("):\n");
        textBuilder.append("────────────────────────────────────────\n");
        if (upcomingReminders.isEmpty()) {
            textBuilder.append("• No upcoming tasks.\n");
        } else {
            for (Reminder r : upcomingReminders) {
                textBuilder.append("• ").append(r.getTitle())
                        .append(" — ").append(r.getDueAt().atZone(userZone).format(DIGEST_DATE_FORMATTER))
                        .append("\n");
            }
        }
        textBuilder.append("\n");

        textBuilder.append("Overdue Tasks (").append(overdueReminders.size()).append("):\n");
        textBuilder.append("────────────────────────────────────────\n");
        if (overdueReminders.isEmpty()) {
            textBuilder.append("• No overdue tasks. All caught up!\n");
        } else {
            for (Reminder r : overdueReminders) {
                textBuilder.append("• ").append(r.getTitle())
                        .append(" — ").append(r.getDueAt().atZone(userZone).format(DIGEST_DATE_FORMATTER))
                        .append("\n");
            }
        }
        textBuilder.append("\n");

        textBuilder.append("Open LifeInbox to review and complete your tasks:\n");
        textBuilder.append(dashboardUrl).append("\n");

        return new EmailMessage(user.getEmail(), user.getName(), subject, htmlBody, textBuilder.toString());
    }

    private String buildReminderListHtml(List<Reminder> reminders, ZoneId zoneId, String timeMode, String emptyMessage) {
        if (reminders.isEmpty()) {
            return "<div style=\"padding: 12px 16px; background-color: #1e293b; border-radius: 8px; font-size: 13px; color: #94a3b8; font-style: italic;\">"
                    + escapeHtml(emptyMessage) + "</div>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color: #1e293b; border-radius: 10px; overflow: hidden; border: 1px solid #334155;\">");

        for (int i = 0; i < reminders.size(); i++) {
            Reminder r = reminders.get(i);
            String borderStyle = (i < reminders.size() - 1) ? "border-bottom: 1px solid #334155;" : "";
            ZonedDateTime zdt = r.getDueAt().atZone(zoneId);
            String formattedTime = "due_time".equals(timeMode)
                    ? zdt.format(DIGEST_TIME_FORMATTER)
                    : zdt.format(DIGEST_DATE_FORMATTER);

            String priorityBadge = switch (r.getPriority() != null ? r.getPriority().name() : "MEDIUM") {
                case "HIGH" -> "<span style=\"font-size: 10px; font-weight: 700; background-color: #7f1d1d; color: #fca5a5; padding: 2px 6px; border-radius: 4px; text-transform: uppercase;\">High</span>";
                case "LOW" -> "<span style=\"font-size: 10px; font-weight: 700; background-color: #1e3a8a; color: #93c5fd; padding: 2px 6px; border-radius: 4px; text-transform: uppercase;\">Low</span>";
                default -> "";
            };

            sb.append("""
                    <tr>
                      <td style="padding: 12px 16px; %s">
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                          <div>
                            <span style="font-size: 14px; font-weight: 600; color: #f1f5f9;">%s</span>
                            %s
                          </div>
                          <span style="font-size: 12px; color: #94a3b8; font-weight: 500; white-space: nowrap; margin-left: 12px;">%s</span>
                        </div>
                      </td>
                    </tr>
                    """.formatted(borderStyle, escapeHtml(r.getTitle()), priorityBadge.isEmpty() ? "" : " " + priorityBadge, formattedTime));
        }

        sb.append("</table>");
        return sb.toString();
    }

    /**
     * Safe HTML escaping to prevent XSS / HTML injection in generated emails.
     */
    public static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.max(16, input.length()));
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
