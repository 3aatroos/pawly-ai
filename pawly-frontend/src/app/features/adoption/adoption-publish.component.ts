import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AdoptionService } from '../../core/services/adoption.service';
import { AdoptionDraft, PublishAdoptionRequest, apiMessage } from '../../core/models/adoption.model';

@Component({
  selector: 'app-adoption-publish',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="fade-up form-card">
      <div class="page-header">
        <div>
          <div class="page-title">Publier une fiche</div>
          <div class="page-sub">Le texte de l'IA reste dans ce formulaire tant que tu n'enregistres pas.</div>
        </div>
        <a routerLink="/adoption" class="btn btn-ghost btn-sm">Retour au fil</a>
      </div>

      @if (notice) { <div class="alert alert-success" data-testid="draft-notice">{{ notice }}</div> }
      @if (generateError) { <div class="alert alert-danger" data-testid="generate-error">{{ generateError }}</div> }
      @if (saveError) { <div class="alert alert-danger">{{ saveError }}</div> }
      @if (uncertainties.length) {
        <div class="card uncertainties">
          <strong>L'IA n'a pas pu établir :</strong>
          <ul>
            @for (item of uncertainties; track item) { <li>{{ item }}</li> }
          </ul>
        </div>
      }

      <div class="card form-grid">
        <label class="form-group">Titre<input class="form-control" [(ngModel)]="form.title" /></label>
        <label class="form-group">Nom de l'animal<input class="form-control" [(ngModel)]="form.animalName" data-testid="animal-name" /></label>
        <label class="form-group">Espèce<input class="form-control" [(ngModel)]="form.species" /></label>
        <label class="form-group">Race<input class="form-control" [(ngModel)]="form.breed" /></label>
        <label class="form-group">Âge<input class="form-control" [(ngModel)]="form.ageLabel" /></label>
        <label class="form-group">Sexe<input class="form-control" [(ngModel)]="form.gender" /></label>
        <label class="form-group wide">Secteur<input class="form-control" [(ngModel)]="form.areaLabel" placeholder="Tunis" /></label>
        <label class="form-group wide">Caractère<textarea class="form-control" rows="2" [(ngModel)]="form.personality"></textarea></label>
        <label class="form-group wide">Besoins particuliers<textarea class="form-control" rows="2" [(ngModel)]="form.specialNeeds"></textarea></label>
        <label class="form-group wide">Notes du refuge<textarea class="form-control" rows="4" [(ngModel)]="form.shelterNotes" data-testid="notes"></textarea></label>
        <label class="form-group wide">Description de la photo<textarea class="form-control" rows="2" [(ngModel)]="form.photoDescription"></textarea></label>
        <label class="form-group wide">Description publiable<textarea class="form-control" rows="5" [(ngModel)]="form.description" data-testid="description"></textarea></label>
        <div class="actions">
          <button class="btn btn-ghost" type="button" data-testid="generate" [disabled]="generating" (click)="generate()">
            {{ generating ? 'Génération…' : 'Générer avec l’IA' }}
          </button>
          <button class="btn btn-primary" type="button" data-testid="publish" [disabled]="saving" (click)="publish()">
            {{ saving ? 'Publication…' : 'Publier' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .form-card { max-width: 860px; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; padding: 20px; }
    .wide { grid-column: 1 / -1; }
    .actions { grid-column: 1 / -1; display: flex; gap: 10px; flex-wrap: wrap; }
    .uncertainties { padding: 14px 18px; margin-bottom: 16px; }
    @media (max-width: 700px) { .form-grid { grid-template-columns: 1fr; } }
  `]
})
export class AdoptionPublishComponent {
  generating = false;
  saving = false;
  notice = '';
  generateError = '';
  saveError = '';
  uncertainties: string[] = [];
  form: PublishAdoptionRequest = {
    title: '',
    animalName: '',
    species: '',
    breed: '',
    ageLabel: '',
    gender: '',
    areaLabel: 'Tunis',
    personality: '',
    specialNeeds: '',
    shelterNotes: '',
    photoDescription: '',
    description: ''
  };

  constructor(private adoption: AdoptionService, private router: Router) {}

  generate(): void {
    this.notice = '';
    this.generateError = '';
    this.uncertainties = [];
    if (!this.form.shelterNotes.trim()) {
      this.generateError = 'Écris d\'abord les notes du refuge. Le formulaire reste modifiable.';
      return;
    }
    this.generating = true;
    this.adoption.previewDraft(this.form.shelterNotes, this.form.photoDescription).subscribe({
      next: draft => {
        this.generating = false;
        this.applyDraft(draft);
        this.notice = 'Proposition affichée dans le formulaire. Rien n\'est publié.';
      },
      error: err => {
        this.generating = false;
        this.generateError = apiMessage(err, 'L\'IA n\'a pas répondu. Tu peux compléter et publier le formulaire à la main.');
      }
    });
  }

  publish(): void {
    this.saveError = '';
    if (!this.form.title.trim() || !this.form.shelterNotes.trim()) {
      this.saveError = 'Le titre et les notes sont requis.';
      return;
    }
    this.saving = true;
    this.adoption.create(this.form).subscribe({
      next: post => this.router.navigate(['/adoption', post.id]),
      error: err => {
        this.saving = false;
        this.saveError = apiMessage(err, 'La publication a échoué. Le formulaire est conservé.');
      }
    });
  }

  private applyDraft(draft: AdoptionDraft): void {
    this.fill('animalName', draft.name);
    this.fill('species', draft.species);
    this.fill('breed', draft.breed);
    this.fill('ageLabel', draft.age);
    this.fill('gender', draft.gender);
    this.fill('specialNeeds', draft.specialNeeds);
    if (draft.description) this.form.description = draft.description;
    if (draft.personalityTags?.length) this.form.personality = draft.personalityTags.join(', ');
    this.uncertainties = draft.uncertainties || [];
  }

  private fill(field: 'animalName' | 'species' | 'breed' | 'ageLabel' | 'gender' | 'specialNeeds', value: string | null): void {
    if (value) this.form[field] = value;
  }
}
