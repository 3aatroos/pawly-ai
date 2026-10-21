import { Routes } from '@angular/router';
import { authGuard, adminGuard, guestGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  // Auth routes (guest only — no shell)
  {
    path: 'auth',
    canActivate: [guestGuard],
    children: [
      {
        path: 'login',
        loadComponent: () =>
          import('./features/auth/login/login.component').then(m => m.LoginComponent)
      },
      {
        path: 'register',
        loadComponent: () =>
          import('./features/auth/register/register.component').then(m => m.RegisterComponent)
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' }
    ]
  },

  // Protected routes wrapped in shell layout
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./shell/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/profile/profile.component').then(m => m.ProfileComponent)
      },
      {
        path: 'pets',
        loadComponent: () =>
          import('./features/pets/pet-list/pet-list.component').then(m => m.PetListComponent)
      },
      {
        path: 'pets/new',
        loadComponent: () =>
          import('./features/pets/pet-form/pet-form.component').then(m => m.PetFormComponent)
      },
      {
        path: 'pets/:id/edit',
        loadComponent: () =>
          import('./features/pets/pet-form/pet-form.component').then(m => m.PetFormComponent)
      },
      {
        path: 'ai-emergency',
        loadComponent: () =>
          import('./features/ai-emergency/ai-emergency.component').then(m => m.AiEmergencyComponent)
      },
      {
        path: 'vet-profile',
        loadComponent: () =>
          import('./features/vet-profile/vet-profile.component').then(m => m.VetProfileComponent)
      },
      {
        path: 'vets',
        loadComponent: () =>
          import('./features/vet-profile/vet-profile.component').then(m => m.VetProfileComponent)
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications.component').then(m => m.NotificationsComponent)
      },
      // Admin routes
      {
        path: 'admin',
        canActivate: [adminGuard],
        children: [
          {
            path: 'users',
            loadComponent: () =>
              import('./features/admin/user-management/user-management.component').then(m => m.UserManagementComponent)
          },
          {
            path: 'vets',
            loadComponent: () =>
              import('./features/admin/vet-verification/vet-verification.component').then(m => m.VetVerificationComponent)
          },
          { path: '', redirectTo: 'users', pathMatch: 'full' }
        ]
      }
    ]
  },

  // 404 fallback
  { path: '**', redirectTo: '' }
];