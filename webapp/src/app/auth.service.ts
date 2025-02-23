import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {environment} from '../environments/environment';
import {catchError, map} from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}


  verifyToken(): Observable<boolean> {
    const token = localStorage.getItem('token');

    if(!token) {
      return of(false)
    }

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    return this.http.get<{ valid: boolean }>(`${this.apiUrl}/verify`, { headers }).pipe(
      map(response => response.valid
      ),
      catchError(() => {
        return of(false)
      })
    );
  }

  logout() {
    localStorage.removeItem('token');
  }
}
