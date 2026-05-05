import { Component, OnInit } from '@angular/core';
import {NgIf} from '@angular/common';
import {User} from '@core/models/user.model';
import {FormsModule} from '@angular/forms';
import {CreateUserGroupRequestModel, EditUserGroupRequest, JoinUserGroupRequestModel} from '@core/models/user-group-models';
import {RouterLink} from '@angular/router';
import {ErrorService} from '@core/services/error.service';
import {MatIcon} from '@angular/material/icon';
import {UserService} from '@core/services/user.service';

@Component({
  selector: 'app-userinfo',
  imports: [
    NgIf,
    FormsModule,
    RouterLink,
    MatIcon
  ],
  templateUrl: './userinfo.component.html',
  standalone: true,
  styleUrl: './userinfo.component.css'
})
export class UserinfoComponent implements OnInit {

  user: User | null = null
  isAdmin: boolean = false
  newGroupName: string = ""
  newGroupPassword: string = ""

  constructor(private userService: UserService, public errorService: ErrorService) {}

  ngOnInit(): void {
      this.getUserInfo()
      this.userService.isUserAdmin().subscribe({
        next: (value) => this.isAdmin = value,
        error: () => this.isAdmin = false
      });
  }

  private getUserInfo() {
    this.userService.getUser().subscribe(
      (response) => {
        this.user = response
      })
  }

  createGroup(): void {
    if (!this.newGroupName.trim() || !this.newGroupPassword.trim()) {
      this.errorService.setError('Please enter both group name and password.');
      return;
    }
    const request: CreateUserGroupRequestModel = {userGroupName: this.newGroupName, password: this.newGroupPassword}
    this.userService.createGroup(request).subscribe(
      () => {
        this.errorService.clearError();
        this.getUserInfo()
        this.newGroupPassword = ""
        this.newGroupName = ""
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to create group.');
      }
    );
  }

  joinGroup(): void {
    if (!this.newGroupName.trim() || !this.newGroupPassword.trim()) {
      this.errorService.setError('Please enter both group name and password.');
      return;
    }
    const request: JoinUserGroupRequestModel = {userGroupName: this.newGroupName, password: this.newGroupPassword}
    this.userService.joinGroup(this.user?.name ?? '', request).subscribe(
      () => {
        this.errorService.clearError();
        this.getUserInfo()
        this.newGroupPassword = ""
        this.newGroupName = ""
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to join group.');
      }
    );
  }

  deleteGroup(): void {
    const userGroupName = this.user?.userGroup ?? ""
    this.userService.deleteGroup(userGroupName).subscribe(
      () => {
        this.errorService.clearError();
        this.getUserInfo()
        this.newGroupPassword = ""
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to delete group.');
      }
    );
  }

  leaveGroup(): void {
    this.userService.leaveGroup(this.user?.name ?? '', this.user?.userGroup ?? '').subscribe(
      () => {
        this.errorService.clearError();
        this.getUserInfo()
        this.newGroupPassword = ""
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to leave group.');
      }
    );
  }

  changeGroupPassword(): void{
    if (!this.newGroupPassword.trim()) {
      this.errorService.setError('Please enter a new password.');
      return;
    }
    const request: EditUserGroupRequest = { userGroupName: this.user?.userGroup ?? "", newPassword: this.newGroupPassword }
    this.userService.changeGroupPassword(request).subscribe(
      () => {
        this.errorService.clearError();
        this.getUserInfo()
        this.newGroupPassword = ""
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to change group password.');
      }
    );
  }
}
