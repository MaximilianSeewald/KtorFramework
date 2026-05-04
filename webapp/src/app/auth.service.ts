import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Router} from '@angular/router';
import {firstValueFrom} from 'rxjs';
import {ErrorService} from './error.service';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  apiUrl = window.location.hostname === 'localhost' ? 'http://localhost:8080' : window.location.origin;
  isLoggedIn: boolean = false;
  isRegistered: boolean = false;

  constructor(private http: HttpClient, private router: Router, private errorService: ErrorService) {}


  async verifyToken(): Promise<boolean> {
      const token = localStorage.getItem('token');

      if(!token) {
        this.isLoggedIn = false
        return false
      }
      const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

      try {
        const response = await firstValueFrom(this.http.get<{ valid: boolean }>(`${this.apiUrl}/verify`, { headers }))
        this.isLoggedIn = response.valid
        return response.valid
      } catch (error) {
        this.isLoggedIn = false
        return false
      }
  }

  logout() {
    localStorage.removeItem('token');
    this.isLoggedIn = false
    this.router.navigate(['landing']);
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
        this.errorService.clearError();
        this.router.navigate(['dashboard']);
      },
      (error) => {
        this.isLoggedIn = false
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
        this.isRegistered = true
        this.errorService.clearError();
        this.login(username, password);
      },
      (error) => {
        this.isLoggedIn = false
        this.isRegistered = false
        this.errorService.setError(error.error?.message || 'Registration failed. Please try again.');
      }
    );
  }

  resetRegistrationStatus() {
    this.isRegistered = false;
  }
}
