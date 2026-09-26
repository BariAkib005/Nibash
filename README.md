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
| Realtime | Spring WebSocket (`TextWebSocketHandler`) at `/ws/chat/{roomId}/`, token-checked at the handshake |
| Infra | Docker Compose · Nginx · GitHub Actions CI (build + test) — cloud deployment (Azure VM, HTTPS, Blob storage, CD) is **not done yet** |

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
├── backend/              Spring Boot API (Maven wrapper included — no local Maven needed)
│   ├── Dockerfile        multi-stage: JDK 26 build → JRE 26 runtime, non-root
│   └── src/main/resources/db/migration/   V1 = the 57-table baseline · V2 = invitations + public rentals
├── frontend/             React SPA
│   ├── Dockerfile        Node build → Nginx
│   └── nginx.conf        serves the build, proxies /api, /media and /ws (with the upgrade headers)
├── docker-compose.yml    MySQL + API + Nginx, one command
├── scripts/              db-backup.sh / db-restore.sh
├── .github/workflows/    CI: backend tests on MySQL, frontend lint + build, both images build
├── db/setup.sql          One-time local database + app-user creation
└── docs/DEMO.md          The five demo journeys, click by click
```

---

## First-time setup

> **Prerequisites:** JDK 26, Node.js 22.12+ and MySQL 8 must be installed first.
> See **[REQUIREMENTS.md](REQUIREMENTS.md)** for versions, install links and first-run troubleshooting.

### 1. Create the database *(once, needs MySQL admin rights)*

```bash
mysql -u root -p < db/setup.sql                  # bash / zsh
Get-Content db\setup.sql | mysql -u root -p      # PowerShell — it has no "<" redirection
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

Serves on **http://localhost:8000**. On first boot Flyway creates all 58 tables (the V1 baseline plus V2).

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

### Or: run the whole stack in Docker

Needs only Docker (Desktop) — no JDK, Node or MySQL on the machine.

```bash
cp .env.example .env            # set MYSQL_ROOT_PASSWORD and MYSQL_PASSWORD; NIBASH_SEED=true for demo data
docker compose up -d --build    # first build downloads Maven and npm dependencies — a few minutes
```

Open **http://localhost:8080**. Three containers: `mysql` (data on a named volume), `backend` (uploads
on a named volume, `/actuator/health` probe) and `web` (Nginx serving the build and proxying `/api`,
`/media` and the `/ws` chat socket with its `Upgrade` headers). `docker compose down` stops them and
keeps the data; `docker compose down -v` also deletes it.

---

## What works today

**Foundation**

- **Landing page** — hero, problem, six module cards, five roles, tenant-isolation trust section, CTA
- **Signup** — creates admin user + building + `enabled_modules` setting + token in one transaction,
  with a live password-strength meter mirroring the server policy and a module picker
- **Login / logout / session** — opaque `Authorization: Token <40-hex>` auth, session survives refresh
- **App shell** — role-filtered sidebar, building switcher, notification bell, SOS, mobile drawer
- **Tenant isolation** — a caller only ever sees buildings they belong to; a foreign id is a `404`, never a `403`
- **Full database schema** — the 57-table baseline with every FK rule, unique constraint and index,
  plus V2's `invitations` table

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

**Chat** — rooms per building, messages over REST with live delivery on a WebSocket (typing
indicators, auto-reconnect), a notification for every other member, private rooms readable only by
members and the committee

**Facilities** — parking grid you click to assign or release a vehicle, with a layout generator ·
lift status board anyone can report on · equipment with warranty warnings (expired / within 60 days)
· service schedule · a waste calendar whose recurring collections roll forward

**Documents** — upload, version chains (a new version retires the old one), downloads that write an
audit row, the audit trail per document, ACL grants stored per user and role

**Vendors & rentals** — vendor catalogue with residents' reviews and a Haversine *nearby* search ·
flat listings with request → approve/decline → contract · a rent guide from the ML estimator's
cached figures

**Utilities** — meters per unit, readings, opening a billing month; pending bills join the next
monthly invoice once and are settled with it

**My shift** — staff and guards check themselves in and out from a phone (idempotent check-in)

**Safety & platform** — emergency numbers, the intercom log (with a hardware webhook), access cards ·
a multi-building **Portfolio** · an **Activity** log of billing runs, payments and document changes ·
`GET /api/dashboard/summary/`, the single call returning all 8 metrics and 32 sections

