import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-logo">
          <div class="auth-logo-icon">🐾</div>
          <span class="auth-logo-text">Pawly</span>
        </div>

        <h1 class="auth-title">Welcome back</h1>
        <p class="auth-sub">Sign in to manage your pets and profile</p>

        @if (error) {
          <div class="alert alert-danger">{{ error }}</div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="form-group">
            <label>Email address</label>
            <input
              type="email"
              formControlName="email"
              placeholder="you@example.com"
              [class.is-invalid]="f['email'].touched && f['email'].invalid"
            />
            @if (f['email'].touched && f['email'].errors?.['required']) {
              <div class="invalid-feedback">Email is required</div>
            }
            @if (f['email'].touched && f['email'].errors?.['email']) {
              <div class="invalid-feedback">Enter a valid email</div>
            }
          </div>

          <div class="form-group">
            <label>Password</label>
            <input
              type="password"
              formControlName="password"
              placeholder="••••••••"
              [class.is-invalid]="f['password'].touched && f['password'].invalid"
            />
            @if (f['password'].touched && f['password'].errors?.['required']) {
              <div class="invalid-feedback">Password is required</div>
            }
          </div>

          <button type="submit" class="btn btn-primary btn-block" [disabled]="loading">
            @if (loading) { <span class="spinner"></span> } Sign in
          </button>
        </form>

        <p class="auth-footer">
          Don't have an account?
          <a routerLink="/auth/register">Create one</a>
        </p>

        <!-- Demo credentials hint -->
        <div class="demo-hint">
          <strong>Demo admin:</strong> admin&#64;pawly.com / Admin&#64;123
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-page {
      min-height: 100vh;
      background: linear-gradient(135deg, #ECEFFE 0%, #dde1ff 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }

    .auth-card {
      background: #fff;
      border-radius: 20px;
      padding: 40px;
      width: 100%;
      max-width: 420px;
      box-shadow: 0 8px 40px rgba(108, 99, 255, 0.15);
    }

    .auth-logo {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 28px;
      justify-content: center;
    }
    .auth-logo-icon {
      width: 44px;
      height: 44px;
      background: var(--primary);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
    }
    .auth-logo-text { font-weight: 800; font-size: 24px; color: var(--primary); }

    .auth-title { font-size: 22px; font-weight: 800; text-align: center; }
    .auth-sub   { font-size: 14px; color: var(--text-soft); text-align: center; margin-top: 4px; margin-bottom: 24px; }

    .auth-footer {
      text-align: center;
      font-size: 13px;
      color: var(--text-soft);
      margin-top: 20px;

      a { color: var(--primary); font-weight: 600; text-decoration: none; }
    }

    .demo-hint {
      margin-top: 16px;
      padding: 10px 14px;
      background: var(--primary-light);
      border-radius: 10px;
      font-size: 12px;
      color: var(--primary);
      text-align: center;
    }
  `]
})
export class LoginComponent {
  form = this.fb.group({
    email:    ['admin@pawly.com', [Validators.required, Validators.email]],
    password: ['Admin@123',       [Validators.required]]
  });

  loading = false;
  error   = '';

  constructor(
    private fb:     FormBuilder,
    private auth:   AuthService,
    private router: Router
  ) {}

  get f() { return this.form.controls; }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading = true;
    this.error   = '';

    this.auth.login(this.form.value as any).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: err => {
        this.loading = false;
        this.error = err.error?.message ?? 'Login failed. Check your credentials.';
      }
    });
  }
}
