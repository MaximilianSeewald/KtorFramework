import {Component} from '@angular/core';
import {MatCard, MatCardContent} from '@angular/material/card';
import {MatError, MatFormField} from '@angular/material/form-field';
import {FormsModule} from '@angular/forms';
import {MatInput} from '@angular/material/input';
import {MatButton} from '@angular/material/button';
import {AuthService} from '../auth.service';

@Component({
  selector: 'app-login',
  imports: [
    MatCard,
    MatCardContent,
    MatError,
    FormsModule,
    MatFormField,
    MatInput,
    MatButton
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  standalone: true
})
export class LoginComponent {

  username = '';
  password = '';

  constructor(private authService: AuthService) {}

  public onSubmit(form: any): void {
    const { username, password } = form.value;
    this.authService.login(username,password)

  }

  public onCreateAccount(form: any): void {
    if (form.valid) {
      const { username, password } = form.value;
      this.authService.signup(username, password);
      form.reset();
    }
  }

}
