# Ktor App

This add-on provides a Home Assistant focused shopping list and recipe list.

## Data and backups

The add-on runs the backend in Home Assistant mode with explicit persistence paths:

```text
DATABASE_PATH=/data/db
DATABASE_BACKUP_PATH=/data/backups
```

Home Assistant mounts `/data` for the add-on and includes it in normal full/add-on backups. That means the live H2 database and any manual export zips under `/data/backups` are covered by HA backup expectations.

For a manual export, stop the add-on first unless you have intentionally enabled an H2 mode that supports concurrent access, then run the packaged backup command alongside the jar:

```sh
DATABASE_PATH=/data/db DATABASE_BACKUP_PATH=/data/backups java -cp ktor.jar com.loudless.database.DatabaseBackupCommandKt
```

The export file is named `ktor-framework-h2-backup-YYYYMMDD-HHMMSS.zip`.

To restore, stop the add-on, keep a copy of the current `/data/db.mv.db`, extract the selected backup zip back into `/data`, and start the add-on again. Confirm readiness before using the UI.

The built-in Home Assistant user group is `ha_instance`. Custom group names in non-HA deployments must be table-safe: 3-48 characters, start with a letter, and use only letters, digits, or underscores.

## Health checks and operations

The Docker healthcheck uses backend readiness:

```text
GET /health/ready
```

Readiness returns `200` only when the backend can validate its H2 connection. Liveness is also available at `/health/live` for checking whether the process is responding without dependency checks.

Smoke test the add-on from inside the container or equivalent shell with:

```sh
curl -fsS http://localhost:8080/health/live
curl -fsS http://localhost:8080/health/ready
test "$(curl -sS -o /dev/null -w '%{http_code}' http://localhost:8080/api/verify)" = "401"
```

Before upgrading, make sure Home Assistant has a fresh backup or create a manual export under `/data/backups`. After upgrade, start the add-on and verify `/health/ready`, then open the ingress UI. For rollback, stop the add-on, restore the previous add-on package or Home Assistant backup, restore the matching database backup if needed, and repeat the smoke tests.

Each response includes `X-Request-ID`, and backend logs include the same value as `request_id` with method, path, status, and duration.

## Lovelace card through HACS

The shopping list Lovelace card is installed through HACS as a dashboard resource from this repository.

1. Install and start the `Ktor App` Home Assistant add-on.
2. In HACS, add this GitHub repository as a custom repository with the `Dashboard` category.
3. Install `Ktor Shopping List Card` from HACS.
4. Confirm the dashboard resource points to the HACS-served module:

```yaml
url: /hacsfiles/KtorFramework/KtorFramework.js
type: module
```

After the resource is loaded, add `Ktor Shopping List` from the dashboard card picker.

If the card works when added manually but does not appear in the picker, add it with the `Manual` card. Some Home Assistant frontend versions do not list every HACS-served custom card in the picker even when the card module is loaded correctly.

Manual card YAML:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
addon_slug: ktor_app
show_completed: true
```

`addon_slug` is the stable Home Assistant add-on slug from `config.yaml`. The card uses it to resolve the current dynamic ingress URL, such as `/api/hassio_ingress/<generated-token>/`, at runtime. Do not hardcode that generated ingress URL for normal usage.

Optional picker fallback:

```yaml
frontend:
  extra_module_url:
    - /hacsfiles/KtorFramework/KtorFramework.js
```

This makes Home Assistant load the module as a frontend extra module, which can help card picker discovery on frontend versions that miss HACS dashboard resources.

For troubleshooting or custom deployments, you can bypass add-on lookup with an explicit backend URL:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
backend_url: /api/hassio_ingress/CURRENT_GENERATED_INGRESS_PATH/
show_completed: true
```
