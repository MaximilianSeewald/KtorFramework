import {Component, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {NgIf} from '@angular/common';
import {AuthService} from '@core/services/auth.service';
import {User} from '@core/models/user.model';
import {MatIconModule} from '@angular/material/icon';
import {ErrorService} from '@core/services/error.service';
import {UserService} from '@core/services/user.service';

@Component({
  selector: 'app-change-password',
  imports: [
    FormsModule,
    NgIf,
    MatIconModule
  ],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.css',
  standalone: true
})
export class ChangePasswordComponent implements OnInit{

  user: User | null = null
  username: string = '';
  oldPassword: string = '';
  newPassword: string = '';
  wrongUsername: boolean = false;
  wrongPassword: boolean = false;

  constructor(private userService: UserService, private authService: AuthService, public errorService: ErrorService) {}

  ngOnInit(): void {
    this.getUserInfo()
  }

  private getUserInfo() {
    this.userService.getUser().subscribe(
      (response) => {
        this.user = response
      })
  }

  public onSubmit(form: any): void {
    this.errorService.clearError();
    this.wrongUsername = false;
    this.wrongPassword = false;
    const { oldPassword, newPassword } = form.value;

    if (this.username != this.user?.name) {
      this.wrongUsername = true;
      form.reset();
      return;
    }

    this.userService.changePassword(this.username, oldPassword, newPassword).subscribe(
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
