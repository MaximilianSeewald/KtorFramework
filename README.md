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
- JWT secret: generated once at startup and stored at `/data/jwt_secret`

When `HA_MODE` is enabled, the backend creates and uses a default Home Assistant user/group:

- User: `ha-user`
- Group: `ha-instance`
- Registration through the normal `/api/user` endpoint is blocked.
- `/api/ha/session` issues the auto-login token used by the HA frontend.

The backend stores H2 data at `/data/db` inside the add-on container. Outside Home Assistant it falls back to `./data/db`, unless `ktor.database.path` is set.

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
- Static Angular asset hosting from `app/browser`
- H2 persistence through Exposed and HikariCP

Important runtime settings:

```text
JWT_SECRET_KEY         Required. Secret used for JWT signing.
JWT_TOKEN_TTL_MS      Optional. Token lifetime in milliseconds.
KTOR_HOST             Optional. Defaults to 0.0.0.0.
KTOR_PORT             Optional. Defaults to 8080.
HA_MODE               Optional. Set true only for Home Assistant mode.
```

Optional `config.properties` file in the backend working directory. Start from `config.example.properties`:

```properties
APP_ENV=production
CORS_ALLOWED_ORIGINS=https://example.com,https://www.example.com
```

Production builds of both Angular apps use relative `api` URLs, so the normal same-origin deployment does not need CORS. If `CORS_ALLOWED_ORIGINS` is omitted, production does not install CORS. If `APP_ENV=development` and no explicit CORS origins are configured, the backend allows the local Angular dev origins on ports `4200` and `4201`.

Swagger UI is available only when `APP_ENV=development`.

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
