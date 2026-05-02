import { Routes } from '@angular/router';
import {LoginComponent} from './login/login.component';
import {AuthGuard, NoAuthGuard} from './auth.guard';
import {ShoppingListComponent} from './shopping-list/shopping-list.component';
import {LandingComponent} from './landing/landing.component';
import {CalculatorComponent} from './calculator/calculator.component';
import {UserinfoComponent} from './userinfo/userinfo.component';
import {RegisterComponent} from './register/register.component';
import {ChangePasswordComponent} from './changePassword/changePassword.component';
import {RecipeComponent} from './recipe/recipe.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [NoAuthGuard]},
  { path: 'register', component: RegisterComponent, canActivate: [NoAuthGuard]},
  { path: 'change-password', component: ChangePasswordComponent, canActivate: [AuthGuard]},
  { path: 'dashboard', component: ShoppingListComponent, canActivate: [AuthGuard] },
  { path: 'recipe', component: RecipeComponent, canActivate: [AuthGuard] },
  { path: 'landing', component: LandingComponent },
  { path: 'user', component: UserinfoComponent, canActivate: [AuthGuard] },
  { path: 'calculator', component: CalculatorComponent},
  { path: '**', redirectTo: 'landing'}
];
