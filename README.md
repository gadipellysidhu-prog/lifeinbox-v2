# LifeInbox

LifeInbox is a modern, high-reliability life-admin reminder inbox designed to help users capture, organize, prioritize, and track chores, warranties, tax deadlines, medical appointments, and recurring obligations with strict multi-tenant privacy and automated email notifications.

---

## Features

- **User Authentication**: Secure user registration and login with BCrypt password hashing and stateless HMAC-SHA256 JWT tokens.
- **Session Persistence & Restoration**: Persistent client session restoration via `localStorage` and `GET /api/auth/me`.
- **Protected Routing**: Automatic navigation guarding on client routes and API endpoints.
- **Reminder CRUD**: Complete creation, reading, editing, and deletion of user-scoped reminders.
- **Completion Tracking**: Seamless toggle between `PENDING` and `COMPLETED` states with backend timestamp tracking (`completedAt`).
- **Dynamic Filters**: Instant categorization for **Today**, **Upcoming** (next 30 days), **Overdue**, **Completed**, and **All** reminders.
- **Server-Side Search**: Case-insensitive substring search matching across titles and notes.
- **Multi-Tenant Data Isolation**: Strict user-level authorization where all reminder queries and mutations are isolated to the authenticated user.
- **Timezone-Safe Date Pipeline**: Full UTC `Instant` storage with seamless browser local time input and display.
- **Scheduled Reminder Emails**: Automated backend scheduler detects pending due reminders and sends contextual alert emails with a direct LifeInbox CTA button.
- **Daily Morning Digest**: User-customizable morning digest email sent at the user's configured local time, grouping tasks into **Today's Tasks**, **Upcoming**, and **Overdue** with clean empty states.
- **User Notification Preferences**: Fine-grained preferences modal allowing users to toggle email notifications, reminder alerts, daily digests, configure digest delivery time, and set or auto-detect their IANA timezone.
- **Database-Backed Idempotency & Safe Retries**: Persistent deduplication keys (`REMINDER:{reminderId}:{dueAt}` and `DAILY_DIGEST:{userId}:{localDate}`) and bounded retry handling for email delivery failures.
- **Responsive UI**: Polished Tailwind CSS design with smooth dark mode, loading spinners, accessible forms, settings modal, and empty states.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    React Frontend (Vite)                     │
│  - React 18, TypeScript, Tailwind CSS, React Hook Form, Zod  │
│  - Axios with JWT Interceptors, Preferences Modal, Reminders │
└──────────────────────────────┬───────────────────────────────┘
                               │ HTTP / JSON (REST API)
                               ▼
┌──────────────────────────────────────────────────────────────┐
│               Spring Boot 3.5 Backend (Java 21)              │
│  - Spring Security 6 (Stateless JWT Filter & CORS)           │
│  - Schedulers: ReminderEmailScheduler, DailyDigestScheduler  │
│  - Services: EmailNotificationService, EmailTemplateService  │
│  - Layered Architecture: Controller -> Service -> Repository │
│  - DTOs + Bean Validation + Global RFC 7807 Error Handling   │
└──────────────┬───────────────────────────────┬───────────────┘
               │ Spring Data JPA               │ JavaMailSender / SMTP
               ▼                               ▼
