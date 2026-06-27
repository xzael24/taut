#!/bin/bash
# ==============================================================================
# backup.sh — TAUT Production Backup Script
# Backs up PostgreSQL (pg_dump) and SQLCipher/SQLite databases
# Schedule via cron:  0 2 * * * /path/to/backup.sh
# ==============================================================================
set -euo pipefail

# ── Configuration ───────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR" && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="${TAUT_BACKUP_DIR:-${PROJECT_DIR}/backups}"
PG_CONTAINER="${TAUT_PG_CONTAINER:-taut-postgres}"
PG_USER="${TAUT_DB_USER:-taut}"
PG_DB="${TAUT_DB_NAME:-taut}"
PG_HOST="${TAUT_PG_HOST:-localhost}"
PG_PORT="${TAUT_PG_PORT:-5432}"
RETENTION_DAYS="${TAUT_BACKUP_RETENTION:-7}"
LOG_FILE="${BACKUP_DIR}/backup.log"

# ── Colors ──────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ── Helpers ─────────────────────────────────────────────────
log()  { echo -e "$(date '+%Y-%m-%d %H:%M:%S') [INFO]  $*" | tee -a "$LOG_FILE"; }
warn() { echo -e "$(date '+%Y-%m-%d %H:%M:%S') [WARN]  $*" | tee -a "$LOG_FILE"; }
err()  { echo -e "$(date '+%Y-%m-%d %H:%M:%S') [ERROR] $*" | tee -a "$LOG_FILE"; exit 1; }
ok()   { echo -e "${GREEN}$(date '+%Y-%m-%d %H:%M:%S') [OK]    $*${NC}"; }

# ── Prerequisites ───────────────────────────────────────────
mkdir -p "${BACKUP_DIR}/{postgres,sqlcipher,archive}"

# Check required tools
command -v pg_dump >/dev/null 2>&1 || err "pg_dump is not installed. Install postgresql-client."
command -v sqlite3  >/dev/null 2>&1 || warn "sqlite3 is not installed. SQLCipher backup will be skipped."
command -v gzip     >/dev/null 2>&1 || err "gzip is not installed."

# ══════════════════════════════════════════════════════════════
# 1. PostgreSQL Backup
# ══════════════════════════════════════════════════════════════
backup_postgres() {
    local backup_file="${BACKUP_DIR}/postgres/taut-pg_${TIMESTAMP}.sql.gz"

    log "Starting PostgreSQL backup → ${backup_file}"

    # Using PG* env vars for credentials (set via .env)
    PGPASSWORD="${TAUT_DB_PASSWORD:-}" \
    pg_dump \
        -h "${PG_HOST}" \
        -p "${PG_PORT}" \
        -U "${PG_USER}" \
        -d "${PG_DB}" \
        --format=custom \
        --compress=9 \
        --verbose \
        --no-owner \
        --no-acl \
        --file="${backup_file}" 2>>"${LOG_FILE}"

    if [ $? -eq 0 ]; then
        ok "PostgreSQL backup completed: $(ls -lh "${backup_file}" | awk '{print $5}')"
    else
        err "PostgreSQL backup FAILED"
    fi
}

# ══════════════════════════════════════════════════════════════
# 2. SQLCipher / SQLite Backup
# Scans for .db / .sqlite / .sqlite3 files in configured paths
# ══════════════════════════════════════════════════════════════
backup_sqlcipher() {
    local sqlcipher_dirs=(
        "${PROJECT_DIR}/data"
        "${PROJECT_DIR}/app/src/main/assets/databases"
        "${PROJECT_DIR}/android/app/src/main/assets/databases"
    )

    log "Scanning for SQLCipher/SQLite databases..."

    local db_count=0
    for dir in "${sqlcipher_dirs[@]}"; do
        if [ -d "$dir" ]; then
            while IFS= read -r -d '' db_file; do
                db_count=$((db_count + 1))
                local db_rel="${db_file#${PROJECT_DIR}/}"
                local db_safe="${db_rel//\//_}"
                local backup_file="${BACKUP_DIR}/sqlcipher/${db_safe}_${TIMESTAMP}.sql.gz"

                log "Backing up SQLite/SQLCipher database: ${db_rel}"

                # Attempt SQLite dump (works for SQLCipher with default passphrase if unencrypted)
                if sqlite3 "${db_file}" ".dump" 2>/dev/null | gzip > "${backup_file}"; then
                    ok "  ✓ $(basename "${db_file}"): $(ls -lh "${backup_file}" | awk '{print $5}')"
                else
                    # Possibly encrypted — copy raw file as fallback
                    warn "  ⚠ SQLite dump failed (likely encrypted). Copying raw file instead."
                    cp "${db_file}" "${BACKUP_DIR}/sqlcipher/${db_rel}_${TIMESTAMP}.raw"
                fi
            done < <(find "${dir}" -type f \( -name "*.db" -o -name "*.sqlite*" \) -print0 2>/dev/null)
        fi
    done

    if [ "$db_count" -eq 0 ]; then
        log "No SQLCipher/SQLite databases found (this is normal unless running on Android)."
    else
        ok "Backed up ${db_count} SQLite/SQLCipher database(s)."
    fi
}

# ══════════════════════════════════════════════════════════════
# 3. Archive & Retention
# ══════════════════════════════════════════════════════════════
cleanup_old_backups() {
    log "Removing backups older than ${RETENTION_DAYS} days..."

    find "${BACKUP_DIR}/postgres" -name "taut-pg_*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete
    find "${BACKUP_DIR}/sqlcipher" -name "*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete
    find "${BACKUP_DIR}/sqlcipher" -name "*.raw" -mtime "+${RETENTION_DAYS}" -delete

    ok "Old backups cleaned (retention: ${RETENTION_DAYS} days)."
}

# ══════════════════════════════════════════════════════════════
# 4. Verification
# ══════════════════════════════════════════════════════════════
verify_backups() {
    local pg_count sql_count
    pg_count=$(find "${BACKUP_DIR}/postgres" -name "taut-pg_*.sql.gz" | wc -l)
    sql_count=$(find "${BACKUP_DIR}/sqlcipher" -name "*.sql.gz" -o -name "*.raw" | wc -l)

    echo ""
    echo "┌─────────────────────────────────────────────────────────────┐"
    echo "│  TAUT Backup Summary — $(date '+%Y-%m-%d %H:%M:%S')               │"
    echo "├─────────────────────────────────────────────────────────────┤"
    printf "│  PostgreSQL backups:        %-5d                           │\n" "$pg_count"
    printf "│  SQLCipher/SQLite backups:  %-5d                           │\n" "$sql_count"
    printf "│  Retention:                 %-5d days                      │\n" "$RETENTION_DAYS"
    printf "│  Total size:                %-5s                          │\n" "$(du -sh "${BACKUP_DIR}" 2>/dev/null | awk '{print $1}')"
    echo "└─────────────────────────────────────────────────────────────┘"
}

# ══════════════════════════════════════════════════════════════
# Main
# ══════════════════════════════════════════════════════════════
main() {
    echo "═══════════════════════════════════════════════════════════════"
    echo "  TAUT Backup Script — $(date '+%Y-%m-%d %H:%M:%S')"
    echo "  Project: ${PROJECT_DIR}"
    echo "  Backup:  ${BACKUP_DIR}"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""

    backup_postgres
    echo ""
    backup_sqlcipher
    echo ""
    cleanup_old_backups
    echo ""
    verify_backups

    echo ""
    ok "Backup script completed successfully."
}

main "$@"
