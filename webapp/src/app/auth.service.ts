import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {BehaviorSubject, Observable, of, tap} from 'rxjs';
import {environment} from '../environments/environment';
import {catchError, map} from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  apiUrl = environment.apiUrl;
  private loggedInSubject = new BehaviorSubject<boolean>(false);
  isLoggedIn$ = this.loggedInSubject.asObservable();

  constructor(private http: HttpClient) {}


  verifyToken(): Observable<boolean> {
    const token = localStorage.getItem('token');

    if(!token) {
      this.loggedInSubject.next(false)
      return of(false)
    }

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    return this.http.get<{ valid: boolean }>(`${this.apiUrl}/verify`, { headers }).pipe(
      map(response => response.valid),
      tap(valid => this.loggedInSubject.next(valid)),
      catchError(() => {
        this.loggedInSubject.next(false);
        return of(false)
      })
    );
  }

  logout() {
    localStorage.removeItem('token');
    this.loggedInSubject.next(false);
  }
}
