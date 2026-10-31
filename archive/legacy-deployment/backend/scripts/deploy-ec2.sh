#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-/tmp/ebike-backend.env}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$BACKEND_DIR"

if [ -f "$ENV_FILE" ]; then
  mv "$ENV_FILE" "$BACKEND_DIR/.env"
  chmod 600 "$BACKEND_DIR/.env"
fi

docker compose -f docker-compose.ec2.yml up -d postgres redis

DEPLOY_POSTGRES_USER="$(awk -F= '$1 == "POSTGRES_USER" {sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit}' .env)"
DEPLOY_POSTGRES_PASSWORD="$(awk -F= '$1 == "POSTGRES_PASSWORD" {sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit}' .env)"
DEPLOY_POSTGRES_DB="$(awk -F= '$1 == "POSTGRES_DB" {sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit}' .env)"

echo "Waiting for PostgreSQL..."
for i in $(seq 1 60); do
  POSTGRES_HEALTH="$(docker inspect -f '{{.State.Health.Status}}' ebike-postgres 2>/dev/null || echo starting)"
  if [ "$POSTGRES_HEALTH" = "healthy" ]; then
    break
  fi

  if [ "$i" -eq 60 ]; then
    docker logs --tail 120 ebike-postgres
    exit 1
  fi

  sleep 2
done

echo "Syncing PostgreSQL role and database..."
docker compose -f docker-compose.ec2.yml exec -T \
  -e DEPLOY_POSTGRES_USER="$DEPLOY_POSTGRES_USER" \
  -e DEPLOY_POSTGRES_PASSWORD="$DEPLOY_POSTGRES_PASSWORD" \
  -e DEPLOY_POSTGRES_DB="$DEPLOY_POSTGRES_DB" \
  postgres sh -s <<'POSTGRES_SYNC'
set -e

ADMIN_USER=
for candidate in postgres "$POSTGRES_USER" "$DEPLOY_POSTGRES_USER" ebike_prod_user ebike_user; do
  if psql -U "$candidate" -d postgres -tAc "SELECT 1" >/dev/null 2>&1; then
    ADMIN_USER="$candidate"
    break
  fi
done

if [ -z "$ADMIN_USER" ]; then
  echo "Khong tim thay PostgreSQL admin role trong volume hien tai."
  echo "Hay SSH vao EC2 va chay: docker exec ebike-postgres psql -U <role-cu> -d postgres -c '\\du'"
  echo "Neu khong can giu du lieu DB, xoa volume postgres_data roi deploy lai."
  exit 1
fi

psql -U "$ADMIN_USER" -d postgres -v ON_ERROR_STOP=1 \
  -v db_user="$DEPLOY_POSTGRES_USER" \
  -v db_password="$DEPLOY_POSTGRES_PASSWORD" \
  -v db_name="$DEPLOY_POSTGRES_DB" <<'SQL'
SELECT CASE
  WHEN EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')
  THEN format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'db_user', :'db_password')
  ELSE format('CREATE ROLE %I WITH LOGIN PASSWORD %L', :'db_user', :'db_password')
END
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'db_name', :'db_user')
\gexec
SQL

psql -U "$ADMIN_USER" -d "$DEPLOY_POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -v db_user="$DEPLOY_POSTGRES_USER" <<'SQL'
SELECT format('ALTER SCHEMA public OWNER TO %I', :'db_user')
\gexec

SELECT format('GRANT ALL ON SCHEMA public TO %I', :'db_user')
\gexec

SELECT format('GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO %I', :'db_user')
\gexec

SELECT format('GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO %I', :'db_user')
\gexec
SQL
POSTGRES_SYNC

test -f src/main/java/com/ebike/adminModule/controller/AdminController.java

echo "Removing old backend container..."
docker compose -f docker-compose.ec2.yml stop backend || true
docker compose -f docker-compose.ec2.yml rm -f backend || true

echo "Building backend image without cache..."
docker compose --progress=plain -f docker-compose.ec2.yml build --no-cache backend

echo "Recreating backend container..."
docker compose -f docker-compose.ec2.yml up -d --no-deps --force-recreate backend
docker compose -f docker-compose.ec2.yml ps

echo "Waiting for backend health..."
for i in $(seq 1 60); do
  if docker exec ebike-backend curl -fsS http://localhost:8080/api/v1/health/ready >/dev/null; then
    break
  fi

  if [ "$i" -eq 60 ]; then
    docker logs --tail 160 ebike-backend
    exit 1
  fi

  sleep 2
done

docker exec ebike-backend sh -lc 'grep -a AdminController /app/app.jar >/dev/null'
ADMIN_STATUS="$(docker exec ebike-backend curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/admin/pricing-rules)"
if [ "$ADMIN_STATUS" != "401" ] && [ "$ADMIN_STATUS" != "403" ]; then
  echo "Expected protected admin API to return 401/403 without a token, got $ADMIN_STATUS"
  exit 1
fi

echo "Backend deploy complete."
