import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { UserService } from '../../core/services/user.service';
import { User } from '../../core/models/user.model';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">My Profile</div>
          <div class="page-sub">Manage your personal information</div>
        </div>
        @if (!editing) {
          <button class="btn btn-primary btn-sm" (click)="startEdit()">✏️ Edit Profile</button>
        }
      </div>

      @if (user) {
        <!-- Hero card -->
        <div class="profile-hero">
          <div class="profile-avatar-lg">{{ initials }}</div>
          <div>
            <div class="profile-name">{{ user.firstName }} {{ user.lastName }}</div>
            <div class="profile-meta">{{ user.email }} @if(user.phone) { · {{ user.phone }} }</div>
            <div class="profile-badges">
              <span class="profile-badge-white">{{ roleLabel }}</span>
              <span class="profile-badge-white" [class]="statusClass">{{ user.status }}</span>
            </div>
          </div>
        </div>

        <div class="two-col">
          <!-- View mode -->
          @if (!editing) {
            <div class="card">
              <div class="card-header"><span class="card-title">Personal Information</span></div>
              <div class="info-grid">
                <div class="info-item"><label>First Name</label><span>{{ user.firstName }}</span></div>
                <div class="info-item"><label>Last Name</label><span>{{ user.lastName }}</span></div>
                <div class="info-item"><label>Email</label><span>{{ user.email }}</span></div>
                <div class="info-item"><label>Phone</label><span>{{ user.phone || '—' }}</span></div>
                <div class="info-item"><label>Address</label><span>{{ user.address || '—' }}</span></div>
                <div class="info-item"><label>Member Since</label><span>{{ user.createdAt | date:'mediumDate' }}</span></div>
              </div>
            </div>
          }

          <!-- Edit mode -->
          @if (editing) {
            <div class="card">
              <div class="card-header"><span class="card-title">Edit Profile</span></div>

              @if (saveError) {
                <div class="alert alert-danger">{{ saveError }}</div>
              }
              @if (saveSuccess) {
                <div class="alert alert-success">Profile updated successfully!</div>
              }

              <form [formGroup]="form" (ngSubmit)="save()">
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px">
                  <div class="form-group">
                    <label>First Name</label>
                    <input type="text" formControlName="firstName" />
                  </div>
                  <div class="form-group">
                    <label>Last Name</label>
                    <input type="text" formControlName="lastName" />
                  </div>
                </div>
                <div class="form-group">
                  <label>Phone</label>
                  <input type="tel" formControlName="phone" />
                </div>
                <div class="form-group">
                  <label>Address</label>
                  <input type="text" formControlName="address" />
                </div>
                <div style="display:flex; gap:10px; margin-top:8px">
                  <button type="submit" class="btn btn-primary" [disabled]="saving">
                    @if (saving) { <span class="spinner"></span> } Save Changes
                  </button>
                  <button type="button" class="btn btn-ghost" (click)="cancelEdit()">Cancel</button>
                </div>
              </form>
            </div>
          }

          <!-- Account summary -->
          <div class="card">
            <div class="card-header"><span class="card-title">Account Summary</span></div>
            <div class="info-grid">
              <div class="info-item">
                <label>Role</label>
                <span><span class="badge" [ngClass]="roleBadgeClass">{{ user.role }}</span></span>
              </div>
              <div class="info-item">
                <label>Status</label>
                <span><span class="badge" [ngClass]="statusBadgeClass">{{ user.status }}</span></span>
              </div>
              <div class="info-item">
                <label>Pets Registered</label>
                <span>{{ user.petCount ?? 0 }}</span>
              </div>
              <div class="info-item">
                <label>User ID</label>
                <span>#{{ user.id }}</span>
              </div>
            </div>
          </div>
        </div>
      } @else {
        <div class="loading-overlay"><div class="spinner"></div></div>
      }
    </div>
  `,
  styles: [`
    .profile-hero {
      background: linear-gradient(135deg, var(--primary) 0%, #9B59B6 100%);
      border-radius: var(--radius);
      padding: 32px;
      color: #fff;
      display: flex;
      align-items: center;
      gap: 24px;
      margin-bottom: 24px;
      position: relative;
      overflow: hidden;

      &::after {
        content: '🐾';
        position: absolute; right: 32px; bottom: 16px;
        font-size: 80px; opacity: 0.12;
      }
    }
    .profile-avatar-lg {
      width: 80px; height: 80px; border-radius: 50%;
      background: rgba(255,255,255,.3);
      display: flex; align-items: center; justify-content: center;
      font-size: 36px; font-weight: 800; color: #fff;
      border: 3px solid rgba(255,255,255,.5);
      flex-shrink: 0;
    }
    .profile-name  { font-size: 22px; font-weight: 800; }
    .profile-meta  { font-size: 14px; opacity: 0.85; margin-top: 4px; }
    .profile-badges { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
    .profile-badge-white {
      background: rgba(255,255,255,.2);
      border: 1px solid rgba(255,255,255,.4);
      color: #fff;
      padding: 4px 12px; border-radius: 99px; font-size: 12px; font-weight: 600;
    }

    .info-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 14px;
    }
    .info-item {
      label { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.07em; color: var(--text-soft); display: block; margin-bottom: 4px; }
      span  { font-size: 14px; font-weight: 500; }
    }
  `]
})
export class ProfileComponent implements OnInit {
  user: User | null = null;
  editing = false;
  saving  = false;
  saveError   = '';
  saveSuccess = false;

  form = this.fb.group({
    firstName: [''],
    lastName:  [''],
    phone:     [''],
    address:   ['']
  });

  constructor(
    private fb:      FormBuilder,
    public  auth:    AuthService,
    private userSvc: UserService
  ) {}

  ngOnInit(): void {
    this.userSvc.getMe().subscribe(u => this.user = u);
  }

  get initials(): string {
    return `${this.user?.firstName?.[0] ?? ''}${this.user?.lastName?.[0] ?? ''}`;
  }

  get roleLabel(): string {
    const map: Record<string, string> = {
      ADMINISTRATOR: '🛡️ Administrator',
      VETERINARIAN:  '🩺 Veterinarian',
      PET_OWNER:     '🐕 Pet Owner'
    };
    return map[this.user?.role ?? ''] ?? this.user?.role ?? '';
  }

  get statusClass(): string { return this.user?.status === 'ACTIVE' ? 'status-active' : ''; }
  get roleBadgeClass():   string {
    const m: Record<string, string> = { ADMINISTRATOR: 'badge badge-admin', VETERINARIAN: 'badge badge-vet', PET_OWNER: 'badge badge-owner' };
    return m[this.user?.role ?? ''] ?? 'badge';
  }
  get statusBadgeClass(): string {
    const m: Record<string, string> = { ACTIVE: 'badge badge-active', INACTIVE: 'badge badge-inactive', PENDING: 'badge badge-pending' };
    return m[this.user?.status ?? ''] ?? 'badge';
  }

  startEdit(): void {
    this.form.patchValue({
      firstName: this.user?.firstName,
      lastName:  this.user?.lastName,
      phone:     this.user?.phone,
      address:   this.user?.address
    });
    this.editing = true;
  }

  cancelEdit(): void { this.editing = false; this.saveError = ''; }

  save(): void {
    this.saving = true;
    this.saveError = '';
    this.saveSuccess = false;

    this.userSvc.updateMe(this.form.value as any).subscribe({
      next: u => {
        this.user = u;
        this.saving = false;
        this.saveSuccess = true;
        this.editing = false;
        setTimeout(() => this.saveSuccess = false, 3000);
      },
      error: err => {
        this.saving = false;
        this.saveError = err.error?.message ?? 'Update failed';
      }
    });
  }
}
