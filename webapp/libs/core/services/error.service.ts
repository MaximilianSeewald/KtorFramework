import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class ErrorService {
  errorMessage: string = '';

  setError(message: string): void {
    this.errorMessage = message;
  }

  clearError(): void {
    this.errorMessage = '';
  }
}

