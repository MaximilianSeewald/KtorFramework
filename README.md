# KtorFramework

Ktor backend with Angular frontends and a Home Assistant add-on for recipes and shopping lists.

## Ktor Shopping List Lovelace Card

The Lovelace card is distributed through HACS as a Dashboard resource from this repository.

HACS installs the card from:

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

`addon_slug` is stable. The card uses it to resolve the current Home Assistant ingress URL at runtime.
