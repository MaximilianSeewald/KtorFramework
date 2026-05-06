# Ktor App

This add-on provides a Home Assistant focused shopping list and recipe list.

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

Manual card YAML:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
addon_slug: ktor_app
show_completed: true
```

`addon_slug` is the stable Home Assistant add-on slug from `config.yaml`. The card uses it to resolve the current dynamic ingress URL, such as `/api/hassio_ingress/<generated-token>/`, at runtime. Do not hardcode that generated ingress URL for normal usage.

For troubleshooting or custom deployments, you can bypass add-on lookup with an explicit backend URL:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
backend_url: /api/hassio_ingress/CURRENT_GENERATED_INGRESS_PATH/
show_completed: true
```
