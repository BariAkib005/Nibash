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

- **Landing page** — hero, problem, six module cards, five roles, tenant-isolation trust section, CTA
- **Signup** — creates admin user + building + `enabled_modules` setting + token in one transaction,
  with a live password-strength meter mirroring the server policy and a module picker
- **Login / logout / session** — opaque `Authorization: Token <40-hex>` auth, session survives refresh
- **App shell** — role-filtered sidebar, building context in the topbar, user menu, mobile drawer
- **Full database schema** — all 57 tables with every FK rule, unique constraint and index
- **Error contract** — `{"detail": "..."}` for business failures, field maps for validation, 401/403/404 shapes

### Verify the journey

Landing → *Get started* → sign up with a building name → land in the dashboard → sign out → sign
back in → refresh the page and stay signed in.

---

## Tests

```bash
cd backend
./mvnw test
```

`AuthFlowTest` covers the auth definition of done: signup creates user + building + setting +
token; duplicate email → 400; weak password → 400; bad login → 400; `/me/` without a token → 401;
and the full signup → login → me → logout journey with the token dying on logout.

Tests run against the separate `nibash_test` schema, so your dev data is never touched.

---

## API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/signup/` | — | Create workspace: admin user + building + modules + token → `201` |
| POST | `/api/auth/login/` | — | `{token, user, building}` → `200` |
| POST | `/api/auth/logout/` | Token | Revokes **all** tokens for the caller |
| GET | `/api/auth/me/` | Token | `{user, building}` — re-hydrates the session |

Trailing slashes are part of the contract. JSON is snake_case throughout.

---


