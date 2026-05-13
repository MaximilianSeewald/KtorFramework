#!/usr/bin/env sh
set -eu

SECRET_FILE="/data/jwt_secret"
PLACEHOLDER_SECRET="change-this-to-a-secure-key"

if [ -n "${JWT_SECRET_KEY:-}" ] && [ "$JWT_SECRET_KEY" != "$PLACEHOLDER_SECRET" ]; then
  :
else
  if [ ! -s "$SECRET_FILE" ]; then
    secret="$(cat /proc/sys/kernel/random/uuid)$(cat /proc/sys/kernel/random/uuid)"
    printf '%s' "$secret" | sed 's/-//g' > "$SECRET_FILE"
    chmod 600 "$SECRET_FILE"
  fi
  export JWT_SECRET_KEY="$(cat "$SECRET_FILE")"
fi

if [ -n "${SUPERVISOR_TOKEN:-}" ]; then
  curl -fsS \
    -X POST \
    -H "Authorization: Bearer $SUPERVISOR_TOKEN" \
    -H "Content-Type: application/json" \
    --data '{"ingress_panel":true}' \
    "http://supervisor/addons/self/options" \
    >/dev/null || true
fi

exec env \
  JWT_SECRET_KEY="$JWT_SECRET_KEY" \
  DATABASE_PATH="${DATABASE_PATH:-/data/db}" \
  DATABASE_BACKUP_PATH="${DATABASE_BACKUP_PATH:-/data/backups}" \
  java -jar ktor.jar
