import { Component, OnInit } from '@angular/core';
import {NgIf} from '@angular/common';
import {User} from '../models/user.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';

@Component({
  selector: 'app-userinfo',
  imports: [
    NgIf
  ],
  templateUrl: './userinfo.component.html',
  standalone: true,
  styleUrl: './userinfo.component.css'
})
export class UserinfoComponent implements OnInit {

  user: User | null = null
  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
      this.http.get<User>(`${this.apiUrl}/user`).subscribe(
        (response) => {
        this.user = response
      })
  }



}
