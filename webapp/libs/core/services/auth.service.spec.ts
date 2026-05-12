import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService, VerifySessionResponse } from '@core';
import { ErrorService } from '@core';
import { environment } from '@core';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let errorService: jasmine.SpyObj<ErrorService>;
  let router: jasmine.SpyObj<Router>;
  const originalHaAutoLogin = environment.haAutoLogin;

  const verifyResponse: VerifySessionResponse = {
    valid: true,
    user: { id: 1, name: 'alice', userGroup: 'family' }
  };

  beforeEach(() => {
    errorService = jasmine.createSpyObj<ErrorService>('ErrorService', ['clearError', 'setError']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ErrorService, useValue: errorService },
        { provide: Router, useValue: router }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
    environment.haAutoLogin = false;
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    environment.haAutoLogin = originalHaAutoLogin;
  });

  it('sets logged-in state and current user when token verifies', async () => {
    localStorage.setItem('token', 'valid-token');

    const resultPromise = service.verifyToken();
    const request = httpMock.expectOne(`${service.apiUrl}/verify`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer valid-token');
    request.flush(verifyResponse);

    await expectAsync(resultPromise).toBeResolvedTo(true);
    expect(service.isLoggedIn).toBeTrue();
    expect(service.currentUser).toEqual(verifyResponse.user);
    expect(errorService.clearError).toHaveBeenCalled();
  });

  it('clears token and session state when verify is unauthorized', async () => {
    localStorage.setItem('token', 'expired-token');

    const resultPromise = service.verifyToken();
    httpMock.expectOne(`${service.apiUrl}/verify`).flush(
      { message: 'Invalid session' },
      { status: 401, statusText: 'Unauthorized' }
    );

    await expectAsync(resultPromise).toBeResolvedTo(false);
    expect(localStorage.getItem('token')).toBeNull();
    expect(service.isLoggedIn).toBeFalse();
    expect(service.currentUser).toBeNull();
  });

  it('deduplicates simultaneous verify calls', async () => {
    localStorage.setItem('token', 'valid-token');

    const first = service.verifyToken();
    const second = service.verifyToken();
    const requests = httpMock.match(`${service.apiUrl}/verify`);
    expect(requests.length).toBe(1);
    requests[0].flush(verifyResponse);

    await expectAsync(first).toBeResolvedTo(true);
    await expectAsync(second).toBeResolvedTo(true);
  });

  it('requests a fresh Home Assistant session instead of verifying an existing stored token', async () => {
    environment.haAutoLogin = true;
    localStorage.setItem('token', 'existing-ha-token');

    const resultPromise = service.verifyToken();
    httpMock.expectOne(`${service.apiUrl}/ha/session`).flush({ token: 'new-ha-token' });
    await new Promise(resolve => setTimeout(resolve, 0));
    const verifyNewToken = httpMock.expectOne(`${service.apiUrl}/verify`);
    expect(verifyNewToken.request.headers.get('Authorization')).toBe('Bearer new-ha-token');
    verifyNewToken.flush(verifyResponse);

    await expectAsync(resultPromise).toBeResolvedTo(true);
    expect(localStorage.getItem('token')).toBe('new-ha-token');
    expect(service.currentUser).toEqual(verifyResponse.user);
  });
});
