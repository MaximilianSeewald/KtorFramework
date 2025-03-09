import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../environments/environment';
import {Router} from '@angular/router';
import {firstValueFrom} from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  apiUrl = environment.apiUrl;
  isLoggedIn: boolean = false;
  isRegistered: boolean = false;

  constructor(private http: HttpClient, private router: Router) {}


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
        this.router.navigate(['dashboard']);
      },
      () => {
        this.isLoggedIn = false
      }
    );
  }

  signup(username: string, password: string) {

    const body = new URLSearchParams();
    body.set('username', username);
    body.set('password', password);

    const headers = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');

    this.http.post(`${this.apiUrl}/user`, body.toString(), { headers }).subscribe(
      (response: any) => {
        this.isLoggedIn = false
        this.isRegistered = true
      },
      () => {
        this.isLoggedIn = false
        this.isRegistered = false
      }
    );
  }

  resetRegistrationStatus() {
    this.isRegistered = false;
  }
}
