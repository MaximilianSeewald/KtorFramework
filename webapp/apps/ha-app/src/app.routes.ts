import { Routes } from '@angular/router';
import { AuthGuard, NoAuthGuard } from '@core/guards/auth.guard';

// Home Assistant specific routes - simpler, focused on main features
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('@features/login/login.component').then(m => m.LoginComponent), canActivate: [NoAuthGuard] },
  { path: 'register', loadComponent: () => import('@features/register/register.component').then(m => m.RegisterComponent), canActivate: [NoAuthGuard] },

  { path: 'dashboard', loadComponent: () => import('@features/shopping-list/shopping-list.component').then(m => m.ShoppingListComponent), canActivate: [AuthGuard] },
  { path: 'recipe', loadComponent: () => import('@features/recipe/recipe.component').then(m => m.RecipeComponent), canActivate: [AuthGuard] },
  { path: 'landing', loadComponent: () => import('@features/landing/landing.component').then(m => m.LandingComponent) },

  { path: '**', redirectTo: 'dashboard' }
];

