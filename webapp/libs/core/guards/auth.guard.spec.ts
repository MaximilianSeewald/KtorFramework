import { AuthGuard, NoAuthGuard } from '@core';
import { AuthService } from '@core';
import { Router } from '@angular/router';

describe('Auth guards', () => {
  let router: jasmine.SpyObj<Router>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['verifyToken']);
  });

  it('AuthGuard allows authenticated users', async () => {
    authService.verifyToken.and.resolveTo(true);
    const guard = new AuthGuard(router, authService);

    await expectAsync(guard.canActivate()).toBeResolvedTo(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('AuthGuard redirects unauthenticated users to login', async () => {
    authService.verifyToken.and.resolveTo(false);
    const guard = new AuthGuard(router, authService);

    await expectAsync(guard.canActivate()).toBeResolvedTo(false);
    expect(router.navigate).toHaveBeenCalledWith(['login']);
  });

  it('NoAuthGuard redirects authenticated users to dashboard', async () => {
    authService.verifyToken.and.resolveTo(true);
    const guard = new NoAuthGuard(router, authService);

    await expectAsync(guard.canActivate()).toBeResolvedTo(false);
    expect(router.navigate).toHaveBeenCalledWith(['dashboard']);
  });

  it('NoAuthGuard allows unauthenticated users', async () => {
    authService.verifyToken.and.resolveTo(false);
    const guard = new NoAuthGuard(router, authService);

    await expectAsync(guard.canActivate()).toBeResolvedTo(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
