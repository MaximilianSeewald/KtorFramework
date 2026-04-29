import {Component} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {AuthService} from '../auth.service';
import {MatIconModule} from '@angular/material/icon';
import {NgIf} from '@angular/common';
import {ErrorService} from '../error.service';

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    MatIconModule,
    NgIf
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  standalone: true
})
export class LoginComponent {

  username = '';
  password = '';

  constructor(private authService: AuthService, public errorService: ErrorService) {}

  public onSubmit(form: any): void {
    const { username, password } = form.value;
    this.authService.login(username, password)
  }

}
