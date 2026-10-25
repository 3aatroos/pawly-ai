import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdoptionService } from '../../core/services/adoption.service';
import { AdoptionApplication, AdoptionReview, apiMessage } from '../../core/models/adoption.model';

@Component({
  selector: 'app-adoption-applications',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="fade-up">
      <a class="btn btn-ghost btn-sm" [routerLink]="['/adoption', postId]">← Fiche</a>
      <div class="page-header">
        <div>
          <div class="page-title">Candidatures</div>
          <div class="page-sub">Le résumé aide la lecture. Accepter ou refuser reste un geste humain.</div>
        </div>
      </div>
      @if (error) { <div class="alert alert-danger">{{ error }}</div> }
      @if (!loading && applications.length === 0) {
        <div class="card empty">Aucune candidature pour cette fiche.</div>
      }
      @for (application of applications; track application.id) {
        <article class="card item">
          <header>
            <strong>{{ application.applicantFirstName || 'Candidat' }}</strong>
            <span class="status">{{ application.status }}</span>
          </header>
          <p>{{ application.housing }} · {{ application.experience || 'expérience non précisée' }}</p>
          @if (application.motivation) { <p>{{ application.motivation }}</p> }
          @if (preview(application.id); as review) {
            <div class="summary" data-testid="ai-summary">
              <div class="attention">Attention {{ review.suggestedAttention }}</div>
              <p>{{ review.summary }}</p>
              @for (point of review.pointsToVerify; track point) { <div class="point">{{ point }}</div> }
              <p class="text-soft">Ce résumé est affiché avant enregistrement. Il ne change pas le statut.</p>
            </div>
          } @else if (application.reviewerSummary) {
            <div class="summary"><p>{{ application.reviewerSummary }}</p></div>
          }
          @if (cardError(application.id); as message) { <div class="alert alert-danger">{{ message }}</div> }
          <div class="actions">
            <button class="btn btn-ghost btn-sm" type="button" [disabled]="busyId === application.id" (click)="summarize(application)">
              Générer le résumé
            </button>
            <button class="btn btn-success btn-sm" type="button" data-testid="accept" [disabled]="busyId === application.id" (click)="decide(application, 'ACCEPTED')">
              Accepter
            </button>
            <button class="btn btn-danger btn-sm" type="button" data-testid="reject" [disabled]="busyId === application.id" (click)="decide(application, 'REJECTED')">
              Refuser
            </button>
          </div>
        </article>
      }
    </div>
  `,
  styles: [`
    .item { padding: 18px; margin-top: 14px; }
    header { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
    .status { font-size: 11px; font-weight: 800; background: var(--neutral); padding: 3px 8px; border-radius: 99px; }
    .summary { margin-top: 12px; padding: 12px; border-radius: 12px; background: #E8FFF5; }
    .attention { font-size: 11px; font-weight: 800; text-transform: uppercase; margin-bottom: 6px; }
    .point { font-size: 13px; margin-top: 4px; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 14px; }
    .empty { padding: 20px; }
  `]
})
export class AdoptionApplicationsComponent implements OnInit {
  postId = 0;
  applications: AdoptionApplication[] = [];
  previews = new Map<number, AdoptionReview>();
  errors = new Map<number, string>();
  loading = true;
  error = '';
  busyId: number | null = null;

  constructor(private route: ActivatedRoute, private adoption: AdoptionService) {}

  ngOnInit(): void {
    this.postId = Number(this.route.snapshot.paramMap.get('id'));
    this.adoption.applications(this.postId).subscribe({
      next: rows => {
        this.applications = rows;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.error = apiMessage(err, 'Les candidatures sont indisponibles.');
      }
    });
  }

  preview(id: number): AdoptionReview | undefined {
    return this.previews.get(id);
  }

  cardError(id: number): string | undefined {
    return this.errors.get(id);
  }

  summarize(application: AdoptionApplication): void {
    this.busyId = application.id;
    this.errors.delete(application.id);
    this.adoption.previewReview(application.id).subscribe({
      next: review => {
        this.busyId = null;
        this.previews.set(application.id, review);
      },
      error: err => {
        this.busyId = null;
        this.errors.set(application.id, apiMessage(err, 'Le résumé n\'a pas pu être généré. Tu peux accepter ou refuser à la main.'));
      }
    });
  }

  decide(application: AdoptionApplication, status: 'ACCEPTED' | 'REJECTED'): void {
    this.busyId = application.id;
    this.errors.delete(application.id);
    this.adoption.decide(application.id, status).subscribe({
      next: updated => {
        this.busyId = null;
        this.applications = this.applications.map(row => row.id === updated.id ? updated : row);
      },
      error: err => {
        this.busyId = null;
        this.errors.set(application.id, apiMessage(err, 'La décision n\'a pas été enregistrée.'));
      }
    });
  }
}
