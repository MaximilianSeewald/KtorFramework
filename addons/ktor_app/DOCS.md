# Ktor App

This add-on provides a Home Assistant focused shopping list and recipe list.

## Dashboard widgets

The easiest way to add widgets is from inside the add-on:

1. Open the add-on from the Home Assistant sidebar.
2. Open the menu and select `Dashboard Setup`.
3. Copy the card YAML for `Shopping List` or `Recipes`.
4. Edit your Home Assistant dashboard, add a manual card, and paste the YAML.

The copied YAML already includes your current ingress URL, so you do not need to find the ingress id manually.

## Manual examples

Use these only if you want to create the cards by hand.

Shopping List:

```yaml
type: iframe
title: Shopping List
url: /api/hassio_ingress/YOUR_INGRESS_ID/?widget=shoppingList
aspect_ratio: 125%
hide_background: true
```

Recipes:

```yaml
type: iframe
title: Recipes
url: /api/hassio_ingress/YOUR_INGRESS_ID/?widget=recipeList
aspect_ratio: 125%
hide_background: true
```

Replace `/api/hassio_ingress/YOUR_INGRESS_ID/` with the actual ingress URL for your add-on instance.

Use the `/api/hassio_ingress/...` URL, not the full sidebar or panel URL. The full panel URL embeds Home Assistant's own navigation around the add-on.
