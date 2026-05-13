# KtorFramework

KtorFramework is a Kotlin/Ktor backend with Angular frontends for recipes, shopping lists, user groups, and supporting tools. The repository is organized around two delivery targets:

1. Home Assistant: an add-on, an HA-focused Angular app, and a HACS Lovelace shopping-list card.
2. Standard web app/API: the regular Angular app served by the same Ktor backend.

## Repository Layout

```text
addons/ktor_app/   Home Assistant add-on package, Dockerfile, add-on config, built app assets, and add-on docs
backend/           Ktor 3 backend, H2 database, JWT auth, API routes, websocket routes, and tests
dist/              HACS-distributed Lovelace card bundle: KtorFramework.js
webapp/            Angular 19 workspace with web-app and ha-app applications plus shared feature/core libraries
deploy/            Generated deployment output for local builds; ignored by git
```

## Home Assistant

The Home Assistant side contains three related pieces:

- `addons/ktor_app`: a Home Assistant add-on that runs the Ktor backend and serves the HA Angular app through ingress.
- `webapp/apps/ha-app`: a compact Angular app for HA usage. It routes directly to shopping list and recipe screens and enables automatic HA session login.
- `dist/KtorFramework.js`: a HACS dashboard resource that registers the `custom:ktor-shopping-list-card` Lovelace card.

### Add-on Behavior

The add-on is configured in `addons/ktor_app/config.yaml`:

- Add-on slug: `ktor_app`
- Ingress: enabled on internal port `8080`
- Panel title: `Recipes & Shopping`
- Panel icon: `mdi:chef-hat`
- Data storage: enabled through the Home Assistant `/data` mount
- Environment: `HA_MODE=true`
- Database path: explicitly set to `/data/db`
- Backup path: explicitly set to `/data/backups`
- JWT secret: uses a real `JWT_SECRET_KEY` environment value first; otherwise generates one once and stores it at `/data/jwt_secret`

When `HA_MODE` is enabled, the backend creates and uses a default Home Assistant user/group:

- User: `ha-user`
- Group: `ha_instance`
- Registration through the normal `/api/user` endpoint is blocked.
- `/api/ha/session` issues the auto-login token used by the HA frontend.

The backend stores H2 data at `/data/db` inside the add-on container. Home Assistant full and add-on backups include the add-on `/data` directory, so the database and `/data/backups` exports are covered by normal HA backup flows.

### Installing The Add-on

Add this repository to Home Assistant as an add-on repository:

```yaml
name: "Ktor Framework Add-ons"
url: "https://github.com/MaximilianSeewald/KtorFramework"
maintainer: "Maximilian Seewald"
```

Then install and start `Ktor App`. The add-on exposes the UI through Home Assistant ingress, so no external port needs to be configured for normal use.

### Lovelace Card Through HACS

The shopping-list Lovelace card is distributed from this repository as a HACS Dashboard resource.

1. Install and start the `Ktor App` add-on.
2. In HACS, add this repository as a custom repository with the `Dashboard` category.
3. Install `Ktor Shopping List Card`.
4. Confirm the dashboard resource points to:

```yaml
url: /hacsfiles/KtorFramework/KtorFramework.js
type: module
```

Card YAML:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
addon_slug: ktor_app
show_completed: true
```

`addon_slug` is stable. The card uses it to resolve the current Home Assistant ingress URL at runtime, so the generated `/api/hassio_ingress/...` URL should not be hardcoded for normal usage.

If the card works through manual YAML but does not appear in the card picker, use the `Manual` card or load it as an extra frontend module:

```yaml
frontend:
  extra_module_url:
    - /hacsfiles/KtorFramework/KtorFramework.js
```

For custom deployments or troubleshooting, the card can bypass add-on lookup with an explicit backend URL:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
backend_url: /api/hassio_ingress/CURRENT_GENERATED_INGRESS_PATH/
show_completed: true
```

### HA Development Commands

Install frontend dependencies once:

```bash
cd webapp
npm install
```

Run the HA frontend locally:

```bash
npm run start:ha
```

This serves `ha-app` at `http://localhost:4201` and uses `http://localhost:8080/api` for the backend.

Build the HA frontend:

```bash
cd webapp
npm run build:ha
```

The production HA build uses relative `api` URLs, enables `haAutoLogin`, and writes output to `deploy/ha-app`.

Build the backend fat jar:

```bash
./gradlew backend:buildFatJar
```

For an add-on package, `addons/ktor_app` must contain:

- `ktor.jar`: the backend fat jar
- `app/`: the Angular browser output served by the backend
- `Dockerfile`
- `config.yaml`

## Standard Web App And API

The standard app is the regular Angular/Ktor deployment. It keeps local auth flows enabled and includes the broader route set.

