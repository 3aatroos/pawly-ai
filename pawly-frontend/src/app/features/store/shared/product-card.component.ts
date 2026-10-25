import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ProductSummary } from '../../../core/services/store.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="product-card" [class.card-unavailable]="!product.isAvailable">
      <a [routerLink]="['/store/products', product.id]" class="card-image-link">
        <div class="card-image">
          @if (product.primaryImageUrl) {
            <img [src]="product.primaryImageUrl" [alt]="product.name" loading="lazy" />
          } @else {
            <div class="card-image-placeholder">🐾</div>
          }
          @if (!product.isAvailable) {
            <div class="card-overlay">Unavailable</div>
          }
          @if (product.stockQuantity !== null && product.stockQuantity! <= 3 && product.isAvailable) {
            <div class="card-low-stock">Only {{ product.stockQuantity }} left</div>
          }
        </div>
      </a>

      <div class="card-body">
        @if (product.targetSpecies) {
          <span class="card-species">{{ speciesEmoji(product.targetSpecies) }} {{ product.targetSpecies }}</span>
        }

        <a [routerLink]="['/store/products', product.id]" class="card-name">
          {{ product.name }}
        </a>

        <div class="card-rating">
          <span class="stars">{{ stars(product.averageRating) }}</span>
          <span class="rating-count">({{ product.reviewCount }})</span>
        </div>

        <div class="card-footer">
          <span class="card-price">{{ product.price | currency:'EUR':'symbol':'1.2-2' }}</span>

          @if (canManage) {
            <div class="card-manage-actions">
              <button class="btn btn-ghost btn-xs" (click)="edit.emit(product)">✏️</button>
              <button class="btn btn-danger btn-xs" (click)="remove.emit(product)">🗑️</button>
            </div>
          } @else if (product.isAvailable) {
            <button
              class="btn btn-primary btn-sm add-btn"
              [disabled]="adding"
              (click)="onAdd()">
              @if (adding) { <span class="spinner-xs"></span> } @else { + Cart }
            </button>
          }
        </div>

        @if (showStock && product.stockQuantity !== null) {
          <div class="card-stock">
            Stock: <strong>{{ product.stockQuantity }}</strong>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .product-card {
      background: var(--white);
      border-radius: 14px;
      border: 1px solid var(--border);
      overflow: hidden;
      transition: transform 0.15s, box-shadow 0.15s;
      display: flex;
      flex-direction: column;

      &:hover { transform: translateY(-3px); box-shadow: var(--shadow); }
      &.card-unavailable { opacity: 0.65; }
    }

    .card-image-link { display: block; flex-shrink: 0; }
    .card-image {
      position: relative;
      height: 180px;
      background: var(--neutral);
      overflow: hidden;

      img { width: 100%; height: 100%; object-fit: cover; }
    }

    .card-image-placeholder {
      width: 100%; height: 100%;
      display: flex; align-items: center; justify-content: center;
      font-size: 52px;
    }

    .card-overlay {
      position: absolute; inset: 0;
      background: rgba(0,0,0,.45);
      color: #fff; font-weight: 700; font-size: 13px;
      display: flex; align-items: center; justify-content: center;
    }

    .card-low-stock {
      position: absolute; top: 8px; right: 8px;
      background: #ff9800; color: #fff;
      font-size: 10px; font-weight: 700;
      padding: 3px 8px; border-radius: 99px;
    }

    .card-body {
      padding: 14px 16px;
      display: flex; flex-direction: column; flex: 1; gap: 6px;
    }

    .card-species {
      font-size: 11px; color: var(--text-soft); text-transform: capitalize;
    }

    .card-name {
      font-weight: 700; font-size: 14px; color: var(--text);
      text-decoration: none; line-height: 1.3;
      display: -webkit-box; -webkit-line-clamp: 2;
      -webkit-box-orient: vertical; overflow: hidden;
      &:hover { color: var(--primary); }
    }

    .card-rating {
      display: flex; align-items: center; gap: 4px; font-size: 12px;
      .stars { color: #f59e0b; letter-spacing: 1px; }
      .rating-count { color: var(--text-soft); }
    }

    .card-footer {
      display: flex; align-items: center;
      justify-content: space-between; margin-top: auto; padding-top: 8px;
    }

    .card-price {
      font-size: 17px; font-weight: 800; color: var(--primary);
    }

    .add-btn { border-radius: 8px; }
    .card-manage-actions { display: flex; gap: 4px; }

    .card-stock { font-size: 11px; color: var(--text-soft); }

    .spinner-xs {
      display: inline-block; width: 12px; height: 12px;
      border: 2px solid rgba(255,255,255,.4);
      border-top-color: #fff; border-radius: 50%;
      animation: spin .7s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
  `]
})
export class ProductCardComponent {
  @Input({ required: true }) product!: ProductSummary;
  @Input() showStock = false;
  @Input() adding = false;
  @Output() addToCart = new EventEmitter<ProductSummary>();
  @Output() edit   = new EventEmitter<ProductSummary>();
  @Output() remove = new EventEmitter<ProductSummary>();

  constructor(public auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.isVet() || this.auth.isAdmin();
  }

  onAdd(): void {
    this.addToCart.emit(this.product);
  }

  stars(rating: number): string {
    const full  = Math.round(rating);
    return '★'.repeat(full) + '☆'.repeat(5 - full);
  }

  speciesEmoji(s: string): string {
    const m: Record<string, string> = {
      dog: '🐕', cat: '🐈', rabbit: '🐇', bird: '🦜', all: '🐾'
    };
    return m[s?.toLowerCase()] ?? '🐾';
  }
}