**Bringing people in** — a new owner signs up, adds flats, then invites everyone else from *Residents*
(residents and committee members, optionally into a flat) and *Staff* (guards and staff, with their job). Each
invitation is a one-time link, `/join#…`, to share by any channel — it is emailed too when SMTP is set up. The
invitee sets a password and lands in the building with their own login, so guards can scan passes and staff can
use *My shift*. Links expire after seven days and can be reissued or withdrawn from a *Waiting to join* list.

**Public flats** — the committee can list a vacant flat for the building and publish any listing on
**`/flats`**, which anyone can browse without an account. Someone looking for a home opens a flat, creates a
renter account on the spot and sends a request with a note. The committee sees who is asking (name, email,
phone, note) under *Rentals*; approving makes the applicant a **resident of that flat** — the flat is marked
rented and leaves the public page — and the renter's *My requests* page (`/flats/mine`) sends them into
their new building.

**Jobs** — at 00:05 Asia/Dhaka past-due invoices become *overdue*; at 08:00 every invoice due by
tomorrow gets an email reminder. The invoice *Remind* button sends the same email and an in-app
notification.

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
- **Chat** — open Chat as `resident1` in one browser and `committee1` in another (a private window) → type in *General* → the other window shows "Ayesha Rahman is typing…" and then the message, live
- **Parking** — Parking → click a free bay → pick a vehicle → *Assign bay* → the bay turns occupied; click it again → *Release*
- **Documents** — Documents → *History* on the bylaws → download it → the audit trail gains a *download* row
- **Vendors** — Vendors → *Find someone nearby* → Plumbing within 5 km lists one plumber; widen to 10 km and the Mirpur plumber appears, further away
- **A new building** — sign up a fresh workspace → *Staff* → *Invite staff* → copy the link → open it in a private window → set a password → *My shift* → *Check in*
- **Renting from outside** — open **http://127.0.0.1:5173/flats** signed out → *Sunny top-floor 3BHK — 06B* → create an account → *Send request* → as `committee1`, *Rentals* → the request is marked *From outside* → *Approve* → back as the renter, *My requests* → *Open your building*

The step-by-step demo script for all five role journeys is in **[docs/DEMO.md](docs/DEMO.md)**.

### Demo accounts

Created by the seeder ([`DemoSeeder`](backend/src/main/java/com/nibash/seed/DemoSeeder.java)). **Every account shares the
password `Nibash@2026`.** The seeder upserts on email, so re-running it leaves an existing account's password alone.

| Email | Password | Name | Role | Building | Notes |
| --- | --- | --- | --- | --- | --- |
| `admin1@nibash.bd` | `Nibash@2026` | Imran Chowdhury | admin | Gulshan Lakeview Heights | Back-office — **sees both buildings**. Developer + resident of 02B |
| `admin2@nibash.bd` | `Nibash@2026` | Nusrat Jahan | admin | Banani Garden Square | Back-office — **sees both buildings**. Developer + resident of 01B |
| `committee1@nibash.bd` | `Nibash@2026` | Farhana Haque | committee | Gulshan Lakeview Heights | Primary contact · resident of 02A |
| `committee2@nibash.bd` | `Nibash@2026` | Tanvir Alam | committee | Banani Garden Square | Primary contact · resident of 01A |
| `resident1@nibash.bd` | `Nibash@2026` | Ayesha Rahman | resident | Gulshan Lakeview Heights | Owner of 01A |
| `resident2@nibash.bd` | `Nibash@2026` | Rafiq Hasan | resident | Gulshan Lakeview Heights | Tenant of 01B · the directory opt-out case |
| `guard1@nibash.bd` | `Nibash@2026` | Jamal Uddin | guard | Gulshan Lakeview Heights | Security · Gate Officer — use this one for Gate scan |
| `guard2@nibash.bd` | `Nibash@2026` | Sohel Mia | guard | Banani Garden Square | Security · Gate Officer |
| `staff1@nibash.bd` | `Nibash@2026` | Ruma Begum | staff | Gulshan Lakeview Heights | Cleaning · Housekeeping Supervisor |
| `staff2@nibash.bd` | `Nibash@2026` | Kamal Sheikh | staff | Gulshan Lakeview Heights | Maintenance · Plumbing & Electrical |

Two things that will otherwise confuse you:

