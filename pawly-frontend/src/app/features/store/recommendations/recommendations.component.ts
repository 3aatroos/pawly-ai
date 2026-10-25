import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService, ProductSummary, ProductRecommendation } from '../../../core/services/store.service';
import { CartService } from '../../../core/services/cart.service';
import { ProductCardComponent } from '../shared/product-card.component';

@Component({
  selector: 'app-recommendations',
  standalone: true,
  imports: [CommonModule, ProductCardComponent],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">✨ For You</div>
          <div class="page-sub">{{ subtitle }}</div>
        </div>
      </div>

      <!-- Initial state — nothing loaded yet -->
      @if (!loaded && !loading) {
        <div class="card hero-card">
          <div class="hero-icon">🐾</div>
          <div class="hero-title">Discover products tailored to your pets</div>
          <div class="hero-sub">
            Our recommendation engine analyses your pets' profiles and purchase history
            to surface the most relevant products for you.
          </div>
          <button class="btn btn-primary btn-lg" (click)="load()">
            🔍 Find my recommendations
          </button>
        </div>
      }

      <!-- Loading -->
      @if (loading) {
        <div class="card hero-card">
          <div class="hero-icon spin">🔍</div>
          <div class="hero-title">Looking for the best picks…</div>
          <div class="hero-sub">Scoring products against your profile</div>
        </div>
      }

      <!-- Error -->
      @if (loaded && loadError) {
        <div class="card hero-card">
          <div class="hero-icon">⚠️</div>
          <div class="hero-title">Could not load recommendations</div>
          <div class="hero-sub">Make sure the AI service is running on port 8000</div>
          <button class="btn btn-primary btn-lg" (click)="load()">Try again</button>
        </div>
      }

      <!-- Empty -->
      @if (loaded && !loadError && products.length === 0) {
        <div class="card hero-card">
          <div class="hero-icon">🐾</div>
          <div class="hero-title">No recommendations yet</div>
          <div class="hero-sub">Add pets to your profile and browse the store to get personalised suggestions</div>
          <button class="btn btn-primary btn-lg" (click)="load()">Try again</button>
        </div>
      }

      <!-- Results -->
      @if (loaded && !loadError && products.length > 0) {
        <div class="results-header">
          <span class="results-meta">{{ products.length }} product{{ products.length !== 1 ? 's' : '' }} found · <em>{{ subtitle }}</em></span>
          <button class="btn btn-ghost btn-sm" (click)="load()">🔄 Refresh</button>
        </div>

        @if (addedName) {
          <div class="alert alert-success toast-msg">✅ {{ addedName }} added to cart!</div>
        }

        <div class="reco-grid">
          @for (p of products; track p.id) {
            <div class="reco-item">
              <app-product-card
                [product]="p"
                [adding]="addingId === p.id"
                (addToCart)="addToCart($event)">
              </app-product-card>
              @if (reasons[p.id]) {
                <p class="reco-reason">💡 {{ reasons[p.id] }}</p>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .hero-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding: 48px 32px;
      gap: 14px;
    }
    .hero-icon { font-size: 52px; }
    .hero-icon.spin { animation: spin 1.2s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .hero-title { font-size: 20px; font-weight: 700; }
    .hero-sub { font-size: 14px; color: var(--text-soft); max-width: 420px; line-height: 1.6; }
    .btn-lg { padding: 14px 32px; font-size: 15px; border-radius: 12px; margin-top: 8px; }
    .results-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;
    }
    .results-meta { font-size: 13px; color: var(--text-soft); }
    .results-meta em { font-style: normal; color: var(--primary); font-weight: 600; }
    .reco-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 18px;
    }
    .reco-item {}
    .toast-msg {
      position: fixed; top: 80px; right: 24px;
      z-index: 999; padding: 12px 20px;
      box-shadow: var(--shadow); border-radius: 12px;
    }
    .reco-reason {
      margin: 8px 4px 0;
      color: var(--text-soft);
      font-size: 12px;
      line-height: 1.4;
      background: var(--neutral);
      border-radius: 8px;
      padding: 6px 10px;
    }
  `]
})
export class RecommendationsComponent {
  products: ProductSummary[] = [];
  reasons:  Record<number, string> = {};
  subtitle  = 'Based on your pets and the catalogue';
  loading   = false;
  loaded    = false;
  loadError = false;
  addingId: number | null = null;
  addedName: string | null = null;

  constructor(
    private storeService: StoreService,
    private cartService:  CartService
  ) {}

  load(): void {
    this.loading   = true;
    this.loaded    = false;
    this.loadError = false;
    this.products  = [];

    this.storeService.getRecommendations(12).subscribe({
      next: rows => {
        this.products = rows.map(row => this.toSummary(row));
        this.reasons  = Object.fromEntries(rows.map(row => [row.productId, row.reason]));
        this.subtitle = rows.some(row => row.source === 'qlora')
          ? '🤖 Scored by the Pawly QLoRA model'
          : '📊 Based on your pets and the catalogue';
        this.loading = false;
        this.loaded  = true;
      },
      error: () => {
        this.loading   = false;
        this.loaded    = true;
        this.loadError = true;
      }
    });
  }

  private toSummary(row: ProductRecommendation): ProductSummary {
    return {
      id: row.productId,
      categoryId: row.categoryId,
      categoryName: row.categoryName,
      name: row.name,
      price: row.price,
      isAvailable: row.isAvailable,
      averageRating: row.averageRating,
      reviewCount: row.reviewCount,
      targetSpecies: row.targetSpecies,
      primaryImageUrl: row.primaryImageUrl,
      stockQuantity: null,
      createdAt: ''
    };
  }

  addToCart(p: ProductSummary): void {
    this.addingId = p.id;
    this.cartService.addItem({ productId: p.id, quantity: 1 }).subscribe({
      next: () => {
        this.addingId  = null;
        this.addedName = p.name;
        setTimeout(() => this.addedName = null, 2500);
      },
      error: err => {
        this.addingId = null;
        alert(err.error?.message ?? 'Failed to add to cart');
      }
    });
  }
}
