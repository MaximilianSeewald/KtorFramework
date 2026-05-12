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

exec env JWT_SECRET_KEY="$JWT_SECRET_KEY" java -jar ktor.jar
