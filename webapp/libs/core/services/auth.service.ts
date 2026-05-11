import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Router} from '@angular/router';
import {firstValueFrom} from 'rxjs';
import {ErrorService} from './error.service';
import {environment} from '../environments/environment';
import {resolveApiUrl} from '../utils/url.util';
import {User} from '../models/user.model';

export interface VerifySessionResponse {
  valid: boolean;
  user: User;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  apiUrl = resolveApiUrl(environment.apiUrl);
  isLoggedIn: boolean = false;
  currentUser: User | null = null;
  private verifyPromise: Promise<boolean> | null = null;

  constructor(private http: HttpClient, private router: Router, private errorService: ErrorService) {}


  async verifyToken(): Promise<boolean> {
    if (!this.verifyPromise) {
      this.verifyPromise = this.verifySession().finally(() => {
        this.verifyPromise = null;
      });
    }
    return this.verifyPromise;
  }

  async loginHomeAssistantUser(): Promise<boolean> {
    if (!environment.haAutoLogin) {
      this.isLoggedIn = false;
      return false;
    }

    try {
      const response = await firstValueFrom(this.http.get<{ token: string }>(`${this.apiUrl}/ha/session`));
      localStorage.setItem('token', response.token);
      return this.verifyStoredToken();
    } catch (error) {
      this.clearSession();
      this.errorService.setError('Home Assistant session could not be started.');
      return false;
    }
  }

  logout(redirectTo: string[] = ['landing']) {
    this.clearSession();
    this.router.navigate(redirectTo);
  }

  login(username: string, password: string) {

    const body = new URLSearchParams();
    body.set('username', username);
    body.set('password', password);

    const headers = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');

    this.http.post(`${this.apiUrl}/login`, body.toString(), { headers }).subscribe(
      (response: any) => {
        localStorage.setItem('token', response.token);
        this.isLoggedIn = true
        this.currentUser = null;
        this.errorService.clearError();
        this.router.navigate(['dashboard']);
      },
      (error) => {
        this.clearSession();
        this.errorService.setError(error.error?.message || 'Login failed. Please check your credentials.');
      }
    );
  }

  signup(username: string, password: string) {

    const body = new URLSearchParams();
    body.set('username', username);
    body.set('password', password);

    const headers = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');

    this.http.post(`${this.apiUrl}/user`, body.toString(), { headers }).subscribe(
      () => {
        this.isLoggedIn = false
        this.errorService.clearError();
        this.login(username, password);
      },
      (error) => {
        this.clearSession();
        this.errorService.setError(error.error?.message || 'Registration failed. Please try again.');
      }
    );
  }

  private async verifySession(): Promise<boolean> {
    const hasToken = !!localStorage.getItem('token');
    if (hasToken && await this.verifyStoredToken()) {
      return true;
    }

    if (environment.haAutoLogin) {
      return this.loginHomeAssistantUser();
    }

    this.clearSession();
    return false;
  }

  private async verifyStoredToken(): Promise<boolean> {
    const token = localStorage.getItem('token');
    if (!token) {
      this.clearSession();
      return false;
    }

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    try {
      const response = await firstValueFrom(
        this.http.get<VerifySessionResponse>(`${this.apiUrl}/verify`, { headers })
      );
      if (response.valid) {
        this.isLoggedIn = true;
        this.currentUser = response.user;
        this.errorService.clearError();
      } else {
        this.clearSession();
      }
      return response.valid;
    } catch (error) {
      this.clearSession();
      return false;
    }
  }

  private clearSession(): void {
    localStorage.removeItem('token');
    this.isLoggedIn = false;
    this.currentUser = null;
  }
}

