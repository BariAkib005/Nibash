# Requirements

Everything that must be installed on a machine before Nibash will run, and nothing that doesn't.

---

## Install these three

| # | Software | Required version | Why this version |
| --- | --- | --- | --- |
| 1 | **JDK (Java Development Kit)** | **26** or newer | `<java.version>26</java.version>` in [`backend/pom.xml`](backend/pom.xml) |
| 2 | **Node.js** | **`^20.19.0` or `>=22.12.0`** | Vite 8's own `engines` field. Node 22 LTS or 24 is the safe pick |
| 3 | **MySQL Server** | **8.x**, listening on port `3306` | `mysql-connector-j` + `flyway-mysql`; the schema is MySQL 8 |

That is the whole install list. Everything else the project needs is downloaded automatically on first
build.

### Where to get them

| Software | Windows | macOS | Debian / Ubuntu |
| --- | --- | --- | --- |
| JDK 26 | [Adoptium](https://adoptium.net/) or [jdk.java.net](https://jdk.java.net/) | `brew install openjdk@26` | Adoptium apt repo (distro packages often stop below 26) |
| Node.js | [nodejs.org](https://nodejs.org/) LTS installer | `brew install node` | [NodeSource](https://github.com/nodesource/distributions) or `nvm install 22` |
| MySQL 8 | [MySQL Installer](https://dev.mysql.com/downloads/installer/) (include *MySQL Server*) | `brew install mysql` then `brew services start mysql` | `sudo apt install mysql-server` |

---

## Do NOT install these

- **Maven** — [`backend/mvnw`](backend/mvnw) is the Maven Wrapper. It downloads Maven **3.9.16** by itself
  (see `backend/.mvn/wrapper/maven-wrapper.properties`). Never run a system `mvn`.
- **Spring Boot, Hibernate, Flyway, Jackson, the MySQL driver** — Maven fetches all nine starters into
  `~/.m2` on the first build.
- **React, Vite, TypeScript, Tailwind** — `npm install` fetches these into `frontend/node_modules`.
- **Docker, Docker Compose, Nginx** — the README's stack table mentions them as the deployment target, but
  there are **no Dockerfiles, compose files, or nginx configs in this repository**. There is no container
  path yet; run the two processes directly.
- **A MySQL command-line client** — only needed if you want to run `db/setup.sql` from a terminal. MySQL
  Workbench, DBeaver, or any GUI does the same job.

> **The first build needs internet.** The Maven wrapper downloads Maven and ~9 starters, and `npm install`
> downloads the frontend tree. After that both work offline.

---

## Commands

Two shells are shown because they genuinely differ on Windows. Run every command from the repository
root unless the comment says otherwise.

### One-time setup (per machine)

**Windows (PowerShell)**

```powershell
# 1. Create the databases and the app user (needs MySQL admin rights).
#    NOTE: PowerShell has no "<" redirection — pipe the file instead.
Get-Content db\setup.sql | mysql -u root -p

# 2. Create the backend config (.env is gitignored, so a fresh clone has none).
Copy-Item backend\.env.example backend\.env

# 3. Install the frontend packages.
cd frontend
npm install
cd ..
```

**macOS / Linux (bash or zsh)**

```bash
mysql -u root -p < db/setup.sql       # 1. databases + app user
cp backend/.env.example backend/.env  # 2. backend config
(cd frontend && npm install)          # 3. frontend packages
```

Do **not** create tables by hand — Flyway builds all 57 on the backend's first boot.

### Every time you want to run the app

Two terminals, one per process. Leave both open — these are long-running servers, not tasks that finish.

**Terminal 1 — backend** → <http://localhost:8000>

```powershell
# Windows. Needed in every new terminal unless JDK 26 is already on your PATH.
$env:JAVA_HOME = "C:\Users\User\.jdks\openjdk-26.0.2"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd backend
.\mvnw.cmd spring-boot:run
```

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk-26        # skip if `java -version` already reports 26
cd backend && ./mvnw spring-boot:run
```

**Terminal 2 — frontend** → <http://127.0.0.1:5173>

```bash
cd frontend
npm run dev
```

Open <http://127.0.0.1:5173> in a browser. Stop either server with `Ctrl+C`.

To avoid setting `JAVA_HOME` in every new terminal, set it permanently once:

```powershell
setx JAVA_HOME "C:\Users\User\.jdks\openjdk-26.0.2"   # then reopen the terminal
```

### Load the demo data (optional)

Seeds ten accounts, two buildings, invoices, tickets, notices, polls and gate passes. Idempotent — safe
to run repeatedly. Credentials are in [README.md](README.md#demo-accounts).

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--seed"
```

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed
```

The `--seed` run also starts the server normally, so this replaces the usual backend command rather than
being an extra step.

### Other useful commands

| Command | What it does |
| --- | --- |
| `cd backend` → `.\mvnw.cmd test` | Run the integration suite against the `nibash_test` schema |
| `cd backend` → `.\mvnw.cmd clean package` | Build the backend jar into `backend/target/` |
| `cd frontend` → `npm run build` | Type-check and build the production frontend into `frontend/dist/` |
| `cd frontend` → `npm run lint` | Lint the frontend with oxlint |
| `cd frontend` → `npm run preview` | Serve the built frontend locally |
| `net start MySQL80` | Start MySQL on Windows if it isn't running |
| `brew services start mysql` / `sudo systemctl start mysql` | Same, on macOS / Linux |

On macOS and Linux use `./mvnw` in place of `.\mvnw.cmd`.

### Where things listen

| Service | URL |
| --- | --- |
| Backend API | <http://localhost:8000> |
| Frontend (Vite dev server) | <http://127.0.0.1:5173> |
| MySQL | `localhost:3306` |

The Vite dev server proxies `/api`, `/media` and `/ws` to port 8000, so there is no CORS setup and no
absolute API URL in the app code.

---

## Common first-run failures

| Symptom | Cause | Fix |
| --- | --- | --- |
| `java: command not found` / wrong Java version | JDK not on `PATH` | Set `JAVA_HOME` to your JDK 26 and add `$JAVA_HOME/bin` to `PATH`. PowerShell: `$env:JAVA_HOME = "C:\Users\<you>\.jdks\openjdk-26.0.2"` |
| `release version 26 not supported` | An older JDK is being used | Point `JAVA_HOME` at JDK 26 — the build will not fall back |
| `Access denied for user 'nibash'@'localhost'` | `db/setup.sql` not run, or the password differs from `.env` | Run step 1, then make `MYSQL_PASSWORD` in `backend/.env` match |
| `Communications link failure` | MySQL isn't running | Start the service (`net start MySQL80`, `brew services start mysql`, `sudo systemctl start mysql`) |
| Backend starts but every request 500s | Flyway couldn't migrate | Confirm the `nibash` database exists and the `nibash` user has rights on it |
| Vite exits on `npm run dev` with an internal/unsupported-API error | Node is below the version floor | Upgrade to Node 22.12+ (check with `node -v`) |
| `Port 8000 already in use` | Something else holds the port | Change `SERVER_PORT` in `backend/.env` |

---

## Verified on

This exact combination was used to run the full stack successfully:

| | Version |
| --- | --- |
| OS | Windows 11 Pro (build 22631) |
| JDK | OpenJDK **26.0.2** (build 26.0.2+10-55) |
| Node.js | **24.14.0** |
| npm | **11.9.0** |
| MySQL | **8.0.44** Community Server |
| Maven | **3.9.16** (supplied by the wrapper) |

Linux and macOS need the same three dependencies; use `./mvnw` instead of `.\mvnw.cmd`.

---

## Running the tests

The integration suite needs the same three dependencies and nothing more. It runs against the separate
`nibash_test` schema that `db/setup.sql` creates, so your dev data is untouched:

```bash
cd backend && ./mvnw test
```
