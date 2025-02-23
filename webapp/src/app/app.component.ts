import {Component} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {AuthService} from './auth.service';
import {map} from 'rxjs/operators';
import {Observable} from 'rxjs';
import {NgIf} from '@angular/common';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FormsModule, RouterLink, NgIf],
  templateUrl: './app.component.html',
  standalone: true,
  styleUrl: './app.component.css',
})

export class AppComponent {

  constructor(public authService: AuthService) {}

  isLoggedIn(): Observable<boolean> {
    return this.authService.verifyToken().pipe( map((isValid) => {
      return isValid;
    }))
  }
}

