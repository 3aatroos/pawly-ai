import { Routes } from '@angular/router';
import { authGuard, adminGuard, guestGuard, vetOrAdminGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  // ── Auth (guest only) ────────────────────────────────────
  {
    path: 'auth',
    canActivate: [guestGuard],
    children: [
      { path: 'login',    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent) },
      { path: 'register', loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent) },
      { path: '', redirectTo: 'login', pathMatch: 'full' }
    ]
  },

  // ── Protected (shell) ─────────────────────────────────────
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },

      { path: 'dashboard',     loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'profile',       loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent) },
      { path: 'pets',          loadComponent: () => import('./features/pets/pet-list/pet-list.component').then(m => m.PetListComponent) },
      { path: 'pets/new',      loadComponent: () => import('./features/pets/pet-form/pet-form.component').then(m => m.PetFormComponent) },
      { path: 'pets/:id/edit', loadComponent: () => import('./features/pets/pet-form/pet-form.component').then(m => m.PetFormComponent) },
      { path: 'ai-emergency',  loadComponent: () => import('./features/ai-emergency/ai-emergency.component').then(m => m.AiEmergencyComponent) },
      { path: 'vet-profile',   loadComponent: () => import('./features/vet-profile/vet-profile.component').then(m => m.VetProfileComponent) },
      { path: 'vets',          loadComponent: () => import('./features/vet-profile/vet-profile.component').then(m => m.VetProfileComponent) },
      { path: 'notifications', loadComponent: () => import('./features/notifications/notifications.component').then(m => m.NotificationsComponent) },

      // ── Adoption ──────────────────────────────────────────
      { path: 'adoption',
        loadComponent: () => import('./features/adoption/adoption-feed.component').then(m => m.AdoptionFeedComponent) },
      { path: 'adoption/new',
        canActivate: [vetOrAdminGuard],
        loadComponent: () => import('./features/adoption/adoption-publish.component').then(m => m.AdoptionPublishComponent) },
      { path: 'adoption/:id/applications',
        canActivate: [vetOrAdminGuard],
        loadComponent: () => import('./features/adoption/adoption-applications.component').then(m => m.AdoptionApplicationsComponent) },
      { path: 'adoption/:id',
        loadComponent: () => import('./features/adoption/adoption-detail.component').then(m => m.AdoptionDetailComponent) },

      // ── Store ─────────────────────────────────────────────
      { path: 'store', redirectTo: 'store/products', pathMatch: 'full' },

      { path: 'store/products',
        loadComponent: () => import('./features/store/product-list/product-list.component').then(m => m.ProductListComponent) },
      { path: 'store/products/:id',
        loadComponent: () => import('./features/store/product-details/product-details.component').then(m => m.ProductDetailsComponent) },
      { path: 'store/cart',
        loadComponent: () => import('./features/store/cart/cart.component').then(m => m.CartComponent) },
      { path: 'store/checkout',
        loadComponent: () => import('./features/store/checkout/checkout.component').then(m => m.CheckoutComponent) },
      { path: 'store/orders',
        loadComponent: () => import('./features/store/orders/order-list.component').then(m => m.OrderListComponent) },
      { path: 'store/orders/:id',
        loadComponent: () => import('./features/store/orders/order-detail.component').then(m => m.OrderDetailComponent) },
      { path: 'store/recommendations',
        loadComponent: () => import('./features/store/recommendations/recommendations.component').then(m => m.RecommendationsComponent) },

      // ── Store Management (VET + ADMIN) ─────────────────────
      { path: 'store/manage/products/new',
        canActivate: [vetOrAdminGuard],
        loadComponent: () => import('./features/store/management/product-management.component').then(m => m.ProductManagementComponent) },
      { path: 'store/manage/products/:id/edit',
        canActivate: [vetOrAdminGuard],
        loadComponent: () => import('./features/store/management/product-management.component').then(m => m.ProductManagementComponent) },
      { path: 'store/manage/stock',
        canActivate: [vetOrAdminGuard],
        loadComponent: () => import('./features/store/management/stock-management.component').then(m => m.StockManagementComponent) },

      // ── Admin ─────────────────────────────────────────────
      {
        path: 'admin',
        canActivate: [adminGuard],
        children: [
          { path: 'users',      loadComponent: () => import('./features/admin/user-management/user-management.component').then(m => m.UserManagementComponent) },
          { path: 'vets',       loadComponent: () => import('./features/admin/vet-verification/vet-verification.component').then(m => m.VetVerificationComponent) },
          { path: 'orders',     loadComponent: () => import('./features/admin/order-management/order-management.component').then(m => m.OrderManagementComponent) },
          { path: 'categories', loadComponent: () => import('./features/store/management/category-management.component').then(m => m.CategoryManagementComponent) },
          { path: '',           redirectTo: 'users', pathMatch: 'full' }
        ]
      }
    ]
  },

  { path: '**', redirectTo: '' }
];
