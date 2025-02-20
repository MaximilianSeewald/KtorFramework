import { Component } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {NgIf} from '@angular/common';
import {environment} from '../../environments/environment';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-calculator',
  imports: [
    NgIf,
    FormsModule
  ],
  templateUrl: './calculator.component.html',
  standalone: true,
  styleUrl: './calculator.component.css'
})
export class CalculatorComponent {

  points: number = 0;
  fileName: string = '';
  message: string = '';
  file: File | null = null;
  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  onFileSelected(event: any) {
    const file: File = event.target.files[0];

    if (file) {
      this.fileName = file.name;
      this.file = file;
    }
  }

  onUpload() {
    if (!this.fileName) {
      this.message = "Please select a file first!";
      return;
    }
    if(this.file != null) {
      const formData = new FormData();
      formData.append('file', this.file, this.fileName);
      formData.append('points', this.points.toString())

      this.http.post(`${this.apiUrl}/upload`, formData, { responseType: 'blob'}).subscribe(
        (response) => {
          const blob = new Blob([response], { type: 'application/zip' });
          const link = document.createElement('a');
          link.href = window.URL.createObjectURL(blob);
          link.download = 'grades.zip';
          link.click();
        },
        (error) => {
          this.message = 'Error calculating grades';
          console.log(error)
        }
      );
    }

  }

}
