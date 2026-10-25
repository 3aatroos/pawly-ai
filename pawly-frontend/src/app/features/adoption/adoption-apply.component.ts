import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdoptionService } from '../../core/services/adoption.service';
import { apiMessage } from '../../core/models/adoption.model';

@Component({
  selector: 'app-adoption-apply',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <form class="card apply" (ngSubmit)="submit()">
      <div class="section-title">Candidature</div>
      <p class="text-soft">Le refuge lit ce mode de vie. Ton e-mail et ton téléphone ne partent pas vers l'IA.</p>
      @if (done) { <div class="alert alert-success">Candidature envoyée.</div> }
      @if (error) { <div class="alert alert-danger">{{ error }}</div> }
      <label class="form-group">Logement<input class="form-control" name="housing" [(ngModel)]="housing" required /></label>
      <label class="form-group">Heures d'absence par jour<input class="form-control" type="number" min="0" max="24" name="hours" [(ngModel)]="hoursAwayPerDay" /></label>
      <label class="form-group">Expérience
        <select class="form-control" name="experience" [(ngModel)]="experience">
          <option value="débutant">Débutant</option>
          <option value="intermédiaire">Intermédiaire</option>
          <option value="expérimenté">Expérimenté</option>
        </select>
      </label>
      <label class="check"><input type="checkbox" name="garden" [(ngModel)]="hasGarden" /> Jardin</label>
      <label class="check"><input type="checkbox" name="pets" [(ngModel)]="otherPets" /> Autre animal</label>
      <label class="check"><input type="checkbox" name="children" [(ngModel)]="childrenAtHome" /> Enfants à la maison</label>
      <label class="form-group">Motivation<textarea class="form-control" rows="4" name="motivation" [(ngModel)]="motivation"></textarea></label>
      <button class="btn btn-primary" type="submit" [disabled]="sending || done">Envoyer la candidature</button>
    </form>
  `,
  styles: [`
    .apply { padding: 20px; margin-top: 16px; }
    .section-title { font-size: 18px; font-weight: 800; margin-bottom: 6px; }
    .check { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; }
  `]
})
export class AdoptionApplyComponent {
  @Input({ required: true }) postId!: number;
  @Output() submitted = new EventEmitter<void>();

  housing = '';
  hoursAwayPerDay: number | null = null;
  experience = 'débutant';
  hasGarden = false;
  otherPets = false;
  childrenAtHome = false;
  motivation = '';
  sending = false;
  done = false;
  error = '';

  constructor(private adoption: AdoptionService) {}

  submit(): void {
    this.error = '';
    if (!this.housing.trim()) {
      this.error = 'Le logement est requis.';
      return;
    }
    this.sending = true;
    this.adoption.apply(this.postId, {
      housing: this.housing.trim(),
      hoursAwayPerDay: this.hoursAwayPerDay,
      experience: this.experience,
      hasGarden: this.hasGarden,
      otherPets: this.otherPets,
      childrenAtHome: this.childrenAtHome,
      motivation: this.motivation.trim() || null
    }).subscribe({
      next: () => {
        this.sending = false;
        this.done = true;
        this.submitted.emit();
      },
      error: err => {
        this.sending = false;
        this.error = apiMessage(err, 'La candidature n\'a pas été envoyée. Le formulaire est conservé.');
      }
    });
  }
}