- **`admin1` and `admin2` are back-office accounts** (`is_staff` + `is_superuser`), so tenant scoping does not apply to
  them — they read *every* building, and a foreign building id returns `200`, not `404`. To watch isolation actually
  work, sign in as `resident1`: `/api/buildings/2/` is then a `404`.
- **The role filter changes the sidebar.** A resident gets no Units, Staff, Expenses, Gate scan or Visitor log, so the
  visitor journey needs two accounts — `resident1` creates the pass, `guard1` scans it.

> Local demo fixtures only. The seeder is a dev/back-office tool — never point it at anything real, and never reuse
> `Nibash@2026` outside a throwaway database.

---

## Tests

```bash
cd backend
./mvnw test
```

70 integration tests against a live MySQL schema, covering the rules that must never regress. CI runs
the same suite on every push (`.github/workflows/ci.yml`).

| Suite | What it pins down |
| --- | --- |
| `FacilitiesModulesTest` | nearby search honours the radius, nearest first · reviews are filed as the caller · only back-office manages global vendors · a download writes **exactly one** audit row · version chains cannot fork · non-allowlisted uploads rejected, media served with `nosniff` + sandbox CSP · chat notifies every *other* member, private rooms stay private · parking layout clamps and is idempotent, a bay never holds two vehicles · a utility bill is invoiced **once** and settled with its invoice · a gate pass only opens on its day, a checked-in visitor can't be cancelled · lift status keeps the latest per lift · recurring waste rolls forward · rentals: only the lister decides, contracts need approval · public ML estimate (200 hit / 202 miss) and intercom webhook · overview intersects buildings · client mistakes are 4xx, not 500 · expenses can be recorded from the multipart form |
| `DashboardAndJobsTest` | every seeded role hydrates the full dashboard (8 metrics, 32 sections) · privacy gating inside the summary · foreign building falls back silently · **the summary's SQL statement count stays flat as rows are added** (no N+1) · the overdue sweep and reminders pick the right invoices and survive a rejected address · *Remind* leaves an in-app notification |
| `ChatSocketTest` | on a real server: **two WebSocket clients in one room both receive a persisted message** · the server, not the client, sets the sender · bad tokens and non-members of a private room are refused at the handshake |
| `MembershipAndPublicRentalsTest` | an owner invites a guard who joins with their own login and clocks in · a link works **once**; renewing retires the old one; an expired or withdrawn link is refused · invitations are for managers of their own building, one open invite per email, and an account can't be pulled into a second role · an owner publishes a flat, an outsider signs up and applies, and approval makes them a resident of that flat (flat marked rented, gone from `/flats`) · only managers publish or let someone in; a neighbour who isn't involved can't see the request |
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
| `POST /api/auth/signup/renter/` | **public** | An account with no building, for someone applying for a flat |
| `/api/invitations/` + `/{id}/renew/` | Committee/Admin | Invite as resident, committee, guard or staff; the response carries the one-time `invite_path` |
| `POST /api/invitations/preview\|accept/` | **public** | The invitee's side: `{token}` → who/where; `{token, password}` → a signed-in session |
| `GET /api/public/listings/` + `/{id}/` | **public** | Published flats nobody has been approved for; `?search= max_rent=`; no people in it |
| `/api/rental-applications/` | Token | The caller's own requests across buildings; apply to a published flat, withdraw a pending one |
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
| `/api/dashboard/summary/` | Token | 8 metrics + 32 sections in one call; foreign `building_id` falls back to your own |
| `/api/analytics/overview` | Token | Multi-building KPIs (+ `per_building`); `?building_ids[]=` intersected with yours |
| `/api/services/` `/vendors/` + `/nearby/` `/reviews/` | mixed | Nearby = Haversine, radius, nearest first; reviews keep the vendor's average |
| `/api/chat/rooms/` `/members/` `/messages/` + `/summary/` | Token | `?room_id= search= latest=`; a message notifies every other member |
| `ws://…/ws/chat/{roomId}/?token=` | Token (handshake) | Pushes `message.created` for every saved message; `typing` frames |
| `/api/documents/` + `/{id}/download/` `/audit/` `/versions/` | Committee/Admin | Multipart upload; download writes an audit row |
| `/api/document-acl-users/` `/document-acl-roles/` `/document-audit/` | mixed | ACL grants (stored, not enforced — see below); read-only audit |
| `/api/parking/slots/` `/layout/` · `/api/vehicles/` | mixed | Layout generator clamps 1–12; assignment keeps slot status in step |
| `/api/assets/` `/asset-maintenance/` · `/api/lifts/status/` `/current/` | mixed | `warranty_state` on assets; latest status per lift |
| `/api/utility-meters/` `/utility-bills/` + `/generate/` | Committee/Admin | `generate/` is idempotent per month |
| `/api/waste-schedules/` + `/next/` | Committee/Admin | `next_occurrence` rolls recurring schedules forward |
| `/api/listings/` `/rental-requests/` `/contracts/` | Token | pending → approved/rejected; contracts on approved requests only; `is_public` is manager-only; residents see only requests they made or received |
| `/api/ml/models/` `/training-runs/` `/city-cache/` · `POST /api/ml/price-estimate` | Committee/Admin · **public** | Estimate: 200 on a cache hit, 202 with `estimate: null` on a miss |
| `/api/intercom/devices/` `/logs/` · `POST /api/intercom/webhook` | mixed · **public** | Webhook can require `X-Intercom-Secret` |
| `/api/activity-logs/` | Committee/Admin (strict) | Read-only; scoped to people attached to your buildings |

