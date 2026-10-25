import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AdoptionService } from '../../core/services/adoption.service';
import { AdoptionPost, apiMessage, speciesEmoji } from '../../core/models/adoption.model';
import { AdoptionApplyComponent } from './adoption-apply.component';

@Component({
  selector: 'app-adoption-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, AdoptionApplyComponent],
  template: `
    <div class="fade-up">
      <a routerLink="/adoption" class="btn btn-ghost btn-sm">← Fil</a>
      @if (error) { <div class="alert alert-danger mt-16">{{ error }}</div> }
      @if (post) {
        <div class="card detail">
          <div class="hero-emoji">{{ emoji }}</div>
          <div>
            <div class="page-title">{{ post.animalName || post.title }}</div>
            <div class="page-sub">{{ meta }}</div>
            <div class="text-soft mt-8">{{ post.areaLabel || 'Secteur non indiqué' }} · publié par {{ post.authorFirstName || 'le refuge' }}</div>
            <span class="status">{{ post.status }}</span>
          </div>
        </div>
        <div class="card text-block">
          <h2>Description</h2>
          <p>{{ post.description || 'Pas encore de description publiée.' }}</p>
          @if (post.personality) { <p><strong>Caractère.</strong> {{ post.personality }}</p> }
          @if (post.specialNeeds) { <p><strong>Besoins.</strong> {{ post.specialNeeds }}</p> }
        </div>
        @if (staff) {
          <a class="btn btn-primary" [routerLink]="['/adoption', post.id, 'applications']">Candidatures du refuge</a>
        }
        @if (canApply) {
          <app-adoption-apply [postId]="post.id" />
        }
      }
    </div>
  `,
  styles: [`
    .detail { display: flex; gap: 18px; align-items: center; padding: 20px; margin-top: 16px; }
    .hero-emoji { font-size: 64px; width: 110px; height: 110px; border-radius: 18px; background: #E8FFF5; display: flex; align-items: center; justify-content: center; }
    .status { display: inline-block; margin-top: 8px; background: var(--success-light); color: #1A8A44; font-size: 11px; font-weight: 800; padding: 3px 8px; border-radius: 99px; }
    .text-block { padding: 20px; margin: 16px 0; }
    .text-block h2 { font-size: 16px; margin-bottom: 8px; }
    .text-block p { margin-top: 8px; }
  `]
})
export class AdoptionDetailComponent implements OnInit {
  post: AdoptionPost | null = null;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private adoption: AdoptionService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.adoption.get(id).subscribe({
      next: post => this.post = post,
      error: err => this.error = apiMessage(err, 'Cette fiche est introuvable.')
    });
  }

  get emoji(): string {
    return speciesEmoji(this.post?.species);
  }

  get meta(): string {
    if (!this.post) return '';
    return [this.post.breed, this.post.ageLabel, this.post.gender, this.post.species].filter(Boolean).join(' · ');
  }

  get staff(): boolean {
    return this.auth.isVet() || this.auth.isAdmin();
  }

  get canApply(): boolean {
    return !!this.post
      && this.post.status === 'AVAILABLE'
      && this.post.authorId !== this.auth.currentUser()?.userId;
  }
}
