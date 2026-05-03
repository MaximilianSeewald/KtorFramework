import { Component } from '@angular/core';
import {RouterLink} from '@angular/router';
import {MatIconModule} from '@angular/material/icon';

@Component({
  selector: 'app-landing',
  imports: [
    RouterLink,
    MatIconModule
  ],
  templateUrl: './landing.component.html',
  standalone: true,
  styleUrl: './landing.component.css'
})
export class LandingComponent {

}
