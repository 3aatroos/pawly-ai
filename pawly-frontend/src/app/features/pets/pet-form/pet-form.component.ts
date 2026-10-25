import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PetService } from '../../../core/services/pet.service';

@Component({
  selector: 'app-pet-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">{{ isEdit ? 'Edit Pet' : 'Add a Pet' }}</div>
          <div class="page-sub">{{ isEdit ? 'Update your pet\'s information' : 'Register a new pet to your profile' }}</div>
        </div>
        <a routerLink="/pets" class="btn btn-ghost btn-sm">← Back to Pets</a>
      </div>

      <div class="card" style="max-width: 600px">
        @if (error) { <div class="alert alert-danger">{{ error }}</div> }

        <form [formGroup]="form" (ngSubmit)="submit()">
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px">
            <div class="form-group" style="grid-column:1/-1">
              <label>Pet Name *</label>
              <input type="text" formControlName="name" placeholder="e.g. Milo"
                [class.is-invalid]="f['name'].touched && f['name'].invalid" />
              @if (f['name'].touched && f['name'].errors?.['required']) {
                <div class="invalid-feedback">Name is required</div>
              }
            </div>

            <div class="form-group">
              <label>Species *</label>
              <select formControlName="species">
                <option value="">Select species</option>
                <option value="dog">🐕 Dog</option>
                <option value="cat">🐈 Cat</option>
                <option value="rabbit">🐇 Rabbit</option>
                <option value="bird">🦜 Bird</option>
                <option value="fish">🐠 Fish</option>
                <option value="hamster">🐹 Hamster</option>
                <option value="turtle">🐢 Turtle</option>
                <option value="horse">🐎 Horse</option>
                <option value="other">🐾 Other</option>
              </select>
            </div>

            <div class="form-group">
              <label>Breed</label>
              <input type="text" formControlName="breed" placeholder="e.g. Golden Retriever" />
            </div>

            <div class="form-group">
              <label>Date of Birth</label>
              <input type="date" formControlName="dateOfBirth" />
            </div>

            <div class="form-group">
              <label>Gender</label>
              <select formControlName="gender">
                <option value="UNKNOWN">Unknown</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
              </select>
            </div>

            <div class="form-group">
              <label>Weight (kg)</label>
              <input type="number" formControlName="weightKg" placeholder="e.g. 12.5" step="0.1" />
            </div>

            <div class="form-group">
              <label>Color</label>
              <input type="text" formControlName="color" placeholder="e.g. Golden" />
            </div>

            <div class="form-group" style="grid-column:1/-1">
              <label>Health Information</label>
              <textarea formControlName="healthInfo" rows="3"
                placeholder="Allergies, medications, medical history…"></textarea>
            </div>

            <div class="form-group">
              <label>Microchip ID</label>
              <input type="text" formControlName="microchipId" placeholder="Optional" />
            </div>

            <div class="form-group" style="display:flex; align-items:center; gap:10px; padding-top:20px">
              <input type="checkbox" formControlName="isVaccinated" id="vaccinated" style="width:auto" />
              <label for="vaccinated" style="margin:0; text-transform:none; font-size:14px; font-weight:500; color:var(--text)">
                Pet is vaccinated
              </label>
            </div>
          </div>

          <div style="display:flex; gap:10px; margin-top:16px">
            <button type="submit" class="btn btn-primary" [disabled]="saving">
              @if (saving) { <span class="spinner"></span> }
              {{ isEdit ? 'Save Changes' : 'Add Pet' }}
            </button>
            <a routerLink="/pets" class="btn btn-ghost">Cancel</a>
          </div>
        </form>
      </div>
    </div>
  `
})
export class PetFormComponent implements OnInit {
  form = this.fb.group({
    name:        ['', Validators.required],
    species:     ['', Validators.required],
    breed:       [''],
    dateOfBirth: [''],
    gender:      ['UNKNOWN'],
    healthInfo:  [''],
    weightKg:    [null as number | null],
    color:       [''],
    microchipId: [''],
    isVaccinated:[false]
  });

  isEdit = false;
  petId: number | null = null;
  saving = false;
  error  = '';

  constructor(
    private fb:     FormBuilder,
    private petSvc: PetService,
    private route:  ActivatedRoute,
    private router: Router
  ) {}

  get f() { return this.form.controls; }

  ngOnInit(): void {
    this.petId = this.route.snapshot.params['id'] ? +this.route.snapshot.params['id'] : null;
    this.isEdit = !!this.petId;

    if (this.isEdit && this.petId) {
      this.petSvc.getById(this.petId).subscribe(pet => {
        this.form.patchValue({
          ...pet,
          dateOfBirth: pet.dateOfBirth ? pet.dateOfBirth.substring(0, 10) : ''
        } as any);
      });
    }
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving = true;
    this.error  = '';

    const data = this.form.value as any;
    const obs  = this.isEdit && this.petId
      ? this.petSvc.update(this.petId, data)
      : this.petSvc.create(data);

    obs.subscribe({
      next: () => this.router.navigate(['/pets']),
      error: err => {
        this.saving = false;
        this.error = err.error?.message ?? 'Failed to save pet';
      }
    });
  }
}
