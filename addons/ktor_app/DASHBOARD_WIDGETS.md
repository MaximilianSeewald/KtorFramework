# Home Assistant dashboard widgets

The add-on exposes compact dashboard routes for Home Assistant Webpage cards:

- `#/shoppingListWidget`
- `#/recipeListWidget`

To add them:

1. Open the add-on from the Home Assistant sidebar.
2. Copy the add-on URL up to, but not including, the current `#...` route.
3. Add a Webpage card to your dashboard and append one of the widget routes.

Example:

```yaml
type: iframe
title: Shopping List
url: /api/hassio_ingress/YOUR_INGRESS_ID/#/shoppingListWidget
aspect_ratio: 125%
hide_background: true
```

```yaml
type: iframe
title: Recipes
url: /api/hassio_ingress/YOUR_INGRESS_ID/#/recipeListWidget
aspect_ratio: 125%
hide_background: true
```

Use the actual ingress URL from your Home Assistant instance in place of `/api/hassio_ingress/YOUR_INGRESS_ID/`.
