import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';
import { resolveApiUrl } from '../utils/url.util';
import { User } from '../models/user.model';
import {
  CreateUserGroupRequestModel,
  EditUserGroupRequest,
  JoinUserGroupRequestModel
} from '../models/user-group-models';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly apiUrl = resolveApiUrl(environment.apiUrl);

  constructor(private http: HttpClient) {}

  getUser(): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/user`);
  }

  isUserAdmin(): Observable<boolean> {
    return this.http.get<boolean>(`${this.apiUrl}/usergroups/admin`);
  }

  createGroup(request: CreateUserGroupRequestModel): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/usergroups`, request);
  }

  joinGroup(username: string, request: JoinUserGroupRequestModel): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/user/${username}/groups`, request);
  }

  deleteGroup(userGroupName: string): Observable<unknown> {
    return this.http.delete(`${this.apiUrl}/usergroups`, { params: { name: userGroupName } });
  }

  leaveGroup(username: string, userGroupName: string): Observable<unknown> {
    return this.http.delete(`${this.apiUrl}/user/${username}/groups/${userGroupName}`);
  }

  changeGroupPassword(request: EditUserGroupRequest): Observable<unknown> {
    return this.http.put(`${this.apiUrl}/usergroups`, request);
  }

  changePassword(username: string, oldPassword: string, newPassword: string): Observable<unknown> {
    const body = new URLSearchParams();
    body.set('oldPassword', oldPassword);
    body.set('newPassword', newPassword);

    const headers = new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded');
    return this.http.post(`${this.apiUrl}/user/${username}/password`, body.toString(), { headers });
  }
}
