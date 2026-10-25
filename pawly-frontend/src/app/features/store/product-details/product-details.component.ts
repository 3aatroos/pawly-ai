import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StoreService, ProductDetail } from '../../../core/services/store.service';
import { CartService } from '../../../core/services/cart.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ProductReviewComponent } from '../shared/product-review.component';

@Component({
  selector: 'app-product-details',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, ProductReviewComponent],
  template: `
    <div class="fade-up">
      <!-- Breadcrumb -->
      <nav class="breadcrumb">
        <a routerLink="/store/products">Store</a>
        <span>›</span>
        @if (product) { <span>{{ product.name }}</span> }
      </nav>

      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      } @else if (!product) {
        <div class="card">
          <div class="empty-state">
            <div class="empty-icon">🔍</div>
            <div class="empty-title">Product not found</div>
            <a routerLink="/store/products" class="btn btn-primary btn-sm" style="margin-top:14px">← Back to store</a>
          </div>
        </div>
      } @else {
        <div class="product-layout">
          <!-- Gallery -->
          <div class="product-gallery">
            <div class="main-image">
              @if (activeImage) {
                <img [src]="activeImage" [alt]="product.name" />
              } @else {
                <div class="image-placeholder">🐾</div>
              }
              @if (!product.isAvailable) {
                <div class="unavailable-overlay">Unavailable</div>
              }
            </div>
            @if (product.images.length > 1) {
              <div class="thumbnails">
                @for (img of product.images; track img.id) {
                  <div class="thumb"
                       [class.active]="activeImage === img.imageUrl"
                       (click)="activeImage = img.imageUrl">
                    <img [src]="img.imageUrl" [alt]="product.name" />
                  </div>
                }
              </div>
            }
          </div>

          <!-- Info -->
          <div class="product-info">
            <div class="product-meta-top">
              <span class="product-category">{{ product.categoryName }}</span>
              @if (product.targetSpecies) {
                <span class="product-species">{{ speciesEmoji(product.targetSpecies) }} {{ product.targetSpecies }}</span>
              }
            </div>

            <h1 class="product-name">{{ product.name }}</h1>

            <div class="product-rating">
              <span class="stars">{{ stars(product.averageRating) }}</span>
              <span class="rating-val">{{ product.averageRating | number:'1.1-1' }}</span>
              <span class="rating-count">({{ product.reviewCount }} review{{ product.reviewCount !== 1 ? 's' : '' }})</span>
            </div>

            <div class="product-price">
              {{ product.price | currency:'EUR':'symbol':'1.2-2' }}
            </div>

            @if (product.description) {
              <p class="product-description">{{ product.description }}</p>
            }

            <!-- Stock / availability -->
            <div class="product-stock">
              @if (!product.isAvailable) {
                <span class="badge badge-rejected">Out of stock</span>
              } @else if (product.stockQuantity !== null && product.stockQuantity <= 5) {
                <span class="badge badge-pending">Only {{ product.stockQuantity }} left!</span>
              } @else {
                <span class="badge badge-active">In stock ✓</span>
              }

              @if ((auth.isVet() || auth.isAdmin()) && product.stockQuantity !== null) {
                <span class="stock-detail">{{ product.stockQuantity }} units</span>
              }
            </div>

            <!-- Add to cart -->
            @if (product.isAvailable) {
              <div class="add-to-cart-row">
                <div class="qty-control">
                  <button class="qty-btn" [disabled]="qty <= 1" (click)="qty = qty - 1">−</button>
                  <span class="qty-val">{{ qty }}</span>
                  <button class="qty-btn"
                    [disabled]="product.stockQuantity !== null && qty >= product.stockQuantity"
                    (click)="qty = qty + 1">+</button>
                </div>
                <button class="btn btn-primary add-btn" [disabled]="adding" (click)="addToCart()">
                  @if (adding) { <span class="spinner"></span> }
                  @else { 🛒 Add to cart }
                </button>
              </div>
            }

            @if (addSuccess) {
              <div class="alert alert-success" style="margin-top:10px">✅ Added to cart!</div>
            }
            @if (addError) {
              <div class="alert alert-danger" style="margin-top:10px">{{ addError }}</div>
            }

            <!-- Manage actions (VET/ADMIN) -->
            @if (canManage) {
              <div class="manage-actions">
                <a [routerLink]="['/store/manage/products', product.id, 'edit']" class="btn btn-ghost btn-sm">✏️ Edit</a>
                <button class="btn btn-danger btn-sm" (click)="deleteProduct()">🗑️ Delete</button>
              </div>
            }

            <div class="product-meta-bottom">
              <span>Sold by: {{ product.createdByName }}</span>
            </div>
          </div>
        </div>

        <!-- Reviews -->
        <app-product-review [productId]="product.id"></app-product-review>
      }
    </div>
  `,
  styles: [`
    .breadcrumb {
      display: flex; align-items: center; gap: 8px;
      font-size: 13px; color: var(--text-soft); margin-bottom: 24px;
      a { color: var(--primary); text-decoration: none; &:hover { text-decoration: underline; } }
    }

    .product-layout {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 40px;
      align-items: start;
      margin-bottom: 40px;
    }

    @media (max-width: 768px) {
      .product-layout { grid-template-columns: 1fr; gap: 24px; }
    }

    .main-image {
      position: relative;
      border-radius: 16px; overflow: hidden;
      background: var(--neutral);
      aspect-ratio: 1 / 1;

      img { width: 100%; height: 100%; object-fit: cover; }
    }

    .image-placeholder {
      width: 100%; height: 100%; aspect-ratio: 1/1;
      display: flex; align-items: center; justify-content: center;
      font-size: 80px;
    }

    .unavailable-overlay {
      position: absolute; inset: 0;
      background: rgba(0,0,0,.4); color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 18px;
    }

    .thumbnails { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
    .thumb {
      width: 60px; height: 60px; border-radius: 8px; overflow: hidden;
      border: 2px solid transparent; cursor: pointer;
      img { width: 100%; height: 100%; object-fit: cover; }
      &.active { border-color: var(--primary); }
      &:hover { border-color: var(--primary-light); }
    }

    .product-meta-top { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
    .product-category { font-size: 12px; font-weight: 600; color: var(--text-soft); text-transform: uppercase; letter-spacing: .07em; }
    .product-species { font-size: 12px; color: var(--text-soft); }

    .product-name { font-size: 26px; font-weight: 800; line-height: 1.2; margin-bottom: 10px; }

    .product-rating {
      display: flex; align-items: center; gap: 6px; margin-bottom: 14px;
      .stars { color: #f59e0b; font-size: 18px; }
      .rating-val { font-weight: 700; font-size: 15px; }
      .rating-count { font-size: 13px; color: var(--text-soft); }
    }

    .product-price { font-size: 32px; font-weight: 800; color: var(--primary); margin-bottom: 16px; }

    .product-description { font-size: 14px; line-height: 1.7; color: var(--text-soft); margin-bottom: 18px; }

    .product-stock {
      display: flex; align-items: center; gap: 10px; margin-bottom: 20px;
      .stock-detail { font-size: 12px; color: var(--text-soft); }
    }

    .add-to-cart-row { display: flex; gap: 12px; align-items: center; margin-bottom: 4px; }

    .qty-control { display: flex; align-items: center; gap: 10px; }
    .qty-btn {
      width: 34px; height: 34px; border-radius: 50%;
      border: 1px solid var(--border); background: var(--neutral);
      font-size: 18px; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      &:hover:not(:disabled) { background: var(--primary-light); color: var(--primary); }
      &:disabled { opacity: .4; cursor: not-allowed; }
    }
    .qty-val { font-weight: 700; font-size: 16px; min-width: 24px; text-align: center; }

    .add-btn { padding: 12px 24px; font-size: 15px; border-radius: 12px; }

    .manage-actions { display: flex; gap: 8px; margin-top: 14px; }

    .product-meta-bottom { margin-top: 20px; font-size: 12px; color: var(--text-soft); }
  `]
})
export class ProductDetailsComponent implements OnInit {
  product:     ProductDetail | null = null;
  activeImage: string | null = null;
  loading = true;
  adding  = false;
  qty     = 1;
  addSuccess = false;
  addError:   string | null = null;