┌──────────────────────────────┐ ┌─────────────────────────────┐
│    PostgreSQL 16 Database    │ │     MailHog / SMTP Server   │
│  - Flyway Migrations (V1-V4) │ │  - Local SMTP: Port 1025    │
│  - Reminders, Users,         │ │  - Web UI: http://localhost: │
│    Preferences & Deliveries  │ │    8025                     │
└──────────────────────────────┘ └─────────────────────────────┘
```

- **Stateless JWT Authentication**: Access tokens are signed using HMAC-SHA256 with the user UUID stored in the token subject. Every protected request verifies the signature and injects the user identity into the `SecurityContext`.
- **Flyway Database Ownership**: All schema alterations are version-controlled via Flyway migrations (`V1` initial schema, `V2` compound indexes, `V3` notification preferences, `V4` email deliveries). Hibernate operates with `ddl-auto: validate`.
- **Email Provider Abstraction**: `EmailProvider` interface decoupling email dispatch logic from physical SMTP transport (`SmtpEmailProvider`). Easily swappable for production providers (AWS SES, SendGrid, Resend, Postmark).
- **Timezone-Aware Local Date Calculation**: User timezones are stored as standard IANA Zone IDs (e.g. `Asia/Kolkata`, `America/New_York`). Daily digests compute local calendar dates dynamically from UTC instants with full Daylight Saving Time (DST) compliance.
- **Persistent Concurrency & Idempotency**: Strict unique constraints on `email_deliveries(deduplication_key)` guarantee zero duplicate email sends even across concurrent or restarting scheduler instances.

---

## Tech Stack

### Frontend
- **Framework**: React 18
- **Language**: TypeScript 5.7
- **Bundler / Dev Server**: Vite 6
- **Styling**: Tailwind CSS 3
- **HTTP Client**: Axios 1.7
- **Form Handling**: React Hook Form 7
- **Schema Validation**: Zod 3.24
- **Date Utilities**: date-fns 4
- **Icons**: Lucide React

### Backend
- **Platform**: Java 21 (OpenJDK)
- **Framework**: Spring Boot 3.5.16
- **Security**: Spring Security 6, JJWT (io.jsonwebtoken 0.12.6)
- **Scheduling**: Spring Scheduling (`@EnableScheduling`)
- **Email Delivery**: Spring Boot Starter Mail (`JavaMailSender`, Jakarta Mail)
- **Persistence**: Spring Data JPA, Hibernate 6.6
- **Database**: PostgreSQL 16
- **Migration**: Flyway Community
- **Validation**: Jakarta Bean Validation / Hibernate Validator
- **API Documentation**: SpringDoc OpenAPI / Swagger UI 3

### Local Development Services
- **MailHog**: Lightweight local SMTP test server with Web UI inspection

### Testing
- **Backend**: JUnit 5, Mockito, Spring Boot Test (`@SpringBootTest`, `@WebMvcTest`, `MockMvc`)
- **Frontend**: Vitest 3, React Testing Library, jsdom

---

## Project Structure

```text
life-inbox/
├── backend/
│   ├── src/main/java/com/lifeinbox/
│   │   ├── config/         # SecurityConfig, CorsConfig, OpenApiConfig, SchedulerConfig, LifeInboxProperties
│   │   ├── controller/     # AuthController, ReminderController, NotificationPreferencesController, HealthController
│   │   ├── dto/            # Auth, Reminder, Preferences, and User Request/Response records
│   │   ├── entity/         # User, Reminder, UserNotificationPreferences, EmailDelivery JPA Entities
│   │   ├── exception/      # ApiError RFC 7807 & GlobalExceptionHandler
│   │   ├── mapper/         # UserMapper, ReminderMapper, NotificationPreferencesMapper
│   │   ├── repository/     # UserRepository, ReminderRepository, UserNotificationPreferencesRepository, EmailDeliveryRepository
│   │   ├── scheduler/      # ReminderEmailScheduler, DailyDigestScheduler, EmailRetryScheduler
│   │   ├── security/       # JwtService, JwtAuthenticationFilter, UserPrincipal
│   │   └── service/        # AuthService, ReminderService, NotificationPreferencesService, EmailNotificationService, EmailTemplateService, SmtpEmailProvider
│   ├── src/main/resources/
│   │   ├── db/migration/   # V1, V2, V3__add_user_notification_preferences.sql, V4__add_email_deliveries.sql
│   │   └── application.yml # Spring Boot configuration
│   ├── src/test/java/      # Comprehensive unit, scheduler, service, security, and integration tests
│   └── pom.xml             # Maven project dependencies
├── frontend/
│   ├── src/
│   │   ├── api/            # apiClient (Axios), authApi, reminderApi, notificationPreferencesApi
│   │   ├── components/     # Header, Footer, Button, Input, Spinner, Reminder Cards/Forms, NotificationPreferencesModal
│   │   ├── context/        # AuthContext & AuthProvider
│   │   ├── features/       # LoginPage, RegisterPage, DashboardPage
│   │   ├── hooks/          # useAuth, useReminders, useNotificationPreferences
│   │   ├── routes/         # AppRoutes, ProtectedRoute
│   │   ├── types/          # TypeScript interfaces (Auth, Reminder, NotificationPreferences)
│   │   └── utils/          # localStorage management, classnames, timezone detection
│   ├── package.json        # Frontend scripts and dependencies
│   ├── tsconfig.json       # TypeScript compiler options
│   └── vite.config.ts      # Vite dev server and proxy setup
├── docker-compose.yml      # PostgreSQL 16 Alpine & MailHog containers
├── .env.example            # Environment configuration template
├── .gitignore              # Monorepo ignore rules
└── README.md               # Project documentation
```

---

## Prerequisites

Ensure you have the following installed locally:
- **Java 21** (e.g. Eclipse Adoptium Temurin 21)
- **Maven 3.9+**
- **Node.js 18+** & **npm 9+**
- **Docker** & **Docker Compose**

---

## Configuration

Copy the example environment file to configure your local setup:

```bash
cp .env.example .env
```

### Key Configuration Variables:

| Variable | Default (Local Dev) | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host (`localhost` for host run, `postgres` inside Docker) |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `lifeinbox` | Database name |
| `DB_USER` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `SERVER_PORT` | `8080` (or `8081`) | Spring Boot server port |
| `JWT_SECRET` | *(256-bit hex)* | HMAC-SHA signing secret key |
| `JWT_EXPIRATION_MS` | `86400000` | JWT expiration duration in milliseconds (24h) |
| `BACKEND_URL` | `http://localhost:8080` | Backend target URL for Vite proxy |
| `MAIL_HOST` | `localhost` | SMTP host (`localhost` for local Spring Boot, `mailhog` inside Docker) |
| `MAIL_PORT` | `1025` | SMTP port (`1025` for MailHog, `587` or `465` for production SMTP) |
| `MAIL_USERNAME` | *(empty for MailHog)* | SMTP username |
| `MAIL_PASSWORD` | *(empty for MailHog)* | SMTP password |
| `MAIL_FROM` | `no-reply@lifeinbox.local` | Sender email address |
| `MAIL_FROM_NAME` | `LifeInbox` | Sender display name |
| `MAIL_SMTP_AUTH` | `false` | Enable SMTP authentication (`true` for production SMTP) |
| `MAIL_SMTP_STARTTLS` | `false` | Enable SMTP STARTTLS (`true` for port 587 production SMTP) |
| `APP_BASE_URL` | `http://localhost:5173` | Application base URL used in email CTA buttons |
| `SCHEDULER_REMINDER_INTERVAL_MS` | `30000` | Polling frequency for due reminder emails (30 seconds) |
| `SCHEDULER_DIGEST_INTERVAL_MS` | `60000` | Polling frequency for daily morning digest (60 seconds) |
| `SCHEDULER_RETRY_INTERVAL_MS` | `120000` | Polling frequency for failed email retries (2 minutes) |
| `EMAIL_MAX_RETRIES` | `3` | Maximum total attempts (initial attempt + up to 2 retries) |
| `EMAIL_RETRY_BACKOFF_SECONDS` | `60` | Fixed backoff delay between retry attempts (seconds) |

