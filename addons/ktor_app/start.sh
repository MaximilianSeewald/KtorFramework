#!/usr/bin/env sh
set -eu

OPTIONS_FILE="/data/options.json"
SECRET_FILE="/data/jwt_secret"
PLACEHOLDER_SECRET="change-this-to-a-secure-key"

configured_secret=""
if [ -f "$OPTIONS_FILE" ]; then
  configured_secret="$(sed -n 's/.*"jwt_secret"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$OPTIONS_FILE" | head -n 1)"
fi

if [ -n "$configured_secret" ] && [ "$configured_secret" != "$PLACEHOLDER_SECRET" ]; then
  export JWT_SECRET_KEY="$configured_secret"
else
  if [ ! -s "$SECRET_FILE" ]; then
    secret="$(cat /proc/sys/kernel/random/uuid)$(cat /proc/sys/kernel/random/uuid)"
    printf '%s' "$secret" | sed 's/-//g' > "$SECRET_FILE"
    chmod 600 "$SECRET_FILE"
  fi
  export JWT_SECRET_KEY="$(cat "$SECRET_FILE")"
fi

exec java -jar ktor.jar
