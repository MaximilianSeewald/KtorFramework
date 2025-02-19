import {Component} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';
import {environment} from '../environments/environment';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FormsModule, RouterLink],
  templateUrl: './app.component.html',
  standalone: true,
  styleUrl: './app.component.css',
})

export class AppComponent {

  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  toShare() {
    this.http.get(`${this.apiUrl}/users`).subscribe((response: any) => {
        console.log(response)
      });
  }
}

