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
  isEditingGroup = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
      this.http.get<User>(`${this.apiUrl}/user`).subscribe(
        (response) => {
        this.user = response
      })
  }

  toggleEditGroup() {
    if (this.isEditingGroup) {
      this.http.put<User>(`${this.apiUrl}/user`, this.user).subscribe()
    }
    this.isEditingGroup = !this.isEditingGroup;
  }

}
