import {Component, OnInit} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {AuthService} from './auth.service';
import {NgIf} from '@angular/common';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FormsModule, RouterLink, NgIf],
  templateUrl: './app.component.html',
  standalone: true,
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {

  constructor(public authService: AuthService) {}

  ngOnInit(): void {
        this.authService.verifyToken().then()
    }


  logout() {
    this.authService.logout();
  }

}

