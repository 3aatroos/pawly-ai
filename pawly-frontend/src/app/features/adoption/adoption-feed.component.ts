import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AdoptionService } from '../../core/services/adoption.service';
import {
  AdoptionMatchItem,
  AdoptionPost,
  apiMessage,
  speciesEmoji,
  speciesMatches
} from '../../core/models/adoption.model';

@Component({
  selector: 'app-adoption-feed',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">Adopt a Pet</div>
          <div class="page-sub">Trouve un compagnon. Le matching décrit ton mode de vie, il ne décide pas.</div>
        </div>
        @if (auth.isVet() || auth.isAdmin()) {
          <a routerLink="/adoption/new" class="btn btn-primary btn-sm">+ Publier une fiche</a>
        }
      </div>

      <div class="adopt-hero">
        <div class="adopt-hero-icon">🏠</div>
        <div>
          <div class="hero-title">Find Your Perfect Companion</div>
          <div class="hero-sub">Décris ton mode de vie. Le score s'affiche sur les cartes disponibles.</div>
        </div>
      </div>

      <section class="match-panel">
        <div class="match-head">
          <div>
            <div class="match-title">Décris ton mode de vie</div>
            <div class="match-sub">Logement, présence et expérience. Aucun nom ni contact n'est envoyé.</div>
          </div>
          @if (matching) { <div class="spinner"></div> }
        </div>
        <div class="match-grid">
          <label>Logement
            <input class="match-field" [(ngModel)]="lifestyle.housing" placeholder="Appartement à Tunis" />
          </label>
          <label>Secteur
            <input class="match-field" [(ngModel)]="lifestyle.sector" placeholder="Tunis" />
          </label>
          <label>Heures d'absence
            <input class="match-field" type="number" min="0" max="24" [(ngModel)]="lifestyle.hoursAwayPerDay" />
          </label>
          <label>Expérience
            <select class="match-field" [(ngModel)]="lifestyle.experience">
              <option value="">Non précisée</option>
              <option value="débutant">Débutant</option>
              <option value="intermédiaire">Intermédiaire</option>
              <option value="expérimenté">Expérimenté</option>
            </select>
          </label>
        </div>
        <div class="match-checks">
          <label><input type="checkbox" [(ngModel)]="lifestyle.hasGarden" /> Jardin</label>
          <label><input type="checkbox" [(ngModel)]="lifestyle.otherPets" /> Autre animal</label>
          <label><input type="checkbox" [(ngModel)]="lifestyle.children" /> Enfants</label>
        </div>
        <div class="match-actions">
          <button class="btn btn-success" type="button" data-testid="find-match" [disabled]="matching" (click)="findMatch()">
            Trouver mon match
          </button>
          <select class="match-field species-select" [(ngModel)]="species" data-testid="species-filter">
            <option value="">Toutes les espèces</option>
            <option value="chien">Chiens</option>
            <option value="chat">Chats</option>
            <option value="lapin">Lapins</option>
            <option value="oiseau">Oiseaux</option>
          </select>
        </div>
        @if (matchError) {
          <div class="match-error">{{ matchError }}</div>
        }
        @if (matchMs !== null) {
          <div class="match-time" data-testid="match-time">Réponse du matching en {{ matchMs }} ms</div>
        }
        @if (matchCards.length) {
          <div class="match-results">
            @for (card of matchCards; track card.item.postId) {
              <a class="match-card" [routerLink]="['/adoption', card.item.postId]">
                <div class="match-animal-emoji">{{ card.emoji }}</div>
                <div class="match-animal-name">{{ card.name }}</div>
                <div class="match-animal-breed">{{ card.meta }}</div>
                <div class="match-pct">{{ card.item.score }}% match</div>
                <div class="match-why">{{ card.item.reason }}</div>
                @if (card.item.warning) { <div class="match-why">{{ card.item.warning }}</div> }
              </a>
            }
          </div>
        } @else if (!matching && matchMs === null) {
          <div class="match-placeholder">Décris ton mode de vie, puis lance le matching.</div>
        }
      </section>

      <div class="filter-bar">
        <input class="search-input" type="search" placeholder="Rechercher un nom, une race, un secteur" [(ngModel)]="search" data-testid="search" />
      </div>

      @if (loadError) { <div class="alert alert-danger">{{ loadError }}</div> }
      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      } @else if (visible.length === 0) {
        <div class="card"><div class="empty-state"><div class="empty-title">Aucune fiche disponible</div></div></div>
      } @else {
        <div class="adopt-feed">
          @for (post of visible; track post.id) {
            <a class="adopt-card" [routerLink]="['/adoption', post.id]">
              <div class="adopt-card-img" [ngClass]="tone(post.id)">
                {{ emoji(post) }}
                <span class="adopt-status-badge adopt-status-available">Available</span>
              </div>
              <div class="adopt-card-body">
                <div class="adopt-animal-name">{{ post.animalName || post.title }}</div>
                <div class="adopt-animal-info">{{ info(post) }}</div>
                <div class="adopt-location">{{ post.areaLabel || 'Secteur non indiqué' }} · {{ post.authorFirstName || 'Refuge' }}</div>
                @if (post.personality) {
                  <div class="adopt-tags"><span class="adopt-tag">{{ post.personality }}</span></div>
                }
                @if (score(post.id); as item) {
                  <div class="adopt-ai-match" data-testid="match-badge">{{ item.score }}% match</div>
                }
              </div>
            </a>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .adopt-hero { background: linear-gradient(135deg, #2ED573 0%, #1abc9c 100%); border-radius: var(--radius); padding: 28px; color: #fff; margin-bottom: 24px; display: flex; align-items: center; gap: 20px; position: relative; overflow: hidden; }
    .adopt-hero::after { content: '🏠'; position: absolute; right: 24px; font-size: 90px; opacity: .15; }
    .adopt-hero-icon { width: 60px; height: 60px; border-radius: 50%; background: rgba(255,255,255,.25); display: flex; align-items: center; justify-content: center; font-size: 28px; }
    .hero-title { font-size: 22px; font-weight: 800; }
    .hero-sub { font-size: 14px; opacity: .85; margin-top: 4px; }
    .match-panel { background: linear-gradient(135deg, #1A1D2E 0%, #1a3a2e 100%); border-radius: var(--radius); padding: 24px; color: #fff; margin-bottom: 20px; }
    .match-head, .match-actions, .match-checks { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    .match-title { font-size: 16px; font-weight: 800; }
    .match-sub, .match-placeholder, .match-time { font-size: 12px; opacity: .75; }
    .match-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 16px 0; }
    .match-grid label, .match-checks label { font-size: 11px; font-weight: 700; display: flex; flex-direction: column; gap: 6px; }
    .match-checks label { flex-direction: row; align-items: center; }
    .match-field { background: rgba(255,255,255,.08); color: #fff; border: 1.5px solid rgba(255,255,255,.2); border-radius: 10px; padding: 8px 12px; }
    .species-select { max-width: 220px; }
    .match-error { margin-top: 12px; color: #ffd0d4; }
    .match-results { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 16px; }
    .match-card { background: rgba(255,255,255,.07); border-radius: 12px; padding: 14px; border: 1.5px solid rgba(255,255,255,.12); color: inherit; text-decoration: none; }
    .match-card:hover { background: rgba(46,213,115,.15); border-color: #2ED573; }
    .match-animal-emoji { font-size: 36px; }
    .match-animal-name { font-size: 14px; font-weight: 800; }
    .match-animal-breed, .match-why { font-size: 11px; opacity: .7; margin-top: 4px; }
    .match-pct { display: inline-flex; margin-top: 8px; background: rgba(46,213,115,.25); border: 1px solid #2ED573; color: #2ED573; font-size: 11px; font-weight: 800; padding: 3px 10px; border-radius: 99px; }
    .filter-bar { margin-bottom: 16px; }
    .search-input { max-width: 420px; }
    .adopt-feed { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .adopt-card { background: var(--white); border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); text-decoration: none; color: inherit; border: 2px solid transparent; }
    .adopt-card:hover { transform: translateY(-3px); box-shadow: var(--shadow); border-color: #2ED573; }
    .adopt-card-img { height: 120px; display: flex; align-items: center; justify-content: center; font-size: 60px; position: relative; }
    .bg-green { background: #E8FFF5; } .bg-orange { background: #FFF4EF; } .bg-blue { background: #EEF8FF; } .bg-purple { background: var(--primary-light); } .bg-yellow { background: #FFFBEF; }
    .adopt-status-badge { position: absolute; top: 10px; right: 10px; font-size: 10px; font-weight: 800; padding: 3px 8px; border-radius: 99px; }
    .adopt-status-available { background: var(--success-light); color: #1A8A44; }
    .adopt-card-body { padding: 14px; }
    .adopt-animal-name { font-size: 15px; font-weight: 800; }
    .adopt-animal-info, .adopt-location { font-size: 12px; color: var(--text-soft); margin-top: 2px; }
    .adopt-tags { display: flex; gap: 4px; flex-wrap: wrap; margin-top: 8px; }
    .adopt-tag { background: var(--neutral); color: var(--text-soft); font-size: 10px; font-weight: 600; padding: 2px 8px; border-radius: 99px; }
    .adopt-ai-match { display: inline-flex; margin-top: 8px; background: linear-gradient(90deg,#2ED573,#1abc9c); color: #fff; font-size: 10px; font-weight: 800; padding: 3px 8px; border-radius: 99px; }
    @media (max-width: 900px) {
      .match-grid, .match-results, .adopt-feed { grid-template-columns: 1fr; }
    }
  `]
})
export class AdoptionFeedComponent implements OnInit {
  posts: AdoptionPost[] = [];
  scores = new Map<number, AdoptionMatchItem>();
  search = '';
  species = '';
  loading = true;
  matching = false;
  loadError = '';
  matchError = '';
  matchMs: number | null = null;
  lifestyle = {
    housing: '',
    sector: 'Tunis',
    hoursAwayPerDay: null as number | null,
    experience: '',
    hasGarden: false,
    otherPets: false,
    children: false
  };

  constructor(private adoption: AdoptionService, public auth: AuthService) {}

  ngOnInit(): void {
    this.adoption.list('AVAILABLE').subscribe({
      next: posts => {
        this.posts = posts;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.loadError = apiMessage(err, 'Le fil est indisponible.');
      }
    });
  }

  get visible(): AdoptionPost[] {
    const query = this.search.trim().toLowerCase();
    return this.posts.filter(post => {
      if (!speciesMatches(post.species, this.species)) return false;
      if (!query) return true;
      const haystack = `${post.animalName || ''} ${post.title} ${post.breed || ''} ${post.areaLabel || ''}`.toLowerCase();
      return haystack.includes(query);
    });
  }

  get matchCards(): { item: AdoptionMatchItem; name: string; meta: string; emoji: string }[] {
    return (this.scores.size ? [...this.scores.values()] : [])
      .map(item => {
        const post = this.posts.find(candidate => candidate.id === item.postId);
        if (post && !speciesMatches(post.species, this.species)) return null;
        return {
          item,
          name: post?.animalName || post?.title || `Fiche ${item.postId}`,
          meta: post ? this.info(post) : '',
          emoji: this.emoji(post)
        };
      })
      .filter((card): card is { item: AdoptionMatchItem; name: string; meta: string; emoji: string } => !!card)
      .slice(0, 5);
  }

  findMatch(): void {
    if (!this.lifestyle.housing.trim() && !this.lifestyle.sector.trim() && this.lifestyle.hoursAwayPerDay === null) {
      this.matchError = 'Indique au moins le logement, le secteur ou les heures d\'absence.';
      return;
    }
    this.matching = true;
    this.matchError = '';
    const started = performance.now();
    const hours = this.lifestyle.hoursAwayPerDay;
    this.adoption.match({
      housing: this.lifestyle.housing.trim() || null,
      sector: this.lifestyle.sector.trim() || null,
      hoursAwayPerDay: hours === null || hours === undefined || String(hours) === '' ? null : Number(hours),
      experience: this.lifestyle.experience || null,
      hasGarden: this.lifestyle.hasGarden,
      otherPets: this.lifestyle.otherPets,
      children: this.lifestyle.children
    }).subscribe({
      next: result => {
        this.matching = false;
        this.matchMs = Math.round(performance.now() - started);
        this.scores = new Map(result.matches.map(item => [item.postId, item]));
      },
      error: err => {
        this.matching = false;
        this.matchMs = Math.round(performance.now() - started);
        this.matchError = apiMessage(err, 'Le matching n\'a pas répondu. Le fil reste consultable.');
      }
    });
  }

  score(id: number): AdoptionMatchItem | undefined {
    return this.scores.get(id);
  }

  emoji(post: AdoptionPost | undefined): string {
    return speciesEmoji(post?.species);
  }

  info(post: AdoptionPost): string {
    return [post.breed, post.ageLabel, post.gender].filter(Boolean).join(' · ') || post.species || 'Espèce non précisée';
  }

  tone(id: number): string {
    return ['bg-orange', 'bg-purple', 'bg-blue', 'bg-yellow', 'bg-green'][id % 5];
  }
}
