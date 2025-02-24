import { Component, OnInit } from '@angular/core';
import {NgIf} from '@angular/common';
import {User} from '../models/user.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-userinfo',
  imports: [
    NgIf,
    FormsModule
  ],
  templateUrl: './userinfo.component.html',
  standalone: true,
  styleUrl: './userinfo.component.css'
})
export class UserinfoComponent implements OnInit {

  user: User | null = null
  apiUrl = environment.apiUrl;
  newGroupName: String = ""
  newGroupPassword: String = ""

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
      this.http.get<User>(`${this.apiUrl}/user`).subscribe(
        (response) => {
        this.user = response
      })
  }

  isAdmin(): boolean {
   return true;
  }

  createGroup(): void {
    this.http.post(`${this.apiUrl}/usergroups`, {name: this.newGroupName, password: this.newGroupPassword}).subscribe()
  }

  joinGroup(): void {
    /*TODO*/
  }

  deleteGroup(): void {
    const userGroupName = this.user?.userGroup ?? ""
    this.http.delete(`${this.apiUrl}/usergroups`, { params: { name: userGroupName } }).subscribe()
  }

  leaveGroup(): void {
    /*TODO*/
  }

  changeGroupPassword(): void{
    /*TODO*/
  }
}
