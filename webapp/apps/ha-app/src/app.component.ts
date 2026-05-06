import {Component, OnInit} from '@angular/core';
import {Router, RouterLink, RouterOutlet, NavigationStart} from '@angular/router';
import {AuthService} from '@core/services/auth.service';
import {NgIf} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';
import {ErrorService} from '@core/services/error.service';
import {filter} from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, NgIf, MatIconModule],
  templateUrl: './app.component.html',
  standalone: true,
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  menuOpen = false;

  constructor(public authService: AuthService, private router: Router, private errorService: ErrorService) {}

  ngOnInit(): void {
    this.authService.verifyToken().then((isLoggedIn) => {
      if (isLoggedIn && this.router.url.includes('/login')) {
        this.router.navigate(['shoppingList']);
      }
    })
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart)
    ).subscribe(() => this.errorService.clearError());
  }

  toggleMenu() {
    this.menuOpen = !this.menuOpen;
  }
}

