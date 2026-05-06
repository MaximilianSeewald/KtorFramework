# Ktor App

This add-on provides a Home Assistant focused shopping list and recipe list.

## Native Lovelace card

The add-on ships a native Lovelace custom card for the shopping list.

1. Open the add-on from the Home Assistant sidebar.
2. Open the menu and select `Dashboard Setup`.
3. Select `Install or update resource`.
4. Reload the Home Assistant frontend.
5. Add `Ktor Shopping List` from the dashboard card picker.

After the resource is loaded, the card is registered in Home Assistant's card picker as `Ktor Shopping List`.

If automatic resource installation fails, add the resource manually:

```yaml
url: /api/hassio_ingress/YOUR_INGRESS_ID/ktor-lovelace-cards.js
type: module
```

Manual card YAML:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
max_items: 12
show_completed: true
```

Replace `/api/hassio_ingress/YOUR_INGRESS_ID/` with the actual ingress URL for your add-on instance when adding the resource manually.
