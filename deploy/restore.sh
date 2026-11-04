#!/usr/bin/env bash
# Restores a pg_dump (custom format) into the server database, replacing what is there.
#   ./deploy/restore.sh path/to/xbot.dump
set -euo pipefail
cd "$(dirname "$0")/.."
dump="${1:?Usage: ./deploy/restore.sh path/to/xbot.dump}"
compose="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
read -r -p "This replaces ALL data in the server database with $dump. Type YES to continue: " ok
[ "$ok" = "YES" ] || { echo "Cancelled."; exit 1; }
$compose stop backend
$compose up -d postgres
sleep 5
$compose cp "$dump" postgres:/tmp/restore.dump
$compose exec -T postgres pg_restore -U "${SPRING_DATASOURCE_USERNAME:-xbot}" -d xbot --clean --if-exists --no-owner /tmp/restore.dump
$compose exec -T postgres rm -f /tmp/restore.dump
$compose up -d backend
echo "Restore complete."
