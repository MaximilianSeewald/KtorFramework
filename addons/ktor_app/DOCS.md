# Ktor App

This add-on provides a Home Assistant focused shopping list and recipe list.

## Native Lovelace card

The add-on ships a native Lovelace custom card for the shopping list.

1. Install or update the add-on.
2. Reload the Home Assistant frontend.
3. Add `Ktor Shopping List` from the dashboard card picker.

After the resource is loaded, the card is registered in Home Assistant's card picker as `Ktor Shopping List`.
The add-on automatically publishes and registers the Lovelace resource during startup. If the card does not appear, open the add-on from the Home Assistant sidebar, select `Dashboard Setup`, and select `Install or update resource`.

The installer copies the card module to Home Assistant's `/config/www/ktor-lovelace-cards.js` and registers it as:

```yaml
url: /local/ktor-lovelace-cards.js
type: module
```

Manual card YAML:

```yaml
type: custom:ktor-shopping-list-card
title: Shopping List
max_items: 12
show_completed: true
```
