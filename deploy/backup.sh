#!/usr/bin/env bash
# Daily database backup. Keeps 14 days. Run from the project folder, e.g. from cron:
#   15 3 * * * cd /opt/x-content-bot && ./deploy/backup.sh >> backups/backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p backups
stamp=$(date +%Y%m%d-%H%M%S)
compose="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
$compose exec -T postgres pg_dump -U "${SPRING_DATASOURCE_USERNAME:-xbot}" -d xbot -Fc -f /tmp/xbot.dump
$compose cp postgres:/tmp/xbot.dump "backups/xbot-$stamp.dump"
$compose exec -T postgres rm -f /tmp/xbot.dump
find backups -name 'xbot-*.dump' -mtime +14 -delete
echo "$(date -Is) backup ok: backups/xbot-$stamp.dump ($(du -h "backups/xbot-$stamp.dump" | cut -f1))"
