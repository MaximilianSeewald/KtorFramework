import {Component, OnInit} from '@angular/core';
import {MatCard, MatCardContent} from '@angular/material/card';
import {MatError, MatFormField} from '@angular/material/form-field';
import {FormsModule} from '@angular/forms';
import {MatInput} from '@angular/material/input';
import {MatButton} from '@angular/material/button';
import {NgIf} from '@angular/common';
import {HttpHeaders} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {HttpClient} from '@angular/common/http';
import {AuthService} from '../auth.service';
import {User} from '../models/user.model';

@Component({
  selector: 'app-change-password',
  imports: [
    MatCard,
    MatCardContent,
    MatError,
    FormsModule,
    MatFormField,
    MatInput,
    MatButton,
    NgIf,
  ],
  templateUrl: './changePassword.component.html',
  styleUrl: './changePassword.component.css',
  standalone: true
})
export class ChangePasswordComponent implements OnInit{

  user: User | null = null
  apiUrl = environment.apiUrl;
  username: string = '';
  oldPassword: string = '';
  newPassword: string = '';
  wrongUsername: boolean = false;
  wrongPassword: boolean = false;

  constructor(private http: HttpClient, protected authService: AuthService) {}

  ngOnInit(): void {
    this.getUserInfo()
  }

  private getUserInfo() {
    this.http.get<User>(`${this.apiUrl}/user`).subscribe(
      (response) => {
        this.user = response
      })
  }

  public onSubmit(form: any): void {
    this.wrongUsername = false;
    this.wrongPassword = false;
    const { oldPassword, newPassword } = form.value;
    const body = new URLSearchParams();
    body.set('oldPassword', oldPassword);
    body.set('newPassword', newPassword);

    const headers = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');

    if (this.username != this.user?.name) {
      this.wrongUsername = true;
      form.reset();
      return;
    }

    this.http.post(`${this.apiUrl}/user/${this.username}/password`, body.toString(), { headers }).subscribe(
        () => {
          form.reset();
          this.authService.logout();
          return;
        });
    this.wrongPassword = true;
    form.reset();
  }
}