---

## Running Local Infrastructure (PostgreSQL & MailHog)

Start the database and MailHog containers using Docker Compose:

```bash
docker compose up -d
```

Verify containers are running:

```bash
docker compose ps
```

- **PostgreSQL**: `localhost:5432`
- **MailHog SMTP Server**: `localhost:1025`
- **MailHog Web UI**: `http://localhost:8025`

---

## Running the Backend

### Windows (PowerShell):
```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

### Linux / macOS:
```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

- **Health Endpoint**: `http://localhost:8080/api/health`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

---

## Running the Frontend

```bash
cd frontend
npm install
npm run dev
```

- **Web Application**: `http://localhost:5173`

---

## Testing

### Backend Test Suite (JUnit 5, Mockito, Spring Boot Test):
```bash
cd backend
mvn clean verify
```
*Executes all 74 unit, service, controller, scheduler, and security tests with zero failures.*

### Frontend Test Suite (Vitest, React Testing Library):
```bash
cd frontend
npm run test
```
*Executes all 30 component, hook, and modal tests with zero failures.*

---

## Production Build

### Backend JAR Package:
```bash
cd backend
mvn clean package
```
*Output: `backend/target/backend-0.0.1-SNAPSHOT.jar`*

### Frontend Static Bundle:
```bash
cd frontend
npm run build
```
*Output: `frontend/dist/`*

