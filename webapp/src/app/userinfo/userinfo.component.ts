import { Component, OnInit } from '@angular/core';
import {NgIf} from '@angular/common';
import {User} from '../models/user.model';
import {HttpClient} from '@angular/common/http';
import {FormsModule} from '@angular/forms';
import {CreateUserGroupRequestModel} from "../models/createUserGroupRequest.model";
import {EditUserGroupRequest} from '../models/editUserGroupRequest';
import {JoinUserGroupRequestModel} from '../models/joinUserGroupRequest.model';
import {RouterLink} from '@angular/router';
import {ErrorService} from '../error.service';
import {MatIcon} from '@angular/material/icon';

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
  apiUrl = window.location.hostname === 'localhost' ? 'http://localhost:8080' : window.location.origin;
  newGroupName: string = ""
  newGroupPassword: string = ""

  constructor(private http: HttpClient, public errorService: ErrorService) {}

  ngOnInit(): void {
      this.getUserInfo()
      this.isUserAdmin().then(value => {
        this.isAdmin = value
      })
  }

  private getUserInfo() {
    this.http.get<User>(`${this.apiUrl}/user`).subscribe(
      (response) => {
        this.user = response
      })
  }

  async isUserAdmin(): Promise<boolean> {
    try {
      let response = await this.http.get<boolean>(`${this.apiUrl}/usergroups/admin`).toPromise();
      return response ?? false;
    } catch (e) {
      return false;
    }
  }


  createGroup(): void {
    if (!this.newGroupName.trim() || !this.newGroupPassword.trim()) {
      this.errorService.setError('Please enter both group name and password.');
      return;
    }
    const request: CreateUserGroupRequestModel = {userGroupName: this.newGroupName, password: this.newGroupPassword}
    this.http.post(`${this.apiUrl}/usergroups`,request).subscribe(
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
    this.http.post(`${this.apiUrl}/user/${this.user?.name}/groups`,request).subscribe(
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
    this.http.delete(`${this.apiUrl}/usergroups`, { params: { name: userGroupName } }).subscribe(
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
    this.http.delete(`${this.apiUrl}/user/${this.user?.name}/groups/${this.user?.userGroup}`).subscribe(
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
    this.http.put(`${this.apiUrl}/usergroups`, request).subscribe(
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