### Standard Web Features

The `web-app` Angular application includes routes for:

- Landing page
- Login and registration
- Password change
- Shopping list dashboard
- Recipe list
- User info
- Calculator

The standard production environment uses relative API URLs:

```ts
apiUrl: 'api'
wsUrl: 'api'
haAutoLogin: false
```

### Backend Features

The Ktor backend provides:

- JWT authentication and `/api/verify`
- User signup, login, password changes, and user group membership
- Shopping list HTTP endpoints and websocket updates
- Recipe HTTP endpoints and websocket updates
- User group creation, editing, deletion, and admin lookup
- CSV grade upload endpoint
- Public health checks at `/health/live` and `/health/ready`
- Request logs with `request_id`, `method`, `path`, `status`, and `duration_ms`
- Consistent JSON responses for unexpected server errors
- Static Angular asset hosting from `app/browser`
- H2 persistence through Exposed and HikariCP

Required runtime settings:

```text
JWT_SECRET_KEY  Secret used for JWT signing. Production rejects weak secrets.
DATABASE_PATH   Absolute H2 file path outside the app working directory when APP_ENV=production.
```

Optional runtime settings:

```text
APP_ENV               Defaults to production. Use development only for local work.
KTOR_HOST            Defaults to 0.0.0.0.
KTOR_PORT            Defaults to 8080.
DATABASE_BACKUP_PATH Defaults next to DATABASE_PATH, or /data/backups in HA mode.
JWT_TOKEN_TTL_MS     Token lifetime in milliseconds.
CORS_ALLOWED_ORIGINS Comma-separated origins. Usually unnecessary for same-origin production.
HA_MODE              Set true only for Home Assistant mode.
H2_MODE              H2 URL mode suffix. Leave empty; allowed explicit value is AUTO_SERVER=TRUE.
```

Optional `config.properties` file in the backend working directory. Start from `config.example.properties`:

```properties
APP_ENV=production
CORS_ALLOWED_ORIGINS=https://example.com,https://www.example.com
DATABASE_PATH=/var/lib/ktor-framework/db
```

Production builds of both Angular apps use relative `api` URLs, so the normal same-origin deployment does not need CORS. If `CORS_ALLOWED_ORIGINS` is omitted, production does not install CORS. If `APP_ENV=development` and no explicit CORS origins are configured, the backend allows the local Angular dev origins on ports `4200` and `4201`.

Swagger UI is available only when `APP_ENV=development`.

Production startup refuses to use `./data/db` or any database path under the current application working directory. This avoids accidental local writes when a deployment forgot to configure persistence. For local development, set `APP_ENV=development`; then the backend may use `./data/db` if no explicit `DATABASE_PATH` is provided. The legacy JVM property `ktor.database.path` still works as a deprecated alias for `DATABASE_PATH`.

User group names become H2 table identifiers for shopping lists and recipes. New names must be 3-48 characters, start with a letter, contain only letters, digits, or underscores, must not end with `_recipe`, and must not use reserved table names.

### Database Backups

Create an offline H2 backup with:

```bash
DATABASE_PATH=/var/lib/ktor-framework/db DATABASE_BACKUP_PATH=/var/backups/ktor-framework ./gradlew backend:backupDatabase
```

On PowerShell:

```powershell
$env:DATABASE_PATH = "C:\ProgramData\KtorFramework\db"
$env:DATABASE_BACKUP_PATH = "C:\ProgramData\KtorFramework\backups"
.\gradlew.bat backend:backupDatabase
```

The command writes `ktor-framework-h2-backup-YYYYMMDD-HHMMSS.zip`. Stop the app before running an offline export unless you intentionally run H2 with a mode that supports concurrent access. In Home Assistant, exports should stay under `/data/backups` so they are included in HA backups.

Restore from a backup by stopping the backend, keeping a copy of the current `.mv.db` file, extracting the backup zip to the database directory, and starting the backend again. Verify `/health/ready` before allowing traffic back to the instance.

### Health Checks And Smoke Tests

The backend exposes unauthenticated operational checks:

```text
GET /health/live   Process liveness only.
GET /health/ready  Readiness, including database connectivity.
```

For local smoke testing, start the backend and run:

```bash
curl -fsS http://localhost:8080/health/live
curl -fsS http://localhost:8080/health/ready
test "$(curl -sS -o /dev/null -w '%{http_code}' http://localhost:8080/api/verify)" = "401"
curl -fsS http://localhost:8080/ >/dev/null
```

Every response includes `X-Request-ID`. Provide this header from a proxy if one already exists; otherwise the backend generates it and logs it as `request_id`.

### Operations Runbook

Before upgrading, create a database backup, keep the currently running `ktor.jar` and `app/` assets available for rollback, deploy the new jar/assets, restart the backend, and verify `/health/ready` plus `/api/verify` returning `401` without a token.

