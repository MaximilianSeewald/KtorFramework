import { Component } from '@angular/core';
import {MatCard, MatCardHeader} from '@angular/material/card';
import {MatIcon} from '@angular/material/icon';

@Component({
  selector: 'app-dashboard',
  imports: [
    MatCard,
    MatCardHeader,
    MatIcon
  ],
  templateUrl: './dashboard.component.html',
  standalone: true,
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {
}
