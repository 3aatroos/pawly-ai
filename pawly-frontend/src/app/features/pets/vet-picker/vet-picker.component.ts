import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VetService } from '../../../core/services/vet.service';
import { PetService } from '../../../core/services/pet.service';
import { VetProfile } from '../../../core/models/vet-profile.model';

@Component({
  selector: 'app-vet-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- Backdrop -->
    <div class="picker-backdrop" (click)="onBackdropClick()"></div>

    <!-- Modal -->
    <div class="picker-modal fade-up">
      <!-- Header -->
      <div class="picker-header">
        <div>
          <div class="picker-title">Select a Veterinarian</div>
          <div class="picker-sub">Showing verified & available vets</div>
        </div>
        <button class="btn-close" (click)="closed.emit()">✕</button>
      </div>

      <!-- Search -->
      <div class="picker-search">
        <input
          type="text"
          class="search-input"
          placeholder="Search by name, clinic or city…"
          [(ngModel)]="searchTerm"
          (input)="search()"
        />
      </div>

      <!-- Body -->
      <div class="picker-body">
        @if (loading) {
          <div class="loading-overlay" style="position:relative; height:120px">
            <div class="spinner"></div>
          </div>
        }

        @if (!loading && vets.length === 0) {
          <div class="empty-state" style="padding:32px 0">
            <div class="empty-icon">🩺</div>
            <div class="empty-title">No verified vets found</div>
            <div class="empty-sub">Try a different search term</div>
          </div>
        }

        @if (!loading && vets.length > 0) {
          <div class="vet-list">
            @for (vet of vets; track vet.id) {
              <div class="vet-row" [class.vet-row-current]="vet.id === currentVetId">
                <div class="vet-avatar">
                  @if (vet.userPhotoUrl) {
                    <img [src]="vet.userPhotoUrl" [alt]="vet.userFirstName" />
                  } @else {
                    <span>{{ initials(vet) }}</span>
                  }
                </div>

                <div class="vet-info">
                  <div class="vet-name">
                    Dr. {{ vet.userFirstName }} {{ vet.userLastName }}
                    @if (vet.id === currentVetId) {
                      <span class="badge badge-verified" style="margin-left:8px">Current vet ✓</span>
                    }
                  </div>
                  <div class="vet-meta">
                    <span class="meta-item">🏥 {{ vet.specialization }}</span>
                    @if (vet.clinicName) {
                      <span class="meta-sep">·</span>
                      <span class="meta-item">{{ vet.clinicName }}</span>
                    }
                    @if (vet.city) {
                      <span class="meta-sep">·</span>
                      <span class="meta-item">📍 {{ vet.city }}</span>
                    }
                    @if (vet.experienceYears) {
                      <span class="meta-sep">·</span>
                      <span class="meta-item">{{ vet.experienceYears }} yrs exp.</span>
                    }
                  </div>
                </div>

                <div class="vet-action">
                  @if (assigningVetId === vet.id) {
                    <div class="spinner" style="width:20px;height:20px"></div>
                  } @else {
                    <button
                      class="btn btn-primary btn-sm"
                      [disabled]="!!assigningVetId"
                      (click)="selectVet(vet)"
                    >
                      {{ vet.id === currentVetId ? 'Keep' : 'Select' }}
                    </button>
                  }
                </div>
              </div>
            }
          </div>
        }

        <!-- Assignment error -->
        @if (error) {
          <div class="alert-error">{{ error }}</div>
        }
      </div>
    </div>
  `,
  styles: [`
    /* ── Overlay ─────────────────────────────────────────────── */
    .picker-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.45);
      z-index: 900;
    }

    /* ── Modal shell ─────────────────────────────────────────── */
    .picker-modal {
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      z-index: 901;
      background: var(--white);
      border-radius: 18px;
      box-shadow: var(--shadow);
      width: min(640px, calc(100vw - 32px));
      max-height: min(680px, calc(100vh - 48px));
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    /* ── Header ──────────────────────────────────────────────── */
    .picker-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding: 20px 24px 12px;
      border-bottom: 1px solid var(--border);
    }

    .picker-title {
      font-size: 18px;
      font-weight: 700;
      color: var(--text);
    }

    .picker-sub {
      font-size: 13px;
      color: var(--text-soft);
      margin-top: 2px;
    }

    .btn-close {
      background: none;
      border: none;
      font-size: 18px;
      cursor: pointer;
      color: var(--text-soft);
      padding: 4px 8px;
      border-radius: 8px;
      line-height: 1;
      transition: background 0.15s;

      &:hover { background: var(--primary-light); color: var(--primary); }
    }

    /* ── Search bar ──────────────────────────────────────────── */
    .picker-search {
      padding: 12px 24px;
      border-bottom: 1px solid var(--border);
    }

    .search-input {
      width: 100%;
      padding: 9px 14px;
      border: 1.5px solid var(--border);
      border-radius: 10px;
      font-size: 14px;
      background: var(--bg, #f8f9fb);
      color: var(--text);
      transition: border-color 0.15s;
      box-sizing: border-box;

      &:focus { outline: none; border-color: var(--primary); }
    }

    /* ── Scrollable body ─────────────────────────────────────── */
    .picker-body {
      overflow-y: auto;
      flex: 1;
      padding: 8px 0;
    }

    /* ── Vet list rows ───────────────────────────────────────── */
    .vet-list {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 0 12px;
    }

    .vet-row {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 12px 14px;
      border-radius: 12px;
      transition: background 0.12s;

      &:hover { background: var(--primary-light, #f0f4ff); }
    }

    .vet-row-current {
      background: var(--primary-light, #f0f4ff);
      border: 1.5px solid var(--primary);
    }

    /* Avatar circle */
    .vet-avatar {
      flex-shrink: 0;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: var(--primary-light, #e8eeff);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 15px;
      color: var(--primary);
      overflow: hidden;

      img { width: 100%; height: 100%; object-fit: cover; }
    }

    /* Info block */
    .vet-info {
      flex: 1;
      min-width: 0;
    }

    .vet-name {
      font-weight: 600;
      font-size: 14px;
      color: var(--text);
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 4px;
    }

    .vet-meta {
      margin-top: 4px;
      font-size: 12px;
      color: var(--text-soft);
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px;
    }

    .meta-sep { opacity: 0.4; }

    /* Action column */
    .vet-action {
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }

    /* ── Error alert ─────────────────────────────────────────── */
    .alert-error {
      margin: 8px 24px 12px;
      padding: 10px 14px;
      background: #fef2f2;
      color: #b91c1c;
      border: 1px solid #fecaca;
      border-radius: 10px;
      font-size: 13px;
    }
  `]
})
export class VetPickerComponent implements OnInit {
  @Input() petId!: number;
  @Input() currentVetId?: number;

  @Output() vetSelected = new EventEmitter<VetProfile>();
  @Output() closed = new EventEmitter<void>();

  vets: VetProfile[] = [];
  loading = false;
  error = '';
  searchTerm = '';
  assigningVetId: number | null = null;

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private vetService: VetService,
    private petService: PetService
  ) {}

  ngOnInit(): void {
    this.loadVets();
  }

  search(): void {
    // Debounce with 300 ms so we don't hammer the API on every keystroke
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.loadVets(), 300);
  }

  private loadVets(): void {
    this.loading = true;
    this.error = '';
    const term = this.searchTerm.trim() || undefined;
    this.vetService.getAll('VERIFIED', term, true).subscribe({
      next: page => {
        this.vets = page.content ?? [];
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load veterinarians. Please try again.';
        this.loading = false;
      }
    });
  }

  selectVet(vet: VetProfile): void {
    if (!vet.id) return;
    this.assigningVetId = vet.id;
    this.error = '';
    this.petService.assignVet(this.petId, vet.id).subscribe({
      next: updatedPet => {
        this.assigningVetId = null;
        this.vetSelected.emit(vet);
        this.closed.emit();
      },
      error: err => {
        this.assigningVetId = null;
        this.error = err?.error?.error ?? err?.error?.message ?? 'Failed to assign vet. Please try again.';
      }
    });
  }

  onBackdropClick(): void {
    // Only close if no assignment is in progress
    if (!this.assigningVetId) {
      this.closed.emit();
    }
  }

  initials(vet: VetProfile): string {
    const first = vet.userFirstName?.[0] ?? '';
    const last  = vet.userLastName?.[0] ?? '';
    return (first + last).toUpperCase() || '?';
  }
}
