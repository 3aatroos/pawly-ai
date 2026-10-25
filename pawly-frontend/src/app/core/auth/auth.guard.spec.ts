import { TestBed } from '@angular/core/testing';
import { Router, Routes } from '@angular/router';
import { routes } from '../../app.routes';
import { AuthService } from './auth.service';
import { adminGuard, authGuard, guestGuard, vetOrAdminGuard } from './auth.guard';

describe('Store route protection', () => {
  let auth: jasmine.SpyObj<Pick<AuthService, 'isLoggedIn' | 'isAdmin' | 'isVet'>>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'isAdmin', 'isVet']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router }
      ]
    });
  });

  it('sends an anonymous visitor to login', () => {
    auth.isLoggedIn.and.returnValue(false);
    expect(run(authGuard)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('lets a pet owner through the authenticated shell', () => {
    auth.isLoggedIn.and.returnValue(true);
    expect(run(authGuard)).toBeTrue();
  });

  it('keeps store management for a veterinarian or an administrator', () => {
    auth.isVet.and.returnValue(false);
    auth.isAdmin.and.returnValue(false);
    expect(run(vetOrAdminGuard)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/store/products']);

    auth.isVet.and.returnValue(true);
    expect(run(vetOrAdminGuard)).toBeTrue();
  });

  it('keeps the admin area for an administrator', () => {
    auth.isAdmin.and.returnValue(false);
    expect(run(adminGuard)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);

    auth.isAdmin.and.returnValue(true);
    expect(run(adminGuard)).toBeTrue();
  });

  it('turns a logged-in visitor away from the guest login route', () => {
    auth.isLoggedIn.and.returnValue(true);
    expect(run(guestGuard)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('places the store pages behind those guards', () => {
    const shell = routes.find(route => route.path === '' && route.children);
    expect(shell?.canActivate).toContain(authGuard);
    expect(child(shell, 'store/cart')).toBeTruthy();
    expect(child(shell, 'store/checkout')).toBeTruthy();
    expect(child(shell, 'store/orders')).toBeTruthy();
    expect(child(shell, 'store/recommendations')).toBeTruthy();
    expect(child(shell, 'adoption')).toBeTruthy();
    expect(child(shell, 'adoption/new')?.canActivate).toContain(vetOrAdminGuard);
    expect(child(shell, 'adoption/:id/applications')?.canActivate).toContain(vetOrAdminGuard);
    expect(child(shell, 'store/manage/products/new')?.canActivate).toContain(vetOrAdminGuard);
    expect(child(shell, 'admin')?.canActivate).toContain(adminGuard);
    expect(routes.find(route => route.path === 'auth')?.canActivate).toContain(guestGuard);
  });
});

function run(guard: typeof authGuard): boolean {
  return TestBed.runInInjectionContext(() => guard(undefined!, undefined!)) as boolean;
}

function child(shell: Routes[number] | undefined, path: string) {
  return shell?.children?.find(route => route.path === path);
}