---

## Configuration

Every setting comes from the environment — `backend/.env` when running with `./mvnw`, the root
`.env` for Docker Compose. Nothing secret is in `application.yml`.

| Variable | Default | What it does |
| --- | --- | --- |
| `MYSQL_HOST` `MYSQL_PORT` `MYSQL_DATABASE` `MYSQL_USER` `MYSQL_PASSWORD` | `localhost` `3306` `nibash` `nibash` — | Database connection |
| `SERVER_PORT` | `8000` | API port |
| `NIBASH_APP_URL` | `http://127.0.0.1:5173` | Where people open the app — the base of the links in invitation and rental emails |
| `NIBASH_MEDIA_DIR` | `./media` | Where uploads are stored (a volume in Docker) |
| `NIBASH_CORS_ORIGINS` | the Vite dev server | Only matters when the browser calls the API cross-origin |
| `NIBASH_SEED` | `false` | `true` runs the idempotent demo seeder at startup (same as `--seed`) |
| `NIBASH_JOBS_ENABLED` | `true` | The 00:05 overdue sweep and 08:00 reminders |
| `NIBASH_INTERCOM_WEBHOOK_SECRET` | empty | When set, `POST /api/intercom/webhook` needs it in `X-Intercom-Secret` |
| `NIBASH_MAIL_FROM` `SMTP_HOST` `SMTP_PORT` `SMTP_USER` `SMTP_PASSWORD` `SMTP_AUTH` `SMTP_TLS` | no-ops without a server | Reminder email; a missing server is logged, never fatal |

## Background jobs

Both run on **Asia/Dhaka** time and can be switched off with `NIBASH_JOBS_ENABLED=false`.

| When | Job | Rule |
| --- | --- | --- |
| 00:05 daily | Overdue sweep | `pending` invoices whose due date has passed become `overdue` |
| 08:00 daily | Invoice reminders | every `pending` or `overdue` invoice due by tomorrow gets `Reminder: Invoice {number} due {date}`; a rejected address is logged and the batch carries on |

## Backups and restore

```bash
scripts/db-backup.sh                 # local MySQL (credentials from backend/.env) → backups/nibash-<time>.sql.gz
scripts/db-backup.sh --docker        # the compose `mysql` container
scripts/db-restore.sh backups/<file>.sql.gz --yes            # DESTRUCTIVE — replaces the tables in the target DB
scripts/db-restore.sh backups/<file>.sql.gz --docker --yes
```

- Dumps are consistent snapshots (`--single-transaction`) taken without locking the app out, gzip'd,
  and checked for mysqldump's completion marker; the newest 14 are kept (`KEEP=`).
- On Windows run them from Git Bash, with `MYSQL_BIN_DIR="/c/Program Files/MySQL/MySQL Server 8.0/bin"`
  if the MySQL client isn't on `PATH`.
- Passwords go through `MYSQL_PWD`, never the command line. `backups/` is gitignored — backups hold real data.
- **Tested:** the dev database was backed up and restored into a fresh container database; all 58
  tables matched row for row. Restart the API after a restore.
- For a daily backup, schedule `scripts/db-backup.sh --docker` with cron or Task Scheduler.

## Security notes

