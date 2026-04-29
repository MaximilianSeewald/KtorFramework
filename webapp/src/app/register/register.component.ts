import {Component} from '@angular/core';
import {MatCard, MatCardContent} from '@angular/material/card';
import {MatError, MatFormField, MatLabel} from '@angular/material/form-field';
import {FormsModule} from '@angular/forms';
import {MatInput} from '@angular/material/input';
import {MatButton} from '@angular/material/button';
import {AuthService} from '../auth.service';
import {NgIf} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatIconModule} from '@angular/material/icon';
import {ErrorService} from '../error.service';

@Component({
  selector: 'app-register',
  imports: [
    MatCard,
    MatCardContent,
    MatError,
    FormsModule,
    MatFormField,
    MatInput,
    MatButton,
    NgIf,
    RouterLink,
    MatIconModule,
    MatLabel
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
  };
}
