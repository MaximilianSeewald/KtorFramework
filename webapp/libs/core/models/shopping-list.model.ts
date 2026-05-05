export interface ShoppingListItem {
  id: string;
  name: string;
  amount: string;
  retrieved: boolean;
}

export interface ShoppingListItemExtended extends ShoppingListItem{
  isEditing: boolean;
}

