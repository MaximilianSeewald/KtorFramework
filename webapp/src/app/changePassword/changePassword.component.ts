import {Component, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {NgIf} from '@angular/common';
import {HttpHeaders} from '@angular/common/http';
import {HttpClient} from '@angular/common/http';
import {AuthService} from '../auth.service';
import {User} from '../models/user.model';
import {MatIconModule} from '@angular/material/icon';
import {ErrorService} from '../error.service';
import {environment} from '../../environments/environment';

@Component({
  selector: 'app-change-password',
  imports: [
    FormsModule,
    NgIf,
    MatIconModule
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

  constructor(private http: HttpClient, private authService: AuthService, public errorService: ErrorService) {}

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
    this.errorService.clearError();
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
        },
        (error) => {
          this.errorService.setError(error.error?.message || 'Password change failed.');
          this.wrongPassword = true;
          form.reset();
        });
  }
}
