#!/usr/bin/env bash
# ============================================================
# migrate.sh — Runs Flyway migration against local PostgreSQL
# Usage: ./scripts/migrate.sh
#
# Prerequisites:
#   - PostgreSQL running (locally or via Docker)
#   - Database 'taut' created
#
# Environment variables (optional, have defaults):
#   DB_HOST     — PostgreSQL host      (default: localhost)
#   DB_PORT     — PostgreSQL port      (default: 5432)
#   DB_NAME     — Database name        (default: taut)
#   DB_USER     — Database user        (default: taut)
#   DB_PASSWORD — Database password    (default: taut)
# ============================================================

set -euo pipefail

# Configuration with defaults
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-taut}"
DB_USER="${DB_USER:-taut}"
DB_PASSWORD="${DB_PASSWORD:-taut}"

JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

# Get script and project root directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "============================================"
echo " TAUT Database Migration"
echo "============================================"
echo " Database: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo " JDBC URL: ${JDBC_URL}"
echo "============================================"
echo ""

# Check if PostgreSQL is reachable
if ! pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
    echo "WARNING: Cannot connect to PostgreSQL at ${DB_HOST}:${DB_PORT}"
    echo "Make sure PostgreSQL is running."
    echo ""
    echo "If using Docker, start it with:"
    echo "  docker compose up -d postgres"
    echo ""
fi

# Navigate to backend directory
cd "$PROJECT_ROOT/backend"

# Check if Gradle wrapper exists
if [ ! -f "./gradlew" ] && [ ! -f "./gradlew.bat" ]; then
    echo "ERROR: Gradle wrapper not found in backend/"
    exit 1
fi

echo "Running Flyway migrations..."
echo ""

# Run the Flyway migration using Gradle
# The backend app runs Flyway on startup, but we can also run
# migrations standalone via the Gradle application plugin
DB_PASSWORD="$DB_PASSWORD" TAUT_DB_URL="$JDBC_URL" TAUT_DB_USER="$DB_USER" \
    ./gradlew --no-daemon flywayMigrate 2>/dev/null || {
    echo "Note: flywayMigrate task not available via Gradle plugin."
    echo "Running migrations through the application instead..."
    echo ""

    # Alternative: use Flyway CLI Docker image to run migrations
    if command -v docker >/dev/null 2>&1; then
        echo "Using Docker Flyway image to run migrations..."
        docker run --rm \
            --network host \
            -v "${PROJECT_ROOT}/backend/src/main/resources/db/migration:/flyway/sql" \
            -e FLYWAY_URL="$JDBC_URL" \
            -e FLYWAY_USER="$DB_USER" \
            -e FLYWAY_PASSWORD="$DB_PASSWORD" \
            -e FLYWAY_SCHEMAS=public \
            flyway/flyway:10 \
            migrate
    else
        echo "ERROR: Neither Gradle flywayMigrate task nor Docker available."
        echo "Options:"
        echo "  1. Run the backend application (./gradlew run) - Flyway runs on startup"
        echo "  2. Install Flyway CLI and run: flyway -url=$JDBC_URL -user=$DB_USER -password=$DB_PASSWORD migrate"
        echo "  3. Use Docker: see command above"
        exit 1
    fi
}

echo ""
echo "Migration completed successfully!"
echo ""
echo "To verify: psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c '\\dt'"
