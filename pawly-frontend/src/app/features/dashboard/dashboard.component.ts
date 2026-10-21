import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AdminService } from '../../core/services/admin.service';
import { UserService } from '../../core/services/user.service';
import { PetService } from '../../core/services/pet.service';
import { VetService } from '../../core/services/vet.service';
import { Pet } from '../../core/models/pet.model';
import { Notification } from '../../core/models/notification.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">Welcome back, {{ auth.currentUser()?.firstName }} 👋</div>
          <div class="page-sub">Here's what's happening in your Pawly account today.</div>
        </div>
        <div class="header-actions">
          <a routerLink="/notifications" class="btn btn-ghost btn-sm">🔔 Alerts</a>
        </div>
      </div>

      <!-- Stats (admin only) -->
      @if (auth.isAdmin() && stats) {
        <div class="stats-row">
          <div class="stat-card">
            <div class="stat-icon">👥</div>
            <div class="stat-value">{{ stats['totalUsers'] ?? 0 }}</div>
            <div class="stat-label">Total Users</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">🩺</div>
            <div class="stat-value">{{ stats['veterinarians'] ?? 0 }}</div>
            <div class="stat-label">Veterinarians</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">✅</div>
            <div class="stat-value">{{ stats['activeAccounts'] ?? 0 }}</div>
            <div class="stat-label">Active Accounts</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">⚠️</div>
            <div class="stat-value">{{ stats['pendingVets'] ?? 0 }}</div>
            <div class="stat-label">Pending Verifications</div>
          </div>
        </div>
      }

      <!-- Pet owner stats -->
      @if (!auth.isAdmin()) {
        <div class="stats-row" style="grid-template-columns: repeat(3,1fr)">
          <div class="stat-card">
            <div class="stat-icon">🐕</div>
            <div class="stat-value">{{ petCount }}</div>
            <div class="stat-label">My Pets</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">🔔</div>
            <div class="stat-value">{{ unreadCount }}</div>
            <div class="stat-label">Unread Notifications</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">👤</div>
            <div class="stat-value" style="font-size:14px">
              <span class="badge" [ngClass]="roleBadge()">{{ auth.currentUser()?.role }}</span>
            </div>
            <div class="stat-label">My Role</div>
          </div>
        </div>
      }

      <div class="two-col">
        <!-- Recent notifications -->
        <div class="card">
          <div class="card-header">
            <span class="card-title">Recent Notifications</span>
            <a routerLink="/notifications" class="btn btn-ghost btn-sm">View all</a>
          </div>

          @if (notifications.length === 0) {
            <div class="empty-state">
              <div class="empty-icon">🔔</div>
              <div class="empty-title">No notifications yet</div>
            </div>
          }

          @for (n of notifications.slice(0, 4); track n.id) {
            <div class="notif-item" [class.unread]="!n.isRead" [class.unread-danger]="n.type === 'EMERGENCY' && !n.isRead">
              <div class="notif-icon">{{ notifIcon(n.type) }}</div>
              <div>
                <div class="notif-title">{{ n.title }}</div>
                <div class="notif-desc">{{ n.message }}</div>
                <div class="notif-time">{{ n.createdAt | date:'short' }}</div>
              </div>
            </div>
          }
        </div>

        <!-- Quick actions -->
        <div class="card">
          <div class="card-header">
            <span class="card-title">Quick Actions</span>
          </div>

          <div style="display:flex; flex-direction:column; gap:10px">
            <a routerLink="/pets/new" class="quick-action">
              <span class="qa-icon">🐕</span>
              <div>
                <div class="qa-title">Add a Pet</div>
                <div class="qa-sub">Register a new pet to your profile</div>
              </div>
            </a>
            <a routerLink="/profile" class="quick-action">
              <span class="qa-icon">👤</span>
              <div>
                <div class="qa-title">Edit Profile</div>
                <div class="qa-sub">Update your personal information</div>
              </div>
            </a>
            <a routerLink="/vets" class="quick-action">
              <span class="qa-icon">🩺</span>
              <div>
                <div class="qa-title">Find a Vet</div>
                <div class="qa-sub">Browse verified veterinarians</div>
              </div>
            </a>
            @if (auth.isAdmin()) {
              <a routerLink="/admin/vets" class="quick-action qa-danger">
                <span class="qa-icon">🛡️</span>
                <div>
                  <div class="qa-title">Pending Verifications</div>
                  <div class="qa-sub">{{ stats?.['pendingVets'] ?? 0 }} vets awaiting review</div>
                </div>
              </a>
            }
          </div>
        </div>
      </div>

      <!-- Assigned patients (vet only) -->
      @if (auth.currentUser()?.role === 'VETERINARIAN') {
        <div class="card" style="margin-top:24px">
          <div class="card-header">
            <span class="card-title">🩺 My Patients</span>
            <span class="badge badge-pending" style="font-size:12px">{{ assignedPets.length }} pet{{ assignedPets.length !== 1 ? 's' : '' }}</span>
          </div>

          @if (assignedPets.length === 0) {
            <div class="empty-state">
              <div class="empty-icon">🐾</div>
              <div class="empty-title">No pets assigned to you yet</div>
              <div class="empty-sub">Pet owners will assign their pets when they choose you as their vet.</div>
            </div>
          }

          @if (assignedPets.length > 0) {
            <div class="patients-grid">
              @for (pet of assignedPets; track pet.id) {
                <div class="patient-card">
                  <div class="patient-emoji">{{ speciesEmoji(pet.species) }}</div>
                  <div class="patient-name">{{ pet.name }}</div>
                  <div class="patient-meta">{{ pet.breed || pet.species }}</div>
                  <div class="patient-owner">👤 {{ pet.ownerName }}</div>
                  <div style="margin-top:8px">
                    @if (pet.isVaccinated) {
                      <span class="badge badge-verified">Vaccinated ✓</span>
                    } @else {
                      <span class="badge badge-pending">Not vaccinated</span>
                    }
                  </div>
                </div>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .quick-action {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px;
      border-radius: 12px;
      background: var(--neutral);
      cursor: pointer;
      transition: background 0.15s, transform 0.1s;
      text-decoration: none;
      color: inherit;

      &:hover { background: var(--primary-light); transform: translateX(4px); }
      &.qa-danger:hover { background: var(--danger-light); }
    }
    .qa-icon  { font-size: 28px; flex-shrink: 0; }
    .qa-title { font-weight: 600; font-size: 14px; }
    .qa-sub   { font-size: 12px; color: var(--text-soft); margin-top: 2px; }

    .patients-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 12px;
      margin-top: 4px;
    }

    .patient-card {
      background: var(--neutral, #f5f6fa);
      border-radius: 12px;
      padding: 16px;
      text-align: center;
    }

    .patient-emoji { font-size: 36px; margin-bottom: 6px; }
    .patient-name  { font-weight: 700; font-size: 14px; }
    .patient-meta  { font-size: 12px; color: var(--text-soft); }
    .patient-owner { font-size: 12px; color: var(--text-soft); margin-top: 4px; }
  `]
})
export class DashboardComponent implements OnInit {
  stats: Record<string, number> | null = null;
  notifications: Notification[] = [];
  petCount    = 0;
  unreadCount = 0;
  assignedPets: Pet[] = [];

  constructor(
    public auth: AuthService,
    private adminSvc: AdminService,
    private userSvc:  UserService,
    private petSvc:   PetService,
    private vetSvc:   VetService
  ) {}

  ngOnInit(): void {
    if (this.auth.isAdmin()) {
      this.adminSvc.getStats().subscribe(s => this.stats = s);
    } else {
      this.petSvc.getMyPets().subscribe(pets => this.petCount = pets.length);
    }

    if (this.auth.currentUser()?.role === 'VETERINARIAN') {
      this.vetSvc.getMyAssignedPets().subscribe({
        next: pets => this.assignedPets = pets,
        error: () => {}
      });
    }

    this.userSvc.getNotifications().subscribe(n => this.notifications = n);
    this.userSvc.getUnreadCount().subscribe(r => this.unreadCount = r.count);
  }

  notifIcon(type: string): string {
    const icons: Record<string, string> = {
      EMERGENCY: '🚨', WARNING: '⚠️', INFO: '💬', SYSTEM: '🔧', REMINDER: '💉'
    };
    return icons[type] ?? '🔔';
  }

  roleBadge(): string {
    const r = this.auth.currentUser()?.role;
    if (r === 'ADMINISTRATOR') return 'badge badge-admin';
    if (r === 'VETERINARIAN')  return 'badge badge-vet';
    return 'badge badge-owner';
  }

  speciesEmoji(species: string): string {
    const map: Record<string, string> = {
      dog: '🐕', cat: '🐈', rabbit: '🐇', bird: '🦜', fish: '🐠',
      hamster: '🐹', turtle: '🐢', horse: '🐎'
    };
    return map[species?.toLowerCase()] ?? '🐾';
  }
}
