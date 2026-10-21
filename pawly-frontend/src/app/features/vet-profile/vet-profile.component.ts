import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { VetService } from '../../core/services/vet.service';
import { VetProfile } from '../../core/models/vet-profile.model';

@Component({
  selector: 'app-vet-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="fade-up">
      <!-- Browse mode (non-vets) -->
      @if (!auth.isVet()) {
        <div class="page-header">
          <div>
            <div class="page-title">Veterinarians</div>
            <div class="page-sub">Browse verified veterinary professionals</div>
          </div>
        </div>

        <div class="filter-bar">
          <input class="search-input form-control" type="text"
            placeholder="🔍  Search by name, specialization, city…"
            (input)="onSearch($event)" />
          <select class="form-control" style="width:auto" (change)="onStatusFilter($event)">
            <option value="">All Status</option>
            <option value="VERIFIED">Verified</option>
            <option value="PENDING">Pending</option>
          </select>
        </div>

        @if (loading) { <div class="loading-overlay"><div class="spinner"></div></div> }

        <div class="card">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Veterinarian</th>
                  <th>Specialization</th>
                  <th>Clinic</th>
                  <th>City</th>
                  <th>Status</th>
                  <th>Available</th>
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
                    <td>{{ vet.clinicName || '—' }}</td>
                    <td>{{ vet.city || '—' }}</td>
                    <td>
                      <span class="badge" [ngClass]="statusBadge(vet.verificationStatus)">
                        {{ vet.verificationStatus }}
                      </span>
                    </td>
                    <td>
                      @if (vet.isAvailable) {
                        <span class="badge badge-active">✓ Available</span>
                      } @else {
                        <span class="badge badge-inactive">Unavailable</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }

    <!-- Vet Detail Modal -->
    @if (selectedVet) {
      <div class="modal-overlay" (click)="closeModal()">
        <div class="modal-box" (click)="$event.stopPropagation()">
          <button class="modal-close" (click)="closeModal()">?</button>

          <!-- Vet Hero -->
          <div class="vet-modal-hero">
            <div class="vet-modal-avatar">{{ initials(selectedVet) }}</div>
            <div>
              <div class="vet-modal-name">Dr. {{ selectedVet.userFirstName }} {{ selectedVet.userLastName }}</div>
              <div class="vet-modal-spec">{{ selectedVet.specialization }}</div>
              <div style="margin-top:6px; display:flex; gap:6px; flex-wrap:wrap">
                <span class="badge" [ngClass]="statusBadge(selectedVet.verificationStatus)">
                  {{ selectedVet.verificationStatus }}
                </span>
                @if (selectedVet.isAvailable) {
                  <span class="badge badge-active">? Available</span>
                } @else {
                  <span class="badge badge-inactive">Unavailable</span>
                }
              </div>
            </div>
          </div>

          <!-- Details -->
          <div class="vet-modal-details">
            <div class="detail-row">
              <span class="detail-label">?? Clinic</span>
              <span class="detail-value">{{ selectedVet.clinicName || 'Not specified' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">?? Address</span>
              <span class="detail-value">{{ selectedVet.clinicAddress || 'Not specified' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">??? City</span>
              <span class="detail-value">{{ selectedVet.city || 'Not specified' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">? Working Hours</span>
              <span class="detail-value">{{ selectedVet.workingHours || 'Not specified' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">?? Experience</span>
              <span class="detail-value">{{ selectedVet.experienceYears }} years</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">?? License #</span>
              <span class="detail-value">{{ selectedVet.licenseNumber || 'Not specified' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">?? Services</span>
              <span class="detail-value">{{ selectedVet.services || 'Not specified' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">?? Phone</span>
              <span class="detail-value">{{ selectedVet.userPhone || 'Not available' }}</span>
            </div>
            @if (selectedVet.bio) {
              <div class="detail-row" style="flex-direction:column; align-items:flex-start">
                <span class="detail-label">?? Bio</span>
                <span class="detail-value" style="margin-top:6px; line-height:1.6">{{ selectedVet.bio }}</span>
              </div>
            }
          </div>

          <!-- Actions -->
          <div style="display:flex; gap:10px; margin-top:20px">
            @if (selectedVet.verificationStatus === 'VERIFIED' && selectedVet.isAvailable) {
              <button class="btn btn-primary" style="flex:1" (click)="selectVet(selectedVet)">
                ? Select This Vet
              </button>
            }
            <button class="btn btn-ghost" (click)="closeModal()">Close</button>
          </div>

          @if (selectedVet.verificationStatus !== 'VERIFIED') {
            <div class="alert alert-warning" style="margin-top:12px">
              ?? This vet is not yet verified. You can view their profile but cannot select them.
            </div>
          }
        </div>
      </div>
    }

    <!-- Selection confirmation -->
    @if (selectedVetConfirmed) {
      <div class="alert alert-success" style="position:fixed; top:20px; right:20px; z-index:200; max-width:320px">
        ? Dr. {{ selectedVetConfirmed.userFirstName }} {{ selectedVetConfirmed.userLastName }} has been selected as your preferred vet!
      </div>
    }

      <!-- Vet's own profile -->
      @if (auth.isVet()) {
        <div class="page-header">
          <div>
            <div class="page-title">My Vet Profile</div>
            <div class="page-sub">Manage your professional information</div>
          </div>
          @if (!editing) {
            <button class="btn btn-primary btn-sm" (click)="startEdit()">✏️ Edit Profile</button>
          }
        </div>

        @if (myProfile) {
          <div class="vet-hero">
            <div class="vet-hero-avatar">
              {{ initials(myProfile) }}
            </div>
            <div>
              <div class="vet-hero-name">Dr. {{ myProfile.userFirstName }} {{ myProfile.userLastName }}</div>
              <div class="vet-hero-spec">{{ myProfile.specialization }}</div>
              <div class="vet-hero-info">
                @if (myProfile.clinicName) { 🏥 {{ myProfile.clinicName }} · }
                @if (myProfile.city) { 📍 {{ myProfile.city }} }
              </div>
              <div style="margin-top:8px; display:flex; gap:8px">
                <span class="badge" [ngClass]="statusBadge(myProfile.verificationStatus)">
                  {{ myProfile.verificationStatus }}
                </span>
                @if (myProfile.isAvailable) {
                  <span class="badge badge-active">Available</span>
                }
              </div>
            </div>
          </div>

          @if (myProfile.verificationStatus === 'PENDING') {
            <div class="alert alert-warning">
              ⏳ Your profile is pending admin verification. You'll be notified once reviewed.
            </div>
          }
          @if (myProfile.verificationStatus === 'REJECTED') {
            <div class="alert alert-danger">
              ❌ Your profile was rejected. Reason: {{ myProfile.rejectionReason || 'No reason provided' }}
            </div>
          }

          @if (!editing) {
            <div class="two-col">
              <div class="card">
                <div class="card-header"><span class="card-title">Professional Details</span></div>
                <div class="info-grid">
                  <div class="info-item"><label>Specialization</label><span>{{ myProfile.specialization }}</span></div>
                  <div class="info-item"><label>Experience</label><span>{{ myProfile.experienceYears }} years</span></div>
                  <div class="info-item"><label>License #</label><span>{{ myProfile.licenseNumber || '—' }}</span></div>
                  <div class="info-item"><label>Working Hours</label><span>{{ myProfile.workingHours || '—' }}</span></div>
                </div>
              </div>
              <div class="card">
                <div class="card-header"><span class="card-title">Clinic Information</span></div>
                <div class="info-grid">
                  <div class="info-item"><label>Clinic Name</label><span>{{ myProfile.clinicName || '—' }}</span></div>
                  <div class="info-item"><label>City</label><span>{{ myProfile.city || '—' }}</span></div>
                  <div class="info-item" style="grid-column:1/-1"><label>Address</label><span>{{ myProfile.clinicAddress || '—' }}</span></div>
                  <div class="info-item" style="grid-column:1/-1"><label>Services</label><span>{{ myProfile.services || '—' }}</span></div>
                </div>
              </div>
            </div>
          }

          @if (editing) {
            <div class="card" style="max-width:680px">
              <div class="card-header"><span class="card-title">Edit Vet Profile</span></div>
              @if (saveError) { <div class="alert alert-danger">{{ saveError }}</div> }
              <form [formGroup]="vetForm" (ngSubmit)="saveVetProfile()">
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px">
                  <div class="form-group" style="grid-column:1/-1">
                    <label>Specialization *</label>
                    <input type="text" formControlName="specialization" />
                  </div>
                  <div class="form-group">
                    <label>Experience (years)</label>
                    <input type="number" formControlName="experienceYears" />
                  </div>
                  <div class="form-group">
                    <label>License Number</label>
                    <input type="text" formControlName="licenseNumber" />
                  </div>
                  <div class="form-group">
                    <label>Clinic Name</label>
                    <input type="text" formControlName="clinicName" />
                  </div>
                  <div class="form-group">
                    <label>City</label>
                    <input type="text" formControlName="city" />
                  </div>
                  <div class="form-group" style="grid-column:1/-1">
                    <label>Clinic Address</label>
                    <input type="text" formControlName="clinicAddress" />
                  </div>
                  <div class="form-group">
                    <label>Working Hours</label>
                    <input type="text" formControlName="workingHours" placeholder="Mon-Fri 9am-5pm" />
                  </div>
                  <div class="form-group" style="grid-column:1/-1">
                    <label>Services Offered</label>
                    <textarea formControlName="services" rows="2" placeholder="Surgery, Vaccination, Dermatology…"></textarea>
                  </div>
                  <div class="form-group" style="grid-column:1/-1">
                    <label>Bio</label>
                    <textarea formControlName="bio" rows="3"></textarea>
                  </div>
                  <div class="form-group" style="display:flex; align-items:center; gap:10px; padding-top:14px">
                    <input type="checkbox" formControlName="isAvailable" id="avail" style="width:auto" />
                    <label for="avail" style="margin:0; text-transform:none; font-size:14px; font-weight:500; color:var(--text)">Currently available</label>
                  </div>
                </div>
                <div style="display:flex; gap:10px; margin-top:16px">
                  <button type="submit" class="btn btn-primary" [disabled]="saving">
                    @if (saving) { <span class="spinner"></span> } Save Changes
                  </button>
                  <button type="button" class="btn btn-ghost" (click)="cancelEdit()">Cancel</button>
                </div>
              </form>
            </div>
          }
        } @else {
          <div class="loading-overlay"><div class="spinner"></div></div>
        }
      }
    </div>
  `,
  styles: [`
    .vet-hero {
      background: linear-gradient(135deg, var(--primary) 0%, #1E90FF 100%);
      border-radius: var(--radius); padding: 28px; color: #fff;
      display: flex; align-items: center; gap: 20px; margin-bottom: 24px;
    }
    .vet-hero-avatar {
      width: 64px; height: 64px; border-radius: 50%;
      background: rgba(255,255,255,.25);
      display: flex; align-items: center; justify-content: center;
      font-size: 24px; font-weight: 800; flex-shrink: 0;
      border: 2px solid rgba(255,255,255,.4);
    }
    .vet-hero-name { font-size: 20px; font-weight: 800; }
    .vet-hero-spec { font-size: 14px; opacity: 0.85; margin-top: 2px; }
    .vet-hero-info { font-size: 13px; opacity: 0.75; margin-top: 4px; }

    .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
    .info-item {
      label { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.07em; color: var(--text-soft); display: block; margin-bottom: 4px; }
      span  { font-size: 14px; font-weight: 500; }
    }
  `]
})
export class VetProfileComponent implements OnInit {
  vets: VetProfile[] = [];
  myProfile: VetProfile | null = null;
  loading = true;
  editing = false;
  saving  = false;
  saveError = '';

  vetForm = this.fb.group({
    specialization:  [''],
    experienceYears: [0],
    licenseNumber:   [''],
    clinicName:      [''],
    clinicAddress:   [''],
    city:            [''],
    workingHours:    [''],
    services:        [''],
    bio:             [''],
    isAvailable:     [true]
  });

  selectedVet: VetProfile | null = null;
  selectedVetConfirmed: VetProfile | null = null;

  constructor(
    public  auth:   AuthService,
    private vetSvc: VetService,
    private fb:     FormBuilder
  ) {}

  ngOnInit(): void {
    if (this.auth.isVet()) {
      this.vetSvc.getMyProfile().subscribe({
        next: p  => { this.myProfile = p; this.loading = false; },
        error: () => this.loading = false
      });
    } else {
      this.vetSvc.getAll().subscribe({
        next: page => { this.vets = page.content; this.loading = false; },
        error: ()  => this.loading = false
      });
    }
  }

  onSearch(e: Event): void {
    const search = (e.target as HTMLInputElement).value;
    this.vetSvc.getAll(undefined, search).subscribe(p => this.vets = p.content);
  }

  onStatusFilter(e: Event): void {
    const val = (e.target as HTMLSelectElement).value as any;
    this.vetSvc.getAll(val || undefined).subscribe(p => this.vets = p.content);
  }

  initials(v: VetProfile): string {
    return `${v.userFirstName?.[0] ?? ''}${v.userLastName?.[0] ?? ''}`;
  }

  statusBadge(status?: string): string {
    const m: Record<string, string> = { VERIFIED: 'badge badge-verified', PENDING: 'badge badge-pending', REJECTED: 'badge badge-rejected' };
    return m[status ?? ''] ?? 'badge';
  }

  closeModal(): void {
    this.selectedVet = null;
  }

  selectVet(vet: VetProfile): void {
    this.selectedVetConfirmed = vet;
    this.selectedVet = null;
    // Auto-hide confirmation after 3 s
    setTimeout(() => this.selectedVetConfirmed = null, 3000);
  }

  startEdit(): void {
    if (this.myProfile) this.vetForm.patchValue(this.myProfile as any);
    this.editing = true;
  }

  cancelEdit(): void { this.editing = false; this.saveError = ''; }

  saveVetProfile(): void {
    this.saving = true;
    this.saveError = '';
    this.vetSvc.updateMyProfile(this.vetForm.value as any).subscribe({
      next: p  => { this.myProfile = p; this.saving = false; this.editing = false; },
      error: err => { this.saving = false; this.saveError = err.error?.message ?? 'Save failed'; }
    });
  }
}