---

## API Endpoints

### Authentication
- `POST /api/auth/register` — Register a new user account (accepts optional `timezone`, auto-initializes preferences, returns profile and JWT).
- `POST /api/auth/login` — Authenticate user credentials (returns profile and JWT).
- `GET /api/auth/me` — Retrieve currently authenticated user profile (`Bearer <token>`).

### Notification Preferences
- `GET /api/notification-preferences` — Retrieve authenticated user's notification preferences.
- `PUT /api/notification-preferences` — Update authenticated user's notification preferences (email toggles, digest time `HH:mm`, IANA timezone).

### Reminders
- `GET /api/reminders?filter={today|upcoming|overdue|completed|all}&search={term}` — List filtered user reminders.
- `GET /api/reminders/{id}` — Retrieve a single reminder by UUID.
- `POST /api/reminders` — Create a new reminder.
- `PUT /api/reminders/{id}` — Update an existing reminder.
- `PATCH /api/reminders/{id}/complete` — Mark reminder as `COMPLETED`.
- `PATCH /api/reminders/{id}/incomplete` — Reset reminder to `PENDING`.
- `DELETE /api/reminders/{id}` — Delete a reminder.

### System
- `GET /api/health` — Check backend and database health status.

---

## Email Notification Architecture

### 1. Scheduled Reminder Emails
- **Trigger**: Every 30 seconds (`lifeinbox.scheduler.reminder-interval-ms`), the `ReminderEmailScheduler` queries pending reminders where `dueAt <= now()`.
- **Pre-Conditions**: Skips reminders where `status == COMPLETED` or user has disabled `emailNotificationsEnabled` or `reminderEmailsEnabled`.
- **Deduplication**: Claims an email delivery record with deduplication key: `REMINDER:{reminderId}:{dueAtEpochMilli}`. If the due time is rescheduled, a new delivery record will naturally trigger at the new scheduled time.
- **Content**: Clean HTML template displaying the task title, category, formatted due date/time in the user's configured timezone, notes, and an `[Open LifeInbox]` button redirecting to `${appBaseUrl}/dashboard`.

### 2. Daily Morning Digest
- **Trigger**: Every 60 seconds (`lifeinbox.scheduler.digest-interval-ms`), the `DailyDigestScheduler` retrieves all active users with `dailyDigestEnabled == true` and `emailNotificationsEnabled == true`.
- **Timezone & Timing Resolution**: For each user, the scheduler maps the current UTC instant to their IANA `ZoneId` to obtain their local date (`LocalDate`) and local time (`LocalTime`). If the current local time has reached or passed their configured `dailyDigestTime`, the scheduler checks for an existing delivery record for today (`DAILY_DIGEST:{userId}:{localDate}`). If no record exists yet, the digest is sent. This guarantees that delivery is never missed if the scheduler fires slightly after the configured minute (e.g. at `08:00:30` or `08:01`) or recovers after application restart, while the unique deduplication key guarantees exactly one digest per local calendar date.
- **Idempotency**: Claims a delivery record with deduplication key: `DAILY_DIGEST:{userId}:{localDateIso}`.
- **Content**: Gathers:
  - **Today's Tasks**: Pending reminders due within the user's current local calendar day.
  - **Upcoming Tasks**: Pending reminders due beyond today, bounded to the next 30 days (`localDate.plusDays(30)`), consistent with the Dashboard's Upcoming filter horizon.
  - **Overdue Tasks**: Pending reminders whose due instant has passed.
  - Formatted with clean empty states if any category has no items, and a prominent `[Open LifeInbox]` CTA button.

