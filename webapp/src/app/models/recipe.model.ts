export interface RecipeItem {
  name: string;
  value: string;
}

export interface Recipe {
  id: string;
  name: string;
  items: RecipeItem[];
}

export interface RecipeExtended extends Recipe {
  isEditing: boolean;
  editingItemIndex: number | null;
}

