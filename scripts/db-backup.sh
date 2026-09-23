#!/usr/bin/env bash
# Back up the Nibash database to backups/<db>-<timestamp>.sql.gz and keep the newest $KEEP files.
#
#   scripts/db-backup.sh            # local MySQL, credentials from backend/.env
#   scripts/db-backup.sh --docker   # the `mysql` service of docker-compose.yml
#
# Environment:
#   KEEP=14                 how many backups to keep (older ones are deleted)
#   BACKUP_DIR=backups      where to write them
#   MYSQL_BIN_DIR=...       folder holding mysqldump if it is not on PATH
#                           (Windows: "C:/Program Files/MySQL/MySQL Server 8.0/bin")
#
# The dump is a consistent snapshot (--single-transaction) taken without locking the app out.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEEP="${KEEP:-14}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"
MODE="local"
[[ "${1:-}" == "--docker" ]] && MODE="docker"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
DUMP_FLAGS=(--single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4)

if [[ "$MODE" == "docker" ]]; then
  cd "$ROOT"
  DB="$(docker compose exec -T mysql printenv MYSQL_DATABASE | tr -d '\r')"
  OUT="$BACKUP_DIR/$DB-docker-$STAMP.sql.gz"
  # Root credentials stay inside the container; nothing secret crosses the command line.
  docker compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot '"${DUMP_FLAGS[*]}"' "$MYSQL_DATABASE"' \
    | gzip -9 > "$OUT"
else
  ENV_FILE="$ROOT/backend/.env"
  [[ -f "$ENV_FILE" ]] || { echo "No $ENV_FILE — copy backend/.env.example first." >&2; exit 1; }
  # Read only the keys we need; values may contain spaces but not newlines.
  get() { grep -E "^$1=" "$ENV_FILE" | tail -n1 | cut -d= -f2- | tr -d '\r'; }
  HOST="$(get MYSQL_HOST)"; PORT="$(get MYSQL_PORT)"; DB="$(get MYSQL_DATABASE)"; USER="$(get MYSQL_USER)"
  DUMP="mysqldump"
  [[ -n "${MYSQL_BIN_DIR:-}" ]] && DUMP="$MYSQL_BIN_DIR/mysqldump"
  OUT="$BACKUP_DIR/$DB-$STAMP.sql.gz"
  MYSQL_PWD="$(get MYSQL_PASSWORD)" "$DUMP" -h "${HOST:-localhost}" -P "${PORT:-3306}" -u "$USER" \
    "${DUMP_FLAGS[@]}" "$DB" | gzip -9 > "$OUT"
fi

# A dump that ends early is worse than none — check mysqldump wrote its completion marker.
if ! gzip -dc "$OUT" | tail -n 3 | grep -q "Dump completed"; then
  echo "Backup looks incomplete: $OUT" >&2
  exit 1
fi

echo "Backup written: $OUT ($(du -h "$OUT" | cut -f1))"

# Retention: newest $KEEP files for this mode survive.
PATTERN="$DB-$([[ "$MODE" == "docker" ]] && echo "docker-")[0-9]*.sql.gz"
ls -1t "$BACKUP_DIR"/$PATTERN 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do
  rm -f -- "$old" && echo "Pruned $old"
done
