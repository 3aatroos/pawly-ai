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
          <div class="page-sub">Trouve un compagnon. Le score commente ton mode de vie, il ne décide pas.</div>
        </div>
        @if (auth.isVet() || auth.isAdmin()) {
          <a routerLink="/adoption/new" class="btn btn-primary">+ Publier une fiche</a>
        }
      </div>

      <section class="panel">
        <div class="panel-head">
          <div class="panel-icon" aria-hidden="true">🏡</div>
          <div class="panel-copy">
            <h2>Décris ton mode de vie</h2>
            <p>Logement, présence et expérience. Aucun nom ni contact n'est envoyé. Le score apparaît ensuite sur les fiches.</p>
          </div>
          @if (matching) { <div class="spinner" aria-label="Matching en cours"></div> }
        </div>

        <div class="match-grid">
          <label>Logement
            <input [(ngModel)]="lifestyle.housing" placeholder="Appartement à Tunis" />
          </label>
          <label>Secteur
            <input [(ngModel)]="lifestyle.sector" placeholder="Tunis" />
          </label>
          <label>Heures d'absence par jour
            <input type="number" min="0" max="24" [(ngModel)]="lifestyle.hoursAwayPerDay" placeholder="6" />
          </label>
          <label>Expérience
            <select [(ngModel)]="lifestyle.experience">
              <option value="">Non précisée</option>
              <option value="débutant">Débutant</option>
              <option value="intermédiaire">Intermédiaire</option>
              <option value="expérimenté">Expérimenté</option>
            </select>
          </label>
        </div>

        <div class="panel-foot">
          <div class="chips">
            <label class="chip" [class.on]="lifestyle.hasGarden">
              <input type="checkbox" [(ngModel)]="lifestyle.hasGarden" /> Jardin
            </label>
            <label class="chip" [class.on]="lifestyle.otherPets">
              <input type="checkbox" [(ngModel)]="lifestyle.otherPets" /> Autre animal
            </label>
            <label class="chip" [class.on]="lifestyle.children">
              <input type="checkbox" [(ngModel)]="lifestyle.children" /> Enfants
            </label>
          </div>
          <button class="btn btn-success" type="button" data-testid="find-match" [disabled]="matching" (click)="findMatch()">
            {{ matching ? 'Recherche…' : 'Trouver mon match' }}
          </button>
        </div>

        @if (matchError) {
          <div class="alert alert-danger match-alert">{{ matchError }}</div>
        }
        @if (matchMs !== null) {
          <div class="match-time" data-testid="match-time">Réponse du matching en {{ matchMs }} ms</div>
        }
        @if (matchCards.length) {
          <div class="match-results">
            @for (card of matchCards; track card.item.postId) {
              <a class="match-card" [routerLink]="['/adoption', card.item.postId]">
                <div class="match-animal-emoji">{{ card.emoji }}</div>
                <div>
                  <div class="match-animal-name">{{ card.name }}</div>
                  <div class="match-animal-breed">{{ card.meta }}</div>
                  <div class="match-pct">{{ card.item.score }}% match</div>
                  <div class="match-why">{{ card.item.reason }}</div>
                  @if (card.item.warning) { <div class="match-warn">{{ card.item.warning }}</div> }
                </div>
              </a>
            }
          </div>
        }
      </section>

      <div class="feed-head">
        <div>
          <h2>Fiches disponibles</h2>
          <p>{{ loading ? 'Chargement…' : feedLabel }}</p>
        </div>
        <div class="feed-tools">
          <input class="search-input" type="search" placeholder="Nom, race ou secteur" [(ngModel)]="search" data-testid="search" />
          <div class="pills" data-testid="species-filter">
            @for (option of speciesOptions; track option.value) {
              <button type="button" class="pill" [class.active]="species === option.value" (click)="species = option.value">
                {{ option.label }}
              </button>
            }
          </div>
        </div>
      </div>

      @if (loadError) { <div class="alert alert-danger">{{ loadError }}</div> }
      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      } @else if (visible.length === 0) {
        <div class="empty-panel">
          <div class="empty-mark" aria-hidden="true">🐾</div>
          <div class="empty-title">{{ posts.length ? 'Aucun animal pour cette recherche' : 'Aucune fiche pour le moment' }}</div>
          <div class="empty-sub">
            {{ posts.length
              ? 'Élargis l’espèce ou efface la recherche pour revoir tout le fil.'
              : 'Quand un refuge publie un animal, sa fiche apparaît ici. Tu pourras alors lancer le matching.' }}
          </div>
        </div>
      } @else {
        <div class="adopt-feed">
          @for (post of visible; track post.id) {
            <a class="adopt-card" [routerLink]="['/adoption', post.id]">
              <div class="adopt-card-img" [ngClass]="post.photoUrl ? 'has-photo' : tone(post.id)">
                @if (post.photoUrl) {
                  <img [src]="post.photoUrl" [alt]="post.animalName || post.title" />
                } @else {
                  <span class="adopt-emoji">{{ emoji(post) }}</span>
                }
                <span class="adopt-status-badge">Disponible</span>
              </div>
              <div class="adopt-card-body">
                <div class="adopt-animal-name">{{ post.animalName || post.title }}</div>
                <div class="adopt-animal-info">{{ info(post) }}</div>
                <div class="adopt-location">{{ post.areaLabel || 'Secteur non indiqué' }} · {{ post.authorFirstName || 'Refuge' }}</div>
                @if (tags(post).length) {
                  <div class="adopt-tags">
                    @for (tag of tags(post); track tag) { <span class="adopt-tag">{{ tag }}</span> }
                  </div>
                }
                @if (post.description) { <div class="adopt-desc">{{ post.description }}</div> }
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
    .panel { background: var(--white); border-radius: var(--radius); padding: 22px; box-shadow: var(--shadow-sm); margin-bottom: 28px; }
    .panel-head { display: flex; align-items: flex-start; gap: 14px; }
    .panel-icon { width: 48px; height: 48px; border-radius: 14px; background: var(--primary-light); display: flex; align-items: center; justify-content: center; font-size: 24px; flex-shrink: 0; }
    .panel-copy { flex: 1; }
    .panel-copy h2, .feed-head h2 { font-size: 16px; font-weight: 800; }
    .panel-copy p, .feed-head p { font-size: 13px; color: var(--text-soft); margin-top: 4px; }
    .match-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 16px; margin-top: 18px; }
    .match-grid label { font-size: 11px; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; color: var(--text-soft); display: flex; flex-direction: column; gap: 6px; }
    .panel-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-top: 16px; }
    .chips { display: flex; gap: 8px; flex-wrap: wrap; }
    .chip { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 999px; border: 1.5px solid var(--border); background: var(--neutral); font-size: 13px; font-weight: 600; cursor: pointer; color: var(--text); }
    .chip input { width: 16px; height: 16px; accent-color: var(--primary); }
    .chip.on { background: var(--primary-light); border-color: var(--primary); color: var(--primary-dark); }
    .match-alert { margin-top: 14px; margin-bottom: 0; }
    .match-time { margin-top: 12px; font-size: 12px; font-weight: 600; color: var(--text-soft); }
    .match-results { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border); }
    .match-card { display: flex; gap: 12px; background: var(--neutral); border-radius: 14px; padding: 14px; border: 1.5px solid transparent; color: inherit; text-decoration: none; }
    .match-card:hover { background: var(--success-light); border-color: var(--success); }
    .match-animal-emoji { font-size: 32px; line-height: 1; }
    .match-animal-name { font-size: 14px; font-weight: 800; }
    .match-animal-breed, .match-why { font-size: 12px; color: var(--text-soft); margin-top: 2px; }
    .match-warn { font-size: 12px; color: var(--warning); margin-top: 4px; }
    .match-pct { display: inline-flex; margin-top: 8px; background: var(--success-light); color: #1A8A44; font-size: 11px; font-weight: 800; padding: 3px 10px; border-radius: 99px; }
    .feed-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 16px; }
    .feed-tools { display: flex; flex-direction: column; align-items: flex-end; gap: 10px; flex: 1; min-width: 260px; }
    .search-input { max-width: 360px; width: 100%; padding: 10px 14px; border-radius: 10px; border: 1.5px solid var(--border); background: var(--neutral); font: inherit; color: var(--text); outline: none; }
    .search-input:focus { border-color: var(--primary); background: #fff; }
    .pills { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
    .pill { border: 1.5px solid var(--border); background: var(--white); color: var(--text-soft); border-radius: 999px; padding: 7px 12px; font-size: 13px; font-weight: 600; cursor: pointer; font-family: inherit; }
    .pill.active { background: var(--primary); border-color: var(--primary); color: #fff; }
    .empty-panel { background: var(--white); border: 1.5px dashed var(--border); border-radius: var(--radius); text-align: center; padding: 48px 24px; }
    .empty-mark { width: 72px; height: 72px; margin: 0 auto 14px; border-radius: 50%; background: var(--primary-light); display: flex; align-items: center; justify-content: center; font-size: 32px; }
    .empty-title { font-size: 16px; font-weight: 700; }
    .empty-sub { font-size: 13px; color: var(--text-soft); max-width: 420px; margin: 6px auto 0; }
    .adopt-feed { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .adopt-card { background: var(--white); border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); text-decoration: none; color: inherit; border: 1.5px solid transparent; display: flex; flex-direction: column; transition: transform .15s, box-shadow .15s, border-color .15s; }
    .adopt-card:hover { transform: translateY(-3px); box-shadow: var(--shadow); border-color: var(--primary); }
    .adopt-card-img { height: 132px; display: flex; align-items: center; justify-content: center; position: relative; }
    .adopt-card-img.has-photo { background: var(--neutral); }
    .adopt-card-img img { width: 100%; height: 100%; object-fit: cover; }
    .adopt-emoji { font-size: 56px; line-height: 1; }
    .bg-green { background: #E8FFF5; } .bg-orange { background: #FFF4EF; } .bg-blue { background: #EEF8FF; } .bg-purple { background: var(--primary-light); } .bg-yellow { background: #FFFBEF; }
    .adopt-status-badge { position: absolute; top: 10px; right: 10px; font-size: 10px; font-weight: 800; padding: 3px 8px; border-radius: 99px; background: var(--success-light); color: #1A8A44; }
    .adopt-card-body { padding: 14px 16px 16px; }
    .adopt-animal-name { font-size: 15px; font-weight: 800; }
    .adopt-animal-info, .adopt-location, .adopt-desc { font-size: 12px; color: var(--text-soft); margin-top: 2px; }
    .adopt-desc { margin-top: 8px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .adopt-tags { display: flex; gap: 4px; flex-wrap: wrap; margin-top: 8px; }
    .adopt-tag { background: var(--neutral); color: var(--text-soft); font-size: 10px; font-weight: 600; padding: 2px 8px; border-radius: 99px; }
    .adopt-ai-match { display: inline-flex; margin-top: 10px; background: var(--success-light); color: #1A8A44; font-size: 11px; font-weight: 800; padding: 3px 8px; border-radius: 99px; }
    @media (max-width: 1100px) {
      .adopt-feed, .match-results { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 720px) {
      .match-grid, .match-results, .adopt-feed { grid-template-columns: 1fr; }
      .feed-tools, .search-input { max-width: none; width: 100%; align-items: stretch; }
      .pills { justify-content: flex-start; }
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
  readonly speciesOptions = [
    { value: '', label: 'Toutes' },
    { value: 'chien', label: 'Chiens' },
    { value: 'chat', label: 'Chats' },
    { value: 'lapin', label: 'Lapins' },
    { value: 'oiseau', label: 'Oiseaux' }
  ];
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

  get feedLabel(): string {
    const count = this.visible.length;
    if (!count) return 'Aucune fiche à afficher';
    return count === 1 ? '1 fiche' : `${count} fiches`;
  }

  get visible(): AdoptionPost[] {
    const query = this.search.trim().toLowerCase();
    const filtered = this.posts.filter(post => {
      if (!speciesMatches(post.species, this.species)) return false;
      if (!query) return true;
      const haystack = `${post.animalName || ''} ${post.title} ${post.breed || ''} ${post.areaLabel || ''}`.toLowerCase();
      return haystack.includes(query);
    });
    if (!this.scores.size) return filtered;
    return [...filtered].sort((a, b) => (this.scores.get(b.id)?.score ?? -1) - (this.scores.get(a.id)?.score ?? -1));
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

  tags(post: AdoptionPost): string[] {
    return (post.personality || '').split(/[,;]/).map(tag => tag.trim()).filter(Boolean).slice(0, 3);
  }

  tone(id: number): string {
    return ['bg-orange', 'bg-purple', 'bg-blue', 'bg-yellow', 'bg-green'][id % 5];
  }
}