- **Tenancy** everywhere: a row outside your buildings is a `404`, including in custom actions.
- **Uploads** are limited by type (images for ticket photos; images and PDFs for receipts; office
  documents, PDFs, text and images for documents) and size, and served with
  `X-Content-Type-Options: nosniff` and a `sandbox` Content-Security-Policy, so an uploaded file can
  be displayed but never run script on this origin.
- **Invitation links** carry 256 random bits in the URL *fragment* (`/join#…`), which browsers never send
  to a server or put in a `Referer`; only a SHA-256 of the token is stored. A link works once (accepting
  takes a row lock) and expires after seven days. An email that already has an account must give that
  account's password to accept.
- **One role per account.** An account's role applies in every building it belongs to, so an invitation
  or a rental approval never gives an existing account a different role than it has — a resident of one
  building can't become committee of another by accepting an invite. Brand-new accounts with no building
  can take any role.
- **The public flats page** shows only what a manager published — the flat and its building, never who
  listed it or who asked.
- **The chat socket** validates the session token during the handshake and applies the same room
  rules as the REST API; the server sets each message's sender.
- **Errors** never leak a stack trace; client mistakes are `4xx` with the spec's wording.
- Passwords are BCrypt; password hashes, dates of birth and national IDs are never serialized.

## Deviations from the spec

Deliberate, and each covered by a test:

| Area | Spec | Here | Why |
| --- | --- | --- | --- |
| Gate passes | expire only once their date is past | valid **only on** their date | a pass for next week opened the gate today, contradicting the pass's own wording |
| Utility bills | `generate-monthly` adds pending bills | bills go `pending → billed → paid` | otherwise one bill was charged again every month |
| Private chat rooms | not enforced | readable only by members and managers | "private" was otherwise a label |
| Chat socket | unauthenticated echo | token-checked handshake; persisted messages pushed as `message.created` | spec §15.9 sanctions it; nobody can listen to a room they can't read |
| Room rename/delete | any member | managers only | a resident could delete *General* |
| Global vendors | any committee | back-office only | one building could rewrite every building's catalogue |
| Reviews | plain CRUD | filed as the caller; vendor rating = average of reviews | same anti-spoofing as poll votes |
| Waste `next/` | earliest `schedule_time ≥ now` | recurring schedules roll forward (`next_occurrence`) | a weekly schedule vanished after its first date |
| Overdue invoices | nothing flips them | daily sweep (spec §15.10 sanctions it) | overdue invoices still get reminders |
| Invoice *Remind* | stub | sends the reminder email + an in-app notification | same response contract |
| Utility `generate/` | creates duplicates on re-run | idempotent per month | matches `generate-monthly` |
| Intercom webhook | AllowAny | optional shared secret | off unless configured, so behaviour is unchanged by default |
| Activity logs | no public route | read-only route for managers, scoped to their people | the table has no building column |
| Document ACLs | stored, not enforced | unchanged — **still not enforced on reads** | kept for parity (spec §15.6); enforcing them is the natural next step |
| Health probe | — | `/actuator/health` (only `health` exposed) | container health checks |
| Joining a building | only the owner signs up; no way in for anyone else | invitations for residents, committee, guards and staff | a new building could never get a guard, a resident or a committee |
| Renting | neighbours only; requests are resident rows | published listings, renter accounts, `applicant` on requests; approving an outsider (managers only) makes them a resident | nobody from outside a building could ever rent in it |
| Building listings | every listing belongs to a resident | a manager with no flat of their own lists for the building | the owner had no way to let a vacant flat |
| Rental request list | every request in the building | managers see all; residents see only their own and those for their flats | outside applicants' names shouldn't reach every neighbour |
| Session's home building | falls back to the first building in the system | only for back-office accounts; others get none | a renter with no building saw another building's name |

## Not done yet

- **Cloud deployment** — Azure VM, HTTPS with Let's Encrypt, secrets on the server, Azure Blob Storage
  behind `StorageService`, and a deploy step in CI. Everything up to that point is in place: the images,
  `docker-compose.yml`, the Nginx config (including the WebSocket upgrade) and the backup scripts.
- Document ACLs are stored but not enforced on reads (see above).
- The *Overview* page is the redesigned one and shows its own set of cards; `GET /api/dashboard/summary/`
  already returns every section should more widgets be added to it.
- i18n: English UI with Bangla demo data, per the plan's cut list.
