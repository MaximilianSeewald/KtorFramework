import { Routes } from '@angular/router';
import {LoginComponent} from './login/login.component';
import {AuthGuard, NoAuthGuard} from './auth.guard';
import {DashboardComponent} from './dashboard/dashboard.component';
import {LandingComponent} from './landing/landing.component';
import {CalculatorComponent} from './calculator/calculator.component';
import {UserinfoComponent} from './userinfo/userinfo.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [NoAuthGuard]},
  { path: 'dashboard', component: DashboardComponent, canActivate: [AuthGuard] },
  { path: 'landing', component: LandingComponent },
  { path: 'user', component: UserinfoComponent, canActivate: [AuthGuard] },
  { path: 'calculator', component: CalculatorComponent},
  { path: '**', redirectTo: 'landing'}
];
