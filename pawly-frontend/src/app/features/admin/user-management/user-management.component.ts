import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../core/services/admin.service';
import { User, UserRole, UserStatus } from '../../../core/models/user.model';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">User Management</div>
          <div class="page-sub">Manage all registered users on the platform</div>
        </div>
      </div>

      <!-- Stats row -->
      <div class="stats-row">
        <div class="stat-card">
          <div class="stat-icon">👤</div>
          <div class="stat-value">{{ stats?.['petOwners'] ?? 0 }}</div>
          <div class="stat-label">Pet Owners</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">🩺</div>
          <div class="stat-value">{{ stats?.['veterinarians'] ?? 0 }}</div>
          <div class="stat-label">Veterinarians</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">✅</div>
          <div class="stat-value">{{ stats?.['activeAccounts'] ?? 0 }}</div>
          <div class="stat-label">Active Accounts</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">⚠️</div>
          <div class="stat-value">{{ stats?.['inactiveAccounts'] ?? 0 }}</div>
          <div class="stat-label">Inactive Accounts</div>
        </div>
      </div>

      <!-- Filters -->
      <div class="filter-bar">
        <input class="search-input form-control" type="text"
          placeholder="🔍  Search users by name or email…"
          [(ngModel)]="searchQuery" (input)="load()" />
        <select class="form-control" style="width:auto" [(ngModel)]="roleFilter" (change)="load()">
          <option value="">All Roles</option>
          <option value="PET_OWNER">Pet Owner</option>
          <option value="VETERINARIAN">Veterinarian</option>
          <option value="ADMINISTRATOR">Administrator</option>
        </select>
        <select class="form-control" style="width:auto" [(ngModel)]="statusFilter" (change)="load()">
          <option value="">All Status</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
          <option value="PENDING">Pending</option>
        </select>
      </div>

      <div class="card">
        @if (loading) {
          <div class="loading-overlay"><div class="spinner"></div></div>
        }

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>User</th>
                <th>Role</th>
                <th>Joined</th>
                <th>Status</th>
                <th>Pets</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (user of users; track user.id) {
                <tr>
                  <td>
                    <div class="user-cell">
                      <div class="avatar" [ngClass]="avatarColor(user)">{{ initials(user) }}</div>
                      <div>
                        <div class="user-name">{{ user.firstName }} {{ user.lastName }}</div>
                        <div class="user-email">{{ user.email }}</div>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span class="badge" [ngClass]="roleBadge(user.role)">{{ user.role }}</span>
                  </td>
                  <td>{{ user.createdAt | date:'mediumDate' }}</td>
                  <td>
                    <span class="badge" [ngClass]="statusBadge(user.status)">{{ user.status }}</span>
                  </td>
                  <td>{{ user.petCount ?? 0 }}</td>
                  <td>
                    <div style="display:flex; gap:6px; flex-wrap:wrap">
                      @if (user.status === 'ACTIVE' || user.status === 'PENDING') {
                        <button class="btn btn-danger btn-xs" (click)="deactivate(user)">Deactivate</button>
                      }
                      @if (user.status === 'INACTIVE') {
                        <button class="btn btn-success btn-xs" (click)="activate(user)">Activate</button>
                      }
                      <button class="btn btn-ghost btn-xs" (click)="confirmDelete(user)">🗑️</button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        @if (totalPages > 1) {
          <div class="pagination">
            <button class="btn btn-ghost btn-sm" [disabled]="page === 0" (click)="prevPage()">← Prev</button>
            <span>Page {{ page + 1 }} of {{ totalPages }}</span>
            <button class="btn btn-ghost btn-sm" [disabled]="page >= totalPages - 1" (click)="nextPage()">Next →</button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .pagination {
      display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 16px;
      color: var(--text-soft); font-size: 13px;
    }
  `]
})
export class UserManagementComponent implements OnInit {
  users:     User[]  = [];
  stats:     Record<string, number> | null = null;
  loading  = true;
  page     = 0;
  size     = 15;
  totalPages = 0;

  searchQuery  = '';
  roleFilter   = '';
  statusFilter = '';

  constructor(private adminSvc: AdminService) {}

  ngOnInit(): void {
    this.adminSvc.getStats().subscribe(s => this.stats = s);
    this.load();
  }

  load(): void {
    this.loading = true;
    this.adminSvc.getUsers(
      (this.roleFilter   as UserRole)   || undefined,
      (this.statusFilter as UserStatus) || undefined,
      this.searchQuery   || undefined,
      this.page,
      this.size
    ).subscribe({
      next: page => {
        this.users      = page.content;
        this.totalPages = page.totalPages;
        this.loading    = false;
      },
      error: () => this.loading = false
    });
  }

  prevPage(): void { this.page--; this.load(); }
  nextPage(): void { this.page++; this.load(); }

  activate(user: User): void {
    this.adminSvc.activateUser(user.id).subscribe(u => this.replaceUser(u));
  }
  deactivate(user: User): void {
    this.adminSvc.deactivateUser(user.id).subscribe(u => this.replaceUser(u));
  }
  confirmDelete(user: User): void {
    if (!confirm(`Permanently delete ${user.firstName} ${user.lastName}? This cannot be undone.`)) return;
    this.adminSvc.deleteUser(user.id).subscribe(() => {
      this.users = this.users.filter(u => u.id !== user.id);
    });
  }

  private replaceUser(updated: User): void {
    const idx = this.users.findIndex(u => u.id === updated.id);
    if (idx !== -1) this.users[idx] = updated;
  }

  initials(u: User): string { return `${u.firstName[0]}${u.lastName[0]}`; }

  avatarColor(u: User): string {
    const colors = ['av-purple', 'av-blue', 'av-green', 'av-orange', 'av-red'];
    return colors[u.id % colors.length];
  }

  roleBadge(r: UserRole): string {
    const m: Record<string, string> = { ADMINISTRATOR: 'badge badge-admin', VETERINARIAN: 'badge badge-vet', PET_OWNER: 'badge badge-owner' };
    return m[r] ?? 'badge';
  }
  statusBadge(s: UserStatus): string {
    const m: Record<string, string> = { ACTIVE: 'badge badge-active', INACTIVE: 'badge badge-inactive', PENDING: 'badge badge-pending' };
    return m[s] ?? 'badge';
  }
}
