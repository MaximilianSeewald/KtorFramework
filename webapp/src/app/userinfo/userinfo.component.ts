import { Component, OnInit } from '@angular/core';
import {NgIf} from '@angular/common';
import {User} from '../models/user.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {FormsModule} from '@angular/forms';
import {CreateUserGroupRequestModel} from "../models/createUserGroupRequest.model";
import {EditUserGroupRequest} from '../models/editUserGroupRequest';
import {JoinUserGroupRequestModel} from '../models/joinUserGroupRequest.model';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-userinfo',
  imports: [
    NgIf,
    FormsModule,
    RouterLink
  ],
  templateUrl: './userinfo.component.html',
  standalone: true,
  styleUrl: './userinfo.component.css'
})
export class UserinfoComponent implements OnInit {

  user: User | null = null
  isAdmin: boolean = false
  apiUrl = environment.apiUrl;
  newGroupName: string = ""
  newGroupPassword: string = ""

  constructor(private http: HttpClient) {}

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
    const request: CreateUserGroupRequestModel = {userGroupName: this.newGroupName, password: this.newGroupPassword}
    this.http.post(`${this.apiUrl}/usergroups`,request).subscribe(() => {
      this.getUserInfo()
      this.newGroupPassword = ""
    })
  }

  joinGroup(): void {
    const request: JoinUserGroupRequestModel = {userGroupName: this.newGroupName, password: this.newGroupPassword}
    this.http.post(`${this.apiUrl}/user/${this.user?.name}/groups`,request).subscribe(() => {
      this.getUserInfo()
      this.newGroupPassword = ""
    })  }

  deleteGroup(): void {
    const userGroupName = this.user?.userGroup ?? ""
    this.http.delete(`${this.apiUrl}/usergroups`, { params: { name: userGroupName } }).subscribe(() => {
      this.getUserInfo()
      this.newGroupPassword = ""
    })
  }

  leaveGroup(): void {
    this.http.delete(`${this.apiUrl}/user/${this.user?.name}/groups/${this.user?.userGroup}`).subscribe(() => {
      this.getUserInfo()
      this.newGroupPassword = ""
    })
  }

  changeGroupPassword(): void{
    const request: EditUserGroupRequest = { userGroupName: this.user?.userGroup ?? "", newPassword: this.newGroupPassword }
    this.http.put(`${this.apiUrl}/usergroups`, request).subscribe(() => {
      this.getUserInfo()
      this.newGroupPassword = ""
    })
  }
}
