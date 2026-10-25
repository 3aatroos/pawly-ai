import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { UserRole } from '../../../core/models/user.model';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-logo">
          <div class="auth-logo-icon">🐾</div>
          <span class="auth-logo-text">Pawly</span>
        </div>

        <h1 class="auth-title">Create your account</h1>
        <p class="auth-sub">Join thousands of pet lovers on Pawly</p>

        @if (error) {
          <div class="alert alert-danger">{{ error }}</div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()">
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px">
            <div class="form-group">
              <label>First name</label>
              <input type="text" formControlName="firstName" placeholder="Rached"
                [class.is-invalid]="f['firstName'].touched && f['firstName'].invalid" />
              @if (f['firstName'].touched && f['firstName'].errors?.['required']) {
                <div class="invalid-feedback">Required</div>
              }
            </div>
            <div class="form-group">
              <label>Last name</label>
              <input type="text" formControlName="lastName" placeholder="Trabelsi"
                [class.is-invalid]="f['lastName'].touched && f['lastName'].invalid" />
              @if (f['lastName'].touched && f['lastName'].errors?.['required']) {
                <div class="invalid-feedback">Required</div>
              }
            </div>
          </div>

          <div class="form-group">
            <label>Email address</label>
            <input type="email" formControlName="email" placeholder="you@example.com"
              [class.is-invalid]="f['email'].touched && f['email'].invalid" />
            @if (f['email'].touched && f['email'].errors?.['required']) {
              <div class="invalid-feedback">Email is required</div>
            }
            @if (f['email'].touched && f['email'].errors?.['email']) {
              <div class="invalid-feedback">Enter a valid email</div>
            }
          </div>

          <div class="form-group">
            <label>Phone (optional)</label>
            <input type="tel" formControlName="phone" placeholder="+216 55 000 000" />
          </div>

          <div class="form-group">
            <label>I am a…</label>
            <select formControlName="role">
              <option value="PET_OWNER">🐕 Pet Owner</option>
              <option value="VETERINARIAN">🩺 Veterinarian</option>
            </select>
          </div>

          <div class="form-group">
            <label>Password</label>
            <input type="password" formControlName="password" placeholder="Min. 6 characters"
              [class.is-invalid]="f['password'].touched && f['password'].invalid" />
            @if (f['password'].touched && f['password'].errors?.['required']) {
              <div class="invalid-feedback">Password is required</div>
            }
            @if (f['password'].touched && f['password'].errors?.['minlength']) {
              <div class="invalid-feedback">Minimum 6 characters</div>
            }
          </div>

          @if (f['role'].value === 'VETERINARIAN') {
            <div class="alert alert-info" style="margin-bottom:16px">
              🩺 Vet accounts require admin verification before activation.
            </div>
          }

          <button type="submit" class="btn btn-primary btn-block" [disabled]="loading">
            @if (loading) { <span class="spinner"></span> } Create account
          </button>
        </form>

        <p class="auth-footer">
          Already have an account?
          <a routerLink="/auth/login">Sign in</a>
        </p>
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
      max-width: 460px;
      box-shadow: 0 8px 40px rgba(108, 99, 255, 0.15);
    }
    .auth-logo {
      display: flex; align-items: center; gap: 10px;
      margin-bottom: 28px; justify-content: center;
    }
    .auth-logo-icon {
      width: 44px; height: 44px; background: var(--primary); border-radius: 12px;
      display: flex; align-items: center; justify-content: center; font-size: 22px;
    }
    .auth-logo-text { font-weight: 800; font-size: 24px; color: var(--primary); }
    .auth-title { font-size: 22px; font-weight: 800; text-align: center; }
    .auth-sub   { font-size: 14px; color: var(--text-soft); text-align: center; margin-top: 4px; margin-bottom: 24px; }
    .auth-footer {
      text-align: center; font-size: 13px; color: var(--text-soft); margin-top: 20px;
      a { color: var(--primary); font-weight: 600; text-decoration: none; }
    }
  `]
})
export class RegisterComponent {
  form = this.fb.group({
    firstName: ['', Validators.required],
    lastName:  ['', Validators.required],
    email:     ['', [Validators.required, Validators.email]],
    phone:     [''],
    role:      ['PET_OWNER' as UserRole],
    password:  ['', [Validators.required, Validators.minLength(6)]]
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

    this.auth.register(this.form.value as any).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: err => {
        this.loading = false;
        this.error = err.error?.message ?? 'Registration failed. Please try again.';
      }
    });
  }
}
