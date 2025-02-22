export interface ShoppingListItem {
  id: string;
  name: string;
}

export interface ShoppingListItemExtended extends ShoppingListItem{
  isEditing: boolean;
}