  constructor(
    private route:        ActivatedRoute,
    private storeService: StoreService,
    private cartService:  CartService,
    public  auth:         AuthService
  ) {}

  get canManage(): boolean {
    return this.auth.isVet() || this.auth.isAdmin();
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.storeService.getProductById(id).subscribe({
      next: p => {
        this.product = p;
        this.activeImage = p.images.find(i => i.isPrimary)?.imageUrl
            ?? p.images[0]?.imageUrl ?? null;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  addToCart(): void {
    if (!this.product) return;
    this.adding   = true;
    this.addError = null;
    this.cartService.addItem({ productId: this.product.id, quantity: this.qty }).subscribe({
      next: () => {
        this.adding     = false;
        this.addSuccess = true;
        setTimeout(() => this.addSuccess = false, 2500);
      },
      error: err => {
        this.adding   = false;
        this.addError = err.error?.message ?? 'Failed to add to cart';
      }
    });
  }

  deleteProduct(): void {
    if (!this.product || !confirm(`Delete "${this.product.name}"?`)) return;
    this.storeService.deleteProduct(this.product.id).subscribe({
      next: () => window.history.back(),
      error: err => alert(err.error?.message ?? 'Failed to delete product')
    });
  }

  stars(r: number): string {
    const f = Math.round(r);
    return '★'.repeat(f) + '☆'.repeat(5 - f);
  }

  speciesEmoji(s: string): string {
    const m: Record<string, string> = { dog: '🐕', cat: '🐈', rabbit: '🐇', bird: '🦜', all: '🐾' };
    return m[s?.toLowerCase()] ?? '🐾';
  }
}
