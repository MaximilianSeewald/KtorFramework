import { Component } from '@angular/core';
import {MatCard, MatCardContent} from '@angular/material/card';
import {MatError, MatFormField} from '@angular/material/form-field';
import {FormsModule} from '@angular/forms';
import {MatInput} from '@angular/material/input';
import {MatButton} from '@angular/material/button';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../../environments/environment';

@Component({
  selector: 'app-login',
  imports: [
    MatCard,
    MatCardContent,
    MatError,
    FormsModule,
    MatFormField,
    MatInput,
    MatButton
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  standalone: true
})
export class LoginComponent {
  username = '';
  password = '';
  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  public onSubmit(form: any): void {
    const { username, password } = form.value;
    const body = new URLSearchParams();
    body.set('username', username);
    body.set('password', password);

    const headers = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');

    this.http.post(`${this.apiUrl}/login`, body.toString(), { headers }).subscribe(
      (response: any) => {
        // If login is successful, redirect to dashboard or protected page
        localStorage.setItem('token', response.token);
      },
      (error) => {
        // If login fails, show an error message
        console.log('Invalid login credentials');
      }
    );

  }
}
