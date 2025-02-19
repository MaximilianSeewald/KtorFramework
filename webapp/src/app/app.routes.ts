import { Routes } from '@angular/router';
import {LoginComponent} from './login/login.component';
import {AuthGuard, NoAuthGuard} from './auth.guard';
import {DashboardComponent} from './dashboard/dashboard.component';
import {LandingComponent} from './landing/landing.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [NoAuthGuard]},
  { path: 'dashboard', component: DashboardComponent, canActivate: [AuthGuard] },
  { path: 'landing', component: LandingComponent },
  { path: '**', redirectTo: '/landing'}
];