### 3. Concurrency, Delivery Status Flow & Distributed Semantics
- **States**: `PENDING` -> `PROCESSING` -> `SENT` (accepted by SMTP server) or `FAILED` (with recorded failure reason).
- **Concurrency & Idempotency Guarantee**: The PostgreSQL unique constraint on `email_deliveries(deduplication_key)` guarantees at-most-once database delivery claims and prevents concurrent scheduler workers from claiming the same notification.
- **Distributed Failure Semantics**: Because external SMTP acceptance and PostgreSQL database commits cannot be bound in a single atomic two-phase commit (2PC) transaction, if an external SMTP provider accepts an email but the application crashes before updating the delivery record to `SENT`, a subsequent recovery retry could re-dispatch the email. This follows standard distributed systems at-least-once external delivery semantics.
- **Retry Mechanism**: The `EmailRetryScheduler` runs periodically (default every 2 minutes), selecting deliveries where `status == FAILED`, `attemptCount < maxRetries` (default 3 total attempts), and `lastAttemptAt <= now() - backoff` (fixed backoff of 60 seconds). If an associated reminder was completed or deleted before the retry executes, the retry is safely aborted.
- **Security**: All user-generated content (reminder titles, notes, categories) is strictly escaped via `EmailTemplateService.escapeHtml` to prevent HTML injection. No sensitive auth tokens or passwords are ever embedded into emails or logs.

---

## Manual Verification Flow

To verify the email notification system end-to-end locally:

1. **Start Services**:
   ```bash
   docker compose up -d
   ```
   Confirm both PostgreSQL (`5432`) and MailHog (`1025`, `8025`) are healthy.

2. **Start Backend & Frontend**:
   ```bash
   # Terminal 1:
   cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

   # Terminal 2:
   cd frontend && npm run dev
   ```

3. **Register & Configure Preferences**:
   - Navigate to `http://localhost:5173`.
   - Register a new account (e.g. `alex@example.com`). Browser timezone is automatically captured.
   - Click the **Settings** icon in the header to open the **Notification Preferences** modal.
   - Verify preferences: Email Notifications ON, Reminder Emails ON, Daily Digest ON.
   - Note the configured timezone (e.g. `America/New_York` or `Asia/Kolkata`).

4. **Verify Scheduled Reminder Email**:
   - On the Dashboard, create a new reminder due **1 minute in the future** (e.g. "Submit Tax Documents").
   - Wait 60–90 seconds for the scheduler tick.
   - Open MailHog in your browser: `http://localhost:8025`.
   - Verify an email arrives:
     - **Subject**: `LifeInbox Reminder: Submit Tax Documents`
     - **Content**: Details rendered in your local timezone with an `[Open LifeInbox]` button.
   - Click `[Open LifeInbox]`, which directs to `http://localhost:5173/dashboard`.
   - Mark the reminder complete.
   - Notice that no further reminder emails are triggered.

5. **Verify Daily Morning Digest**:
   - In the frontend Settings modal, adjust your **Daily Digest Time** to 2 minutes from your current local clock (e.g. if local time is `19:40`, set to `19:42`). Click **Save Preferences**.
   - Create 3 reminders:
     - One due today in 30 minutes ("Review PR").
     - One due tomorrow ("Grocery Shopping").
     - One created with an overdue past time ("Call Doctor").
   - Wait for the local clock to reach the scheduled digest minute.
   - Check `http://localhost:8025`.
   - Verify the digest email arrives:
     - **Subject**: `LifeInbox — Your Daily Plan`
     - **Sections**: **Today's Tasks** ("Review PR"), **Upcoming** ("Grocery Shopping"), **Overdue** ("Call Doctor").
   - Refresh MailHog after another minute to verify no duplicate digest is sent for today.

---

## Production Email Provider Setup

For production deployment, LifeInbox supports any standard SMTP provider by configuring environment variables:

