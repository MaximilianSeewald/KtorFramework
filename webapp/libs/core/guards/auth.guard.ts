import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard implements CanActivate {

  constructor(private router: Router, private authService: AuthService) {}

  async canActivate(): Promise<boolean> {
    return this.authService.verifyToken().then((value) => {
      if(!value) {
        this.router.navigateByUrl('/login')
      }
      return value
    });
  }
}

@Injectable({
  providedIn: 'root',
})
export class NoAuthGuard implements CanActivate {

  constructor(private router: Router, private authService: AuthService) {}

  async canActivate(): Promise<boolean> {
    return this.authService.verifyToken().then((value) => {
      if(value) {
        this.router.navigateByUrl('/dashboard')
      }
      return !value
    });
  }
}

