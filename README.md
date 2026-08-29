# Nibash

A cloud-native, multi-tenant **building management platform**. One deployment serves many buildings;
every user sees only the buildings they belong to.

Built for the Bangladeshi market — currency **BDT**, timezone **Asia/Dhaka**, bilingual Bangla/English content.

---

## Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 26 · Spring Boot 4.1 · Spring Web MVC / Data JPA / Security / Validation / WebSocket / Mail |
| Database | MySQL 8 · **Flyway** migrations (`backend/src/main/resources/db/migration`) |
| Frontend | React 19 · TypeScript · Vite 8 · Tailwind CSS 4 · React Router 7 · TanStack Query 5 |
| Infra | Docker Compose · Nginx · Azure VM · GitHub Actions |

> **Note on versions.** The spec was written against "Java 21 / Spring Boot 3.3".
> Spring Initializr no longer offers Boot 3.x, so the project runs **Boot 4.1 on Java 26** (the JDK
> installed on this machine).

### Spring Boot 4 differences worth knowing

These have already bitten once and will bite again, so they're recorded here:

| Thing | Boot 3 | Boot 4 (this project) |
| --- | --- | --- |
| JSON mapper | `com.fasterxml.jackson.databind.ObjectMapper` | **`tools.jackson.databind.ObjectMapper`** (Jackson 3). Annotations stay on `com.fasterxml.jackson.annotation`. |
| Web starter | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| MockMvc test annotation | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| Date serialization | `spring.jackson.serialization.write-dates-as-timestamps: false` | Feature removed — Jackson 3 writes ISO-8601 by default. Setting it fails startup. |
| Servlet filters | `@Component` + `@Transactional` tolerated | **Neither** on a `OncePerRequestFilter`: `@Transactional` triggers a CGLIB proxy whose `GenericFilterBean.logger` is null (Tomcat fails to start), and `@Component` double-registers it. Construct it in `SecurityConfig` instead. |

---

## Layout

```
Nibash/
├── backend/          Spring Boot API (Maven wrapper included — no local Maven needed)
│   └── src/main/resources/db/migration/V1__baseline.sql   ← the full 57-table schema
├── frontend/         React SPA (landing page, auth, app shell)
└── db/setup.sql      One-time local database + app-user creation
```

---

## First-time setup

### 1. Create the database *(once, needs MySQL admin rights)*

```bash
mysql -u root -p < db/setup.sql
```

Or open `db/setup.sql` in MySQL Workbench and execute it. It creates:

- database `nibash` and `nibash_test` (utf8mb4)
- user `nibash` / `nibash_dev_password` with rights on both

**Do not create tables by hand** — Flyway builds them on first boot.

### 2. Configure the backend

```bash
cp backend/.env.example backend/.env     # then edit if your credentials differ
```

Every setting is read from the environment (`application.yml` has no hard-coded secrets).

