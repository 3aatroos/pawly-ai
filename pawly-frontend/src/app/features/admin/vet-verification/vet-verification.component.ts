import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../core/services/admin.service';
import { VetProfile } from '../../../core/models/vet-profile.model';

@Component({
  selector: 'app-vet-verification',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">Vet Verification</div>
          <div class="page-sub">Review and verify veterinarian profiles</div>
        </div>
        <div style="display:flex; gap:10px; align-items:center">
          <span class="badge badge-pending">{{ pendingCount }} pending</span>
        </div>
      </div>

      <!-- Filter tabs -->
      <div class="tabs">
        @for (tab of tabs; track tab.value) {
          <button class="tab-btn" [class.active]="statusFilter === tab.value" (click)="setTab(tab.value)">
            {{ tab.label }}
          </button>
        }
      </div>

      <div class="filter-bar" style="margin-top:12px">
        <input class="search-input form-control" type="text"
          placeholder="🔍  Search by name, specialization, city…"
          [(ngModel)]="searchQuery" (input)="load()" />
      </div>

      <div class="card">
        @if (loading) {
          <div class="loading-overlay"><div class="spinner"></div></div>
        }

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Veterinarian</th>
                <th>Specialization</th>
                <th>Clinic / City</th>
                <th>Experience</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (vet of vets; track vet.id) {
                <tr>
                  <td>
                    <div class="user-cell">
                      <div class="avatar av-blue">{{ initials(vet) }}</div>
                      <div>
                        <div class="user-name">Dr. {{ vet.userFirstName }} {{ vet.userLastName }}</div>
                        <div class="user-email">{{ vet.userEmail }}</div>
                      </div>
                    </div>
                  </td>
                  <td>{{ vet.specialization }}</td>
                  <td>{{ vet.clinicName || '—' }} @if(vet.city) { · {{ vet.city }} }</td>
                  <td>{{ vet.experienceYears }} yrs</td>
                  <td>
                    <span class="badge" [ngClass]="statusBadge(vet.verificationStatus)">
                      {{ vet.verificationStatus }}
                    </span>
                  </td>
                  <td>
                    <div style="display:flex; gap:6px; flex-wrap:wrap">
                      @if (vet.verificationStatus === 'PENDING') {
                        <button class="btn btn-primary btn-xs" (click)="verify(vet)">✓ Verify</button>
                        <button class="btn btn-danger btn-xs" (click)="promptReject(vet)">✗ Reject</button>
                      }
                      @if (vet.verificationStatus === 'REJECTED') {
                        <button class="btn btn-primary btn-xs" (click)="verify(vet)">Re-verify</button>
                      }
                      @if (vet.verificationStatus === 'VERIFIED') {
                        <button class="btn btn-danger btn-xs" (click)="promptReject(vet)">Revoke</button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        @if (totalPages > 1) {
          <div class="pagination">
            <button class="btn btn-ghost btn-sm" [disabled]="page === 0" (click)="prevPage()">← Prev</button>
            <span>Page {{ page + 1 }} of {{ totalPages }}</span>
            <button class="btn btn-ghost btn-sm" [disabled]="page >= totalPages - 1" (click)="nextPage()">Next →</button>
          </div>
        }
      </div>
    </div>

    <!-- Rejection modal -->
    @if (rejectingVet) {
      <div class="modal-overlay">
        <div class="modal-box">
          <h3 style="margin-bottom:16px">Reject / Revoke Vet Profile</h3>
          <p style="font-size:13px; color:var(--text-soft); margin-bottom:16px">
            Dr. {{ rejectingVet.userFirstName }} {{ rejectingVet.userLastName }} — {{ rejectingVet.specialization }}
          </p>
          <div class="form-group">
            <label>Reason (optional)</label>
            <textarea [(ngModel)]="rejectReason" rows="3" class="form-control"
              placeholder="Explain why the profile is being rejected…"></textarea>
          </div>
          <div style="display:flex; gap:10px; margin-top:16px">
            <button class="btn btn-danger" (click)="confirmReject()">Reject</button>
            <button class="btn btn-ghost" (click)="rejectingVet = null; rejectReason = ''">Cancel</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .tabs {
      display: flex; gap: 4px; border-bottom: 2px solid var(--border); margin-bottom: 4px;
    }
    .tab-btn {
      padding: 8px 16px; border: none; background: none; cursor: pointer; font-size: 14px;
      font-weight: 500; color: var(--text-soft); border-bottom: 2px solid transparent;
      margin-bottom: -2px; transition: color 0.15s;

      &:hover { color: var(--primary); }
      &.active { color: var(--primary); font-weight: 700; border-bottom-color: var(--primary); }
    }

    .pagination {
      display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 16px;
      color: var(--text-soft); font-size: 13px;
    }

    .modal-overlay {
      position: fixed; inset: 0; background: rgba(0,0,0,.5); z-index: 100;
      display: flex; align-items: center; justify-content: center;
    }
    .modal-box {
      background: #fff; border-radius: 16px; padding: 28px;
      width: 400px; max-width: 90vw; box-shadow: 0 20px 60px rgba(0,0,0,.2);
    }
  `]
})
export class VetVerificationComponent implements OnInit {
  vets:         VetProfile[] = [];
  loading     = true;
  page        = 0;
  size        = 15;
  totalPages  = 0;
  pendingCount = 0;

  searchQuery  = '';
  statusFilter = 'PENDING';

  rejectingVet: VetProfile | null = null;
  rejectReason = '';

  tabs = [
    { label: '⏳ Pending',  value: 'PENDING'  },
    { label: '✅ Verified', value: 'VERIFIED' },
    { label: '❌ Rejected', value: 'REJECTED' },
    { label: 'All',         value: ''          }
  ];

  constructor(private adminSvc: AdminService) {}

  ngOnInit(): void {
    this.load();
    this.adminSvc.getVets('PENDING', undefined, 0, 999)
      .subscribe(p => this.pendingCount = p.totalElements);
  }

  setTab(val: string): void { this.statusFilter = val; this.page = 0; this.load(); }

  load(): void {
    this.loading = true;
    this.adminSvc.getVets(
      (this.statusFilter as any) || undefined,
      this.searchQuery || undefined,
      this.page, this.size
    ).subscribe({
      next: p => {
        this.vets       = p.content;
        this.totalPages = p.totalPages;
        this.loading    = false;
      },
      error: () => this.loading = false
    });
  }

  prevPage(): void { this.page--; this.load(); }
  nextPage(): void { this.page++; this.load(); }

  verify(vet: VetProfile): void {
    this.adminSvc.verifyVet(vet.id!).subscribe(updated => this.replaceVet(updated));
  }

  promptReject(vet: VetProfile): void { this.rejectingVet = vet; }

  confirmReject(): void {
    if (!this.rejectingVet) return;
    this.adminSvc.rejectVet(this.rejectingVet.id!, this.rejectReason).subscribe(updated => {
      this.replaceVet(updated);
      this.rejectingVet = null;
      this.rejectReason = '';
    });
  }

  private replaceVet(updated: VetProfile): void {
    const idx = this.vets.findIndex(v => v.id === updated.id);
    if (idx !== -1) this.vets[idx] = updated;
  }

  initials(v: VetProfile): string {
    return `${v.userFirstName?.[0] ?? ''}${v.userLastName?.[0] ?? ''}`;
  }

  statusBadge(s?: string): string {
    const m: Record<string, string> = { VERIFIED: 'badge badge-verified', PENDING: 'badge badge-pending', REJECTED: 'badge badge-rejected' };
    return m[s ?? ''] ?? 'badge';
  }
}
