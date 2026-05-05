import { Component } from '@angular/core';
import {NgIf} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {MatIcon} from '@angular/material/icon';
import {CalculatorService} from '@core/services/calculator.service';

@Component({
  selector: 'app-calculator',
  imports: [
    NgIf,
    FormsModule,
    MatIcon
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

  constructor(private calculatorService: CalculatorService) {}

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
      this.calculatorService.calculateGrades(this.file, this.points).subscribe(
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
