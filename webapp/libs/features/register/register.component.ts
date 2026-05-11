import {Component} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {AuthService} from '@core/services/auth.service';
import {NgIf} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';
import {ErrorService} from '@core/services/error.service';

@Component({
  selector: 'app-register',
  imports: [
    FormsModule,
    NgIf,
    MatIconModule,
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
  standalone: true
})
export class RegisterComponent {

  username = '';
  password = '';

  constructor(protected authService: AuthService, public errorService: ErrorService) {}

  public onSubmit(form: any): void {
    const { username, password } = form.value;
    this.authService.signup(username, password);
    form.reset();
  }
}
