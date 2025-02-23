import {Component, OnInit} from '@angular/core';
import {Router, RouterLink, RouterOutlet} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {AuthService} from './auth.service';
import {Observable} from 'rxjs';
import {AsyncPipe, NgIf} from '@angular/common';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FormsModule, RouterLink, NgIf, AsyncPipe],
  templateUrl: './app.component.html',
  standalone: true,
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  isLoggedIn$!: Observable<boolean>;

  constructor(public authService: AuthService, private router: Router) {}

  ngOnInit() {
    this.isLoggedIn$ = this.authService.verifyToken();
  }


  logout() {
    this.authService.logout();
    this.router.navigate(['landing']);
  }

}

