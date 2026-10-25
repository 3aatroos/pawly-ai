import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PetService } from '../../../core/services/pet.service';
import { Pet } from '../../../core/models/pet.model';
import { VetPickerComponent } from '../vet-picker/vet-picker.component';
import { VetProfile } from '../../../core/models/vet-profile.model';

@Component({
  selector: 'app-pet-list',
  standalone: true,
  imports: [CommonModule, RouterLink, VetPickerComponent],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">My Pets</div>
          <div class="page-sub">Manage all your registered pets</div>
        </div>
        <div class="header-actions">
          <a routerLink="/pets/new" class="btn btn-primary btn-sm">+ Add Pet</a>
        </div>
      </div>

      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      }

      @if (!loading) {
        @if (pets.length === 0) {
          <div class="card">
            <div class="empty-state">
              <div class="empty-icon">🐾</div>
              <div class="empty-title">No pets registered yet</div>
              <div class="empty-sub">Add your first pet to get started</div>
              <a routerLink="/pets/new" class="btn btn-primary" style="margin-top:16px">+ Add your first pet</a>
            </div>
          </div>
        }

        <div class="pets-grid">
          @for (pet of pets; track pet.id) {
            <div class="pet-card">
              <div class="pet-emoji">{{ speciesEmoji(pet.species) }}</div>
              <div class="pet-name">{{ pet.name }}</div>
              <div class="pet-breed">{{ pet.breed || pet.species }}</div>
              <div class="pet-age">{{ petAge(pet.dateOfBirth) }} · {{ pet.gender }}</div>
              <div class="pet-tags">
                @if (pet.isVaccinated) {
                  <span class="badge badge-verified">Vaccinated ✓</span>
                }
                @if (!pet.isVaccinated) {
                  <span class="badge badge-pending">Not vaccinated</span>
                }
              </div>
              <div class="pet-vet">
                @if (pet.assignedVetName) {
                  <span class="vet-chip">
                    🩺 {{ pet.assignedVetName }}
                    <button class="vet-chip-remove" (click)="removeVet(pet)" title="Remove vet">✕</button>
                  </span>
                } @else {
                  <button class="btn btn-ghost btn-sm vet-assign-btn" (click)="openPicker(pet.id!)">🩺 Assign Vet</button>
                }
              </div>
              <div class="pet-actions">
                <a [routerLink]="['/pets', pet.id, 'edit']" class="btn btn-ghost btn-sm" style="flex:1">✏️ Edit</a>
                <button class="btn btn-danger btn-sm" (click)="deletePet(pet)">🗑️</button>
              </div>
            </div>
          }

          <!-- Add new pet card -->
          <a routerLink="/pets/new" class="pet-card pet-card-add">
            <div style="font-size:36px; color:var(--text-soft)">+</div>
            <div style="font-size:14px; color:var(--text-soft); margin-top:8px">Add a pet</div>
          </a>
        </div>
      }

      @if (pickerOpenForPetId !== null) {
        <app-vet-picker
          [petId]="pickerOpenForPetId"
          [currentVetId]="getPetById(pickerOpenForPetId)?.assignedVetId"
          (vetSelected)="onVetSelected($event)"
          (closed)="pickerOpenForPetId = null"
        ></app-vet-picker>
      }
    </div>
  `,
  styles: [`
    .pets-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
    }

    .pet-card {
      background: var(--white);
      border-radius: 14px;
      padding: 20px;
      text-align: center;
      cursor: pointer;
      transition: transform 0.15s, box-shadow 0.15s;
      border: 2px solid transparent;
      box-shadow: var(--shadow-sm);

      &:hover { transform: translateY(-3px); box-shadow: var(--shadow); border-color: var(--primary); }
    }

    .pet-card-add {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 200px;
      border: 2px dashed var(--border) !important;
      background: transparent !important;
      text-decoration: none;
      box-shadow: none !important;

      &:hover { border-color: var(--primary) !important; background: var(--primary-light) !important; }
    }

    .pet-emoji   { font-size: 48px; margin-bottom: 8px; }
    .pet-name    { font-weight: 700; font-size: 16px; }
    .pet-breed   { font-size: 12px; color: var(--text-soft); }
    .pet-age     { font-size: 12px; color: var(--text-soft); margin-top: 4px; }
    .pet-tags    { margin-top: 10px; display: flex; gap: 6px; justify-content: center; flex-wrap: wrap; }
    .pet-actions { display: flex; gap: 6px; margin-top: 12px; }

    .pet-vet {
      margin-top: 10px;
      display: flex;
      justify-content: center;
    }

    .vet-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      background: var(--primary-light, #e8eeff);
      color: var(--primary);
      border-radius: 20px;
      font-size: 12px;
      font-weight: 600;
    }

    .vet-chip-remove {
      background: none;
      border: none;
      cursor: pointer;
      color: var(--primary);
      padding: 0;
      font-size: 11px;
      line-height: 1;
      opacity: 0.7;
      &:hover { opacity: 1; }
    }

    .vet-assign-btn {
      font-size: 12px;
    }
  `]
})
export class PetListComponent implements OnInit {
  pets: Pet[]  = [];
  loading = true;
  pickerOpenForPetId: number | null = null;

  constructor(private petSvc: PetService) {}

  ngOnInit(): void {
    this.petSvc.getMyPets().subscribe({
      next: pets => { this.pets = pets; this.loading = false; },
      error: ()  => this.loading = false
    });
  }

  speciesEmoji(species: string): string {
    const map: Record<string, string> = {
      dog: '🐕', cat: '🐈', rabbit: '🐇', bird: '🦜', fish: '🐠',
      hamster: '🐹', turtle: '🐢', horse: '🐎'
    };
    return map[species?.toLowerCase()] ?? '🐾';
  }

  petAge(dob?: string): string {
    if (!dob) return 'Unknown age';
    const years = Math.floor((Date.now() - new Date(dob).getTime()) / (365.25 * 24 * 3600 * 1000));
    return years <= 0 ? '< 1 year' : `${years} year${years > 1 ? 's' : ''}`;
  }

  deletePet(pet: Pet): void {
    if (!confirm(`Delete ${pet.name}? This cannot be undone.`)) return;
    this.petSvc.delete(pet.id!).subscribe(() => {
      this.pets = this.pets.filter(p => p.id !== pet.id);
    });
  }

  openPicker(petId: number): void {
    this.pickerOpenForPetId = petId;
  }

  getPetById(petId: number): Pet | undefined {
    return this.pets.find(p => p.id === petId);
  }

  onVetSelected(vet: VetProfile): void {
    // The VetPickerComponent has already called assignVet on the API.
    // Reload the pet list to get the fresh assignedVetName from the server.
    this.petSvc.getMyPets().subscribe({
      next: pets => this.pets = pets,
      error: () => {}
    });
    this.pickerOpenForPetId = null;
  }

  removeVet(pet: Pet): void {
    if (!confirm(`Remove the assigned vet from ${pet.name}?`)) return;
    this.petSvc.removeVet(pet.id!).subscribe({
      next: updated => {
        const idx = this.pets.findIndex(p => p.id === updated.id);
        if (idx !== -1) this.pets[idx] = updated;
      },
      error: () => alert('Failed to remove vet. Please try again.')
    });
  }
}
