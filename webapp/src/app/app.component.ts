import {Component} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {environment} from '../environments/environment';
import {HttpClient} from '@angular/common/http';

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

  public getShoppingItems() {
    this.http.get(`${this.apiUrl}/shoppingList`).subscribe(
      (response) => {
        let s = JSON.stringify(response, null, 2);
        console.log(s)
      },
      () => {

      }
    );
  }
}

