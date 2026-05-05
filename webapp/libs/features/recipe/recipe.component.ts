import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgForOf, NgIf } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RecipeService } from './recipe.service';
import { ErrorService } from '@core/services/error.service';
import { Recipe, RecipeExtended, RecipeItem } from '@core/models/recipe.model';

@Component({
  selector: 'app-recipe',
  imports: [FormsModule, NgForOf, NgIf, MatIconModule],
  templateUrl: './recipe.component.html',
  standalone: true,
  styleUrl: './recipe.component.css'
})
export class RecipeComponent implements OnInit, OnDestroy {
  recipes: RecipeExtended[] = [];
  newRecipeName = '';
  newItemName = '';
  newItemValue = '';
  selectedRecipeIndex: number | null = null;

  constructor(
    private recipeService: RecipeService,
    public errorService: ErrorService
  ) {}

  ngOnInit() {
    this.recipeService.initWebSocket();
    this.recipeService.recipes$.subscribe((recipes) => {
      this.recipes = recipes;
    });
  }

  ngOnDestroy(): void {
    this.recipeService.closeWebSocket();
  }

  addRecipe() {
    if (!this.newRecipeName.trim()) {
      this.errorService.setError('Please enter a recipe name.');
      return;
    }
    this.recipeService.addRecipe(this.newRecipeName).subscribe(
      () => {
        this.errorService.clearError();
        this.newRecipeName = '';
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to add recipe.');
      }
    );
  }

  deleteRecipe(id: string) {
    this.recipeService.deleteRecipe(id).subscribe(
      () => {
        this.errorService.clearError();
        this.selectedRecipeIndex = null;
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to delete recipe.');
      }
    );
  }

  selectRecipe(index: number) {
    this.selectedRecipeIndex = this.selectedRecipeIndex === index ? null : index;
  }

  toggleEditRecipe(recipe: RecipeExtended) {
    if (recipe.isEditing) {
      if (!recipe.name.trim()) {
        this.errorService.setError('Recipe name cannot be empty.');
        return;
      }
      this.updateRecipe(recipe);
    }
    recipe.isEditing = !recipe.isEditing;
  }

  updateRecipe(recipe: RecipeExtended) {
    const cleanRecipe: Recipe = {
      id: recipe.id,
      name: recipe.name,
      items: recipe.items
    };

    this.recipeService.updateRecipe(cleanRecipe).subscribe(
      () => {
        this.errorService.clearError();
      },
      (error) => {
        console.error('Update recipe error:', error);
        this.errorService.setError(error.error?.message || 'Failed to update recipe.');
      }
    );
  }

  addItemToRecipe() {
    if (this.selectedRecipeIndex === null) {
      this.errorService.setError('Please select a recipe first.');
      return;
    }
    if (!this.newItemName.trim() || !this.newItemValue.trim()) {
      this.errorService.setError('Please enter both item name and value.');
      return;
    }

    const recipe = this.recipes[this.selectedRecipeIndex];
    const newItem: RecipeItem = {
      name: this.newItemName,
      value: this.newItemValue
    };
    recipe.items.push(newItem);
    this.updateRecipe(recipe);
    this.newItemName = '';
    this.newItemValue = '';
  }

  removeItemFromRecipe(index: number) {
    if (this.selectedRecipeIndex === null) return;
    const recipe = this.recipes[this.selectedRecipeIndex];
    recipe.items.splice(index, 1);
    this.updateRecipe(recipe);
  }

  editItem(itemIndex: number) {
    if (this.selectedRecipeIndex === null) return;
    const recipe = this.recipes[this.selectedRecipeIndex];
    recipe.editingItemIndex = recipe.editingItemIndex === itemIndex ? null : itemIndex;
    if (recipe.editingItemIndex === null) {
      this.updateRecipe(recipe);
    }
  }
}