- **SendGrid**: `MAIL_HOST=smtp.sendgrid.net`, `MAIL_PORT=587`, `MAIL_USERNAME=apikey`, `MAIL_PASSWORD=<API_KEY>`, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true`
- **AWS SES**: `MAIL_HOST=email-smtp.<region>.amazonaws.com`, `MAIL_PORT=587`, `MAIL_USERNAME=<SES_USER>`, `MAIL_PASSWORD=<SES_PASSWORD>`, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true`
- **Resend**: `MAIL_HOST=smtp.resend.com`, `MAIL_PORT=587`, `MAIL_USERNAME=resend`, `MAIL_PASSWORD=<API_KEY>`, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true`
- **Postmark**: `MAIL_HOST=smtp.postmarkapp.com`, `MAIL_PORT=587`, `MAIL_USERNAME=<SERVER_TOKEN>`, `MAIL_PASSWORD=<SERVER_TOKEN>`, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true`
- **Gmail / Google Workspace**: `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME=<EMAIL>`, `MAIL_PASSWORD=<APP_PASSWORD>`, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true`

---

## Web Push & VAPID Production Configuration

LifeInbox supports standards-compliant browser Web Push notifications (RFC 8291 / RFC 8292) using VAPID (Voluntary Application Server Identification). When reminders become due, Web Push notifications are dispatched alongside emails to active registered browser endpoints.

### 1. Generating Production VAPID Keys

To generate a cryptographically valid VAPID public/private keypair for production, run:

```bash
npx web-push generate-vapid-keys
```

Example output:
```text
=======================================
Public Key:
BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkRxZJjS5Fi28V9WJaSiieUQ...
Private Key:
_b0-Qj9...
=======================================
```

### 2. Environment Variables

Configure the following variables in your production environment (or `.env` file):

| Variable | Required | Description |
|---|---|---|
| `LIFEINBOX_PUSH_ENABLED` | Yes | Set to `true` to enable backend Web Push delivery. |
| `VAPID_PUBLIC_KEY` | Yes (if enabled) | Base64URL-encoded uncompressed P-256 public key (exposed safely to browsers via `/api/push/public-key`). |
| `VAPID_PRIVATE_KEY` | Yes (if enabled) | Base64URL-encoded P-256 private scalar (**strictly server-only; NEVER commit or send to client**). |
| `VAPID_SUBJECT` | Yes (if enabled) | Push subscriber contact URI (`mailto:admin@yourdomain.com` or `https://yourdomain.com`). |
| `LIFEINBOX_PUSH_RETRY_ENABLED` | Optional | Enable automatic retries for transient failures (default: `true`). |
| `LIFEINBOX_PUSH_RETRY_MAX_ATTEMPTS` | Optional | Maximum delivery attempts per endpoint (default: `3`). |
| `LIFEINBOX_PUSH_RETRY_INITIAL_BACKOFF_MS` | Optional | Initial exponential backoff delay (default: `1000` ms). |
| `LIFEINBOX_PUSH_RETRY_MAX_BACKOFF_MS` | Optional | Maximum retry delay cap (default: `10000` ms). |

### 3. HTTPS & Secure Context Requirements

- **Mandatory HTTPS in Production**: Browsers strictly enforce that Service Worker registration and Web Push subscriptions require a Secure Context (`https://`). Non-localhost origins over plain `http://` will fail at the browser permission or subscription stage.
- **Localhost Exception**: Browsers treat `http://localhost:*` and `http://127.0.0.1:*` as secure contexts for development and local testing.
- **TLS Termination**: Production deployments should terminate TLS at a reverse proxy (e.g., Nginx, Caddy, Cloudflare, AWS ALB) before routing requests to the application.
- **Service Worker Location**: The service worker `/sw.js` must be served from the root scope (`/`) with `Content-Type: application/javascript` or `text/javascript`.

### 4. Browser & OS Compatibility

- **Supported Browsers**: Chromium-based browsers (Google Chrome, Microsoft Edge, Brave, Opera), Mozilla Firefox, and Apple Safari (macOS Ventura 13+ and iOS/iPadOS 16.4+ when added to Home Screen).
- **Notification Sound & Focus**: Audible alerts and banners are governed by the user's operating system notification center and focus modes (e.g., Windows Focus Assist, macOS Do Not Disturb).

---

## Current Scope & Limitations

The current release provides high-reliability core reminder management, automated email notifications, and browser Web Push notifications. The following architectural design choices are accepted limitations:

- **In-Process Push Deduplication**: The reminder push deduplication cache operates in-memory (`ConcurrentHashMap` bounded to 10,000 entries with 24-hour TTL). This provides high-performance single-instance deduplication without requiring external broker infrastructure (Redis/Kafka), but does not guarantee deduplication across server restarts or multi-instance clusters.
- **SMS / WhatsApp**: Excluded by design in favor of email and browser Web Push.
- **Third-Party Calendar Integrations**: Not included in current release.
- **Multi-Tenant / Collaborative Reminders**: Single-user isolation per account.