### 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run            # Windows: .\mvnw.cmd spring-boot:run
```

Serves on **http://localhost:8000**. On first boot Flyway creates all 57 tables.

> If `java` isn't on your PATH, set `JAVA_HOME` first — e.g.
> `$env:JAVA_HOME = "C:\Users\User\.jdks\openjdk-26.0.2"`

### 4. Run the frontend

```bash
cd frontend
npm install      # first time only
npm run dev
```

Serves on **http://127.0.0.1:5173** and proxies `/api`, `/media` and `/ws` to the backend, so there
is no CORS in development and no absolute API URL in the app code.

---

## What works today

**Foundation**

- **Landing page** — hero, problem, six module cards, five roles, tenant-isolation trust section, CTA
- **Signup** — creates admin user + building + `enabled_modules` setting + token in one transaction,
  with a live password-strength meter mirroring the server policy and a module picker
- **Login / logout / session** — opaque `Authorization: Token <40-hex>` auth, session survives refresh
- **App shell** — role-filtered sidebar, building switcher, notification bell, SOS, mobile drawer
- **Tenant isolation** — a caller only ever sees buildings they belong to; a foreign id is a `404`, never a `403`
- **Full database schema** — all 57 tables with every FK rule, unique constraint and index

**Registry** — units, residents, staff, directory (with opt-in privacy gating)

**Finance** — invoices with nested line items and five filters · the monthly service-charge batch
(idempotent, so pressing it twice bills nobody twice) · one-click checkout behind a row lock ·
expenses with receipt upload and a stacked month-by-category chart

**Maintenance** — tickets that auto-assign to a staff member whose role matches the category ·
a drag-and-drop board across open → in progress → resolved → closed · photo upload with a lightbox ·
staff attendance with idempotent check-in

**Security** — QR gate passes · a camera-based scan screen built for a guard's phone ·
visitor check-in/out · gate log with an hourly traffic chart · hold-to-confirm SOS that also writes
a notification

**Community & bookings** — notice board (pinned first, live-only by default) · polls with one vote
per resident and live result bars · events with RSVP · a drag-to-select booking calendar that
checks for clashes *before* you submit

### Verify the journeys

```bash
# Seed the demo fixture first — it is idempotent, so run it as often as you like.
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed
```

Sign in as `admin1@nibash.bd` / `Nibash@2026` (every demo account shares that password), then:

- **Finance** — Invoices → *Generate month* → run it, run it again, see `0 created` → pay an invoice → it flips to *paid*
- **Maintenance** — Maintenance → drag a card between columns → open one → attach a photo
- **Security** — Expected visitors → *Expect a visitor* → show the pass → sign in as `guard1@nibash.bd` → Gate scan → type the code → checked in
- **Community** — Polls → vote once, then try again → *Already voted* · Bookings → drag two hours → book → drag an overlapping slot → refused before submit

---

## Tests

```bash
cd backend
./mvnw test
```

38 integration tests against a live MySQL schema, covering the rules that must never regress:

| Suite | What it pins down |
| --- | --- |
| `AuthFlowTest` | signup → login → me → logout, plus duplicate email, weak password, bad login, missing token |
| `TenancyTest` | cross-building reads blocked, role gating, directory privacy, seeder idempotency |
| `FinanceAndMaintenanceTest` | monthly batch creates nothing on a re-run · paid invoice rejected · **two concurrent checkouts write exactly one payment** · expense date/amount rules · idempotent check-in · ticket auto-assignment |
| `SecurityCommunityBookingTest` | expired pass rejected but a same-day late scan passes · re-scan does not move the arrival time · a token from another building is `404` · double vote rejected · voting as someone else silently uses your own row · **two concurrent bookings leave exactly one winner** · SOS writes a notification |

Tests run against the separate `nibash_test` schema, so your dev data is never touched.

---

## API

Trailing slashes are part of the contract. JSON is snake_case throughout. Lists return the DRF
envelope `{count, next, previous, results}` at 20 per page.

| Path | Auth | Notes |
| --- | --- | --- |
| `/api/auth/signup\|login\|logout\|me/` | mixed | Workspace creation and session |
| `/api/buildings/` `/units/` `/residents/` `/staff/` `/users/` `/directory/` | Token | Core registry |
| `/api/invoices/` | Committee/Admin | `?resident_id= status= due_before= due_after=`; nested `items` |
| `/api/invoices/generate-monthly/` | Committee/Admin | Idempotent batch → `{"created_invoices": [...]}` |
| `/api/payments/checkout/` | Token | Locks the invoice, writes one payment, flips status |
| `/api/expenses/` + `/reports/monthly/` | Committee/Admin | Multipart receipt upload; month × category rollup |
| `/api/tickets/` + `/{id}/status/` `/{id}/images/` | Token | Auto-assignment, `closed_at` stamping, 5 MB photo cap |
| `/api/attendance/checkin\|checkout/` | Token | Check-in is idempotent — returns the open shift |
| `/api/appointments/` + `/{id}/qr/` | Token | 32-hex pass token |
| `/api/visitors/scan/` + `/{id}/checkin\|checkout/` | Token | Tenant-scoped token lookup, past-date expiry |
| `/api/gate-events/` + `/analytics/` | Token | Hour × event-type histogram, in **Asia/Dhaka** hours |
| `/api/emergencies/` | Token | Also writes a `type='sos'` notification, same transaction |
| `/api/notifications/` + `/unread-count/` | Token | Feed and bell badge |
| `/api/notices/` | Committee/Admin | Live-only by default; `?include_archived= search=` |
| `/api/polls/` + `/{id}/vote/` `/{id}/results/` | mixed | Voting is open to any resident; anti-spoofing on `resident_id` |
| `/api/events/` + `/{id}/rsvp/` `/{id}/attendees/` | mixed | RSVP upserts rather than duplicating |
| `/api/resources/` + `/{id}/availability/` | Committee/Admin | Bookings overlapping a window |
| `/api/bookings/` + `/quote/` | Token | Overlap rejection under a lock; `quote/` previews it |
| `/api/emergency-contacts/` `/access-cards/` | Committee/Admin | Plain CRUD |
| `/api/settings/` `/api/seed/` | Token / back-office | Profile, password, building settings; demo seeder |

---


