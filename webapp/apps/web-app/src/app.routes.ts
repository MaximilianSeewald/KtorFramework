import { Routes } from '@angular/router';
import { AuthGuard, NoAuthGuard } from '@core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },

  { path: 'login', loadComponent: () => import('@features/login/login.component').then(m => m.LoginComponent), canActivate: [NoAuthGuard] },
  { path: 'register', loadComponent: () => import('@features/register/register.component').then(m => m.RegisterComponent), canActivate: [NoAuthGuard] },
  { path: 'change-password', loadComponent: () => import('@features/change-password/change-password.component').then(m => m.ChangePasswordComponent), canActivate: [AuthGuard] },

  { path: 'dashboard', loadComponent: () => import('@features/shopping-list/shopping-list.component').then(m => m.ShoppingListComponent), canActivate: [AuthGuard] },
  { path: 'recipe', loadComponent: () => import('@features/recipe/recipe.component').then(m => m.RecipeComponent), canActivate: [AuthGuard] },

  { path: 'user', loadComponent: () => import('@features/userinfo/userinfo.component').then(m => m.UserinfoComponent), canActivate: [AuthGuard] },

  { path: '**', redirectTo: 'dashboard' }
];


