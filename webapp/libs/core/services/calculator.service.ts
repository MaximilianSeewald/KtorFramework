import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CalculatorService {
  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  calculateGrades(file: File, points: number): Observable<Blob> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('points', points.toString());

    return this.http.post(`${this.apiUrl}/upload`, formData, { responseType: 'blob' });
  }
}
