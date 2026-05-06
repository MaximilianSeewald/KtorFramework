import { Routes } from '@angular/router';
import { AuthGuard } from '@core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'shoppingList', pathMatch: 'full' },

  { path: 'shoppingList', loadComponent: () => import('@features/shopping-list/shopping-list.component').then(m => m.ShoppingListComponent), canActivate: [AuthGuard] },
  { path: 'recipeList', loadComponent: () => import('@features/recipe/recipe.component').then(m => m.RecipeComponent), canActivate: [AuthGuard] },
  { path: 'dashboardSetup', loadComponent: () => import('./dashboard-setup.component').then(m => m.DashboardSetupComponent), canActivate: [AuthGuard] },

  { path: 'dashboard', redirectTo: 'shoppingList' },
  { path: 'recipe', redirectTo: 'recipeList' },
  { path: '**', redirectTo: 'shoppingList' }
];