For rollback, stop the backend, restore the previous jar/assets, restore the matching database backup if the failed release changed persisted data or schema, restart, then repeat the smoke tests. Keep at least one known-good release artifact and one recent database backup outside the deploy directory.

Production checklist:

- Use a strong `JWT_SECRET_KEY` and do not use development defaults.
- Set `DATABASE_PATH` to an absolute persistent path outside the application directory.
- Set or confirm `DATABASE_BACKUP_PATH`, and test both backup and restore.
- Point container or process health checks at `/health/ready`, not `/`.
- Configure `CORS_ALLOWED_ORIGINS` only when the frontend is not same-origin.
- Capture logs with the `request_id` field so failed requests can be traced.
- Keep rollback artifacts and backups available before each upgrade.

### Local Development

Start the backend on Unix-like shells:

```bash
JWT_SECRET_KEY=dev-secret ./gradlew backend:run
```

On PowerShell:

```powershell
$env:JWT_SECRET_KEY = "dev-secret"
.\gradlew.bat backend:run
```

Local development can use `config.properties` with `APP_ENV=development` for local CORS and Swagger behavior. The backend looks for `config.properties` in the current working directory and one directory above it, so it works from both the repository root and `backend/`.

Start the standard Angular frontend:

```bash
cd webapp
npm run start
```

The standard frontend runs at `http://localhost:4200` and talks to the backend at `http://localhost:8080/api`.

### Build Commands

Build the standard frontend:

```bash
cd webapp
npm run build
```

Build both Angular apps:

```bash
cd webapp
npm run build:all
```

Build the backend:

```bash
./gradlew backend:build
```

Build the backend fat jar:

```bash
./gradlew backend:buildFatJar
```

Run backend tests:

```bash
./gradlew backend:test
```

Run Angular tests:

```bash
cd webapp
npm test
```

### Deployment Notes

The root Gradle `deploy` task builds the standard frontend and the backend fat jar, then copies the jar to `deploy/ktor.jar`:

```bash
./gradlew deploy
```

The Ktor app serves static files from `app/browser`, so production packaging must place the Angular browser output next to the jar in that structure.

For the standard web app, build output is generated under `deploy/app`. For the HA app, build output is generated under `deploy/ha-app`.

## Release Procedures

### Standard Web/API Release

The `CI` workflow runs for pull requests, manual dispatches, and pushes to `master`. It is the only workflow that builds deployable artifacts:

- `web-app-build`: standard Angular app output under `deploy/app`
- `ktor-jar`: backend fat jar from `backend/build/libs/fat.jar`

After CI succeeds on `master`, `Deploy Web Server` downloads those artifacts from the completed CI run and deploys them. The deploy workflow expects these repository variables:

```text
WEB_DEPLOY_HOST
WEB_DEPLOY_PORT
WEB_DEPLOY_DIR
WEB_BACKEND_HOST
WEB_BACKEND_PORT
```

It also expects these repository secrets:

```text
WEB_DEPLOY_USERNAME
WEB_DEPLOY_PASSWORD
JWT_SECRET_KEY
```

The deployment restarts the backend from the downloaded jar and checks:

- `/health/ready` responds successfully.
- `/api/verify` without a token returns `401`.

### Home Assistant Add-on Release

The HA add-on package is generated from successful `CI` artifacts on `master`. CI builds and validates:

- `ha-app-build`
- `ktor-jar`
- `ha-addon-package`

After CI succeeds on a human-authored `master` update, `Deploy Home Assistant Add-on` downloads the CI artifacts, copies the generated HA app and backend jar into `addons/ktor_app`, bumps `addons/ktor_app/config.yaml`, and opens or updates the `version-bump` pull request.

The workflow uses the `VERSION_BUMP_TOKEN` repository secret for the generated PR. Use a fine-scoped token that can push the `version-bump` branch, open pull requests, and enable auto-merge; using a dedicated token instead of the default workflow token lets the PR receive the normal required checks.

The PR has auto-merge enabled, but branch protection and required checks decide when it lands. The squash merge subject includes `[skip addon-bump]`, and the HA packaging workflow ignores CI runs for that marker so the generated version bump cannot trigger another generated version bump.

### HACS Card Release

The HACS Lovelace card is the tracked `dist/KtorFramework.js` file served by HACS as:

```yaml
url: /hacsfiles/KtorFramework/KtorFramework.js
type: module
```

Release card changes separately from the HA add-on package:

1. Update `dist/KtorFramework.js` in a normal pull request.
2. Let the standard required checks run.
3. Merge after review/checks pass.
4. In Home Assistant, reload the HACS resource or clear frontend cache if the old card bundle is still served.
