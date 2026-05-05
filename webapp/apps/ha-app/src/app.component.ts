import {Component, OnInit} from '@angular/core';
import {Router, RouterLink, RouterOutlet, NavigationEnd, NavigationStart} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {AuthService} from '@core/services/auth.service';
import {NgIf} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';
import {ErrorService} from '@core/services/error.service';
import {filter} from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FormsModule, RouterLink, NgIf, MatIconModule],
  templateUrl: './app.component.html',
  standalone: true,
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  menuOpen = false;
  isWidgetRoute = false;

  constructor(public authService: AuthService, private router: Router, private errorService: ErrorService) {}

  ngOnInit(): void {
    this.applyWidgetUrl();
    this.authService.verifyToken().then((isLoggedIn) => {
      if (isLoggedIn && this.router.url.includes('/login')) {
        this.router.navigate(['shoppingList']);
      }
    })
    this.updateWidgetMode(this.router.url);
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart)
    ).subscribe(() => this.errorService.clearError());
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event) => this.updateWidgetMode(event.urlAfterRedirects));
  }

  toggleMenu() {
    this.menuOpen = !this.menuOpen;
  }

  private updateWidgetMode(url: string) {
    const fullUrl = `${window.location.pathname}${window.location.search}${window.location.hash}${url}`.toLowerCase();
    this.isWidgetRoute = fullUrl.includes('widget');
    if (this.isWidgetRoute) {
      this.menuOpen = false;
    }
  }

  private applyWidgetUrl() {
    const widget = new URLSearchParams(window.location.search).get('widget')?.toLowerCase();

    if (widget === 'shoppinglist') {
      this.router.navigateByUrl('/shoppingListWidget');
    }

    if (widget === 'recipelist') {
      this.router.navigateByUrl('/recipeListWidget');
    }
  }
}

