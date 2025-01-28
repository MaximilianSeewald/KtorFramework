import {Component} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {MatIcon} from '@angular/material/icon';
import {MatIconButton} from '@angular/material/button';
import {MatToolbar} from '@angular/material/toolbar';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {environment} from '../enviroments/enviroment';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FormsModule, MatIcon, MatIconButton, MatToolbar, RouterLink],
  templateUrl: './app.component.html',
  standalone: true,
  styleUrl: './app.component.css'
})

export class AppComponent {

  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  toShare() {
    const token = localStorage.getItem('token');
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    this.http.get(`${this.apiUrl}/user`, { headers }).subscribe((response: any) => {
        console.log(response)
        localStorage.setItem('token', "invalid");
      },
      (error) => {
        console.log(error);
      });
  }
}

