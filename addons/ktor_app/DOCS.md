# Ktor App

This add-on provides a Home Assistant focused shopping list and recipe list.

## Native Lovelace card

The add-on ships a native Lovelace custom card for the shopping list.

1. Install or update the add-on.
2. Restart Home Assistant once after the add-on has installed or updated the card module.
3. Add `Ktor Shopping List` from the dashboard card picker.

After the resource is loaded, the card is registered in Home Assistant's card picker as `Ktor Shopping List`.
The add-on automatically publishes and registers the Lovelace resource during startup when Home Assistant provides the ingress URL, and also whenever the add-on UI is opened. If the card does not appear, open the add-on from the Home Assistant sidebar, select `Dashboard Setup`, and select `Install or update resource`.
If the `www` folder was created for the first time, restart Home Assistant once so `/local` resources are served.

The installer copies the card module to Home Assistant's `www` folder and registers a versioned local resource plus a stable fallback resource for the Home Assistant mobile app:

```yaml
url: /local/ktor-lovelace-cards-1.1.7.js
type: module
---
url: /local/ktor-lovelace-cards.js
type: module
```

To make the card picker discover the card reliably, the installer also keeps this Home Assistant frontend module entry in `configuration.yaml`:

```yaml
frontend:
  extra_module_url:
    - /local/ktor-lovelace-cards-1.1.7.js
    - /local/ktor-lovelace-cards.js
```

Manual card YAML:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
max_items: 12
show_completed: true
```
