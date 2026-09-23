#!/usr/bin/env bash
# Restore a backup made by db-backup.sh. DESTRUCTIVE: every table in the dump replaces the one in
# the target database, so the script refuses to run without --yes.
#
#   scripts/db-restore.sh backups/nibash-20260923-210000.sql.gz --yes            # local MySQL
#   scripts/db-restore.sh backups/nibash-20260923-210000.sql.gz --docker --yes   # compose `mysql`
#   scripts/db-restore.sh <file> --database nibash_restore_check --yes           # restore drill
#
# The target database must already exist. Restart the API afterwards so no stale connection
# outlives the swap. MYSQL_BIN_DIR works as in db-backup.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${1:-}"
shift || true
MODE="local"; TARGET=""; CONFIRMED="no"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --docker) MODE="docker" ;;
    --database) TARGET="$2"; shift ;;
    --yes) CONFIRMED="yes" ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

[[ -f "$FILE" ]] || { echo "Usage: $0 <backup.sql.gz> [--docker] [--database NAME] --yes" >&2; exit 2; }
gzip -t "$FILE" || { echo "$FILE is not a readable gzip file." >&2; exit 1; }
gzip -dc "$FILE" | tail -n 3 | grep -q "Dump completed" || { echo "$FILE is an incomplete dump — refusing." >&2; exit 1; }

if [[ "$MODE" == "docker" ]]; then
  cd "$ROOT"
  TARGET="${TARGET:-$(docker compose exec -T mysql printenv MYSQL_DATABASE | tr -d '\r')}"
else
  ENV_FILE="$ROOT/backend/.env"
  get() { grep -E "^$1=" "$ENV_FILE" | tail -n1 | cut -d= -f2- | tr -d '\r'; }
  TARGET="${TARGET:-$(get MYSQL_DATABASE)}"
fi

if [[ "$CONFIRMED" != "yes" ]]; then
  echo "This replaces the data in database '$TARGET' ($MODE) with $FILE."
  echo "Re-run with --yes to proceed."
  exit 3
fi

if [[ "$MODE" == "docker" ]]; then
  gzip -dc "$FILE" | docker compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 "$0"' "$TARGET"
else
  CLIENT="mysql"
  [[ -n "${MYSQL_BIN_DIR:-}" ]] && CLIENT="$MYSQL_BIN_DIR/mysql"
  gzip -dc "$FILE" | MYSQL_PWD="$(get MYSQL_PASSWORD)" "$CLIENT" -h "$(get MYSQL_HOST)" -P "$(get MYSQL_PORT)" \
    -u "$(get MYSQL_USER)" --default-character-set=utf8mb4 "$TARGET"
fi

echo "Restored $FILE into '$TARGET' ($MODE). Restart the API to drop any stale connections."
