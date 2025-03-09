import {Component} from '@angular/core';
import {MatCard, MatCardContent} from '@angular/material/card';
import {MatError, MatFormField} from '@angular/material/form-field';
import {FormsModule} from '@angular/forms';
import {MatInput} from '@angular/material/input';
import {MatButton} from '@angular/material/button';
import {AuthService} from '../auth.service';
import {NgIf} from '@angular/common';
import {RouterLink} from '@angular/router';

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
    RouterLink
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
  standalone: true
})
export class RegisterComponent {

  username = '';
  password = '';

  constructor(protected authService: AuthService) {}

  public onSubmit(form: any): void {
    const { username, password } = form.value;
    this.authService.signup(username, password);
    form.reset();
  };
}
