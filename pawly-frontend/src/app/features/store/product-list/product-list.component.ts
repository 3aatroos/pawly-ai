import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { StoreService, ProductSummary } from '../../../core/services/store.service';
import { CartService } from '../../../core/services/cart.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ProductCardComponent } from '../shared/product-card.component';
import { ProductFilterComponent, FilterState } from '../shared/product-filter.component';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ProductCardComponent, ProductFilterComponent],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">🛍️ Pet Store</div>
          <div class="page-sub">{{ totalElements }} products available</div>
        </div>
        <div class="header-actions">
          @if (auth.isVet() || auth.isAdmin()) {
            <a routerLink="/store/manage/products/new" class="btn btn-primary btn-sm">+ Add Product</a>
          }
          <a routerLink="/store/cart" class="btn btn-ghost btn-sm cart-btn">
            🛒 Cart
            @if (cartService.itemCount() > 0) {
              <span class="cart-badge">{{ cartService.itemCount() }}</span>
            }
          </a>
        </div>
      </div>

      <app-product-filter [state]="filter" (filterChange)="onFilter($event)"></app-product-filter>

      @if (addedName) {
        <div class="alert alert-success toast-msg">
          ✅ {{ addedName }} added to cart!
        </div>
      }

      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      } @else if (products.length === 0) {
        <div class="card">
          <div class="empty-state">
            <div class="empty-icon">🔍</div>
            <div class="empty-title">No products found</div>
            <div class="empty-sub">Try adjusting your search or filters</div>
            <button class="btn btn-ghost btn-sm" style="margin-top:14px" (click)="resetFilter()">Clear filters</button>
          </div>
        </div>
      } @else {
        <div class="products-grid">
          @for (p of products; track p.id) {
            <app-product-card
              [product]="p"
              [showStock]="auth.isVet() || auth.isAdmin()"
              [adding]="addingId === p.id"
              (addToCart)="addToCart($event)"
              (edit)="goEdit($event)"
              (remove)="removeProduct($event)">
            </app-product-card>
          }
        </div>

        <!-- Pagination -->
        @if (totalPages > 1) {
          <div class="pagination-bar">
            <button class="btn btn-ghost btn-sm" [disabled]="page === 0" (click)="prevPage()">← Prev</button>

            <div class="page-nums">
              @for (p of pageNumbers; track p) {
                <button
                  class="page-num"
                  [class.active]="p === page"
                  (click)="goToPage(p)">{{ p + 1 }}</button>
              }
            </div>

            <button class="btn btn-ghost btn-sm" [disabled]="page >= totalPages - 1" (click)="nextPage()">Next →</button>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .cart-btn { position: relative; }
    .cart-badge {
      position: absolute; top: -6px; right: -6px;
      background: var(--danger); color: #fff;
      font-size: 10px; font-weight: 700;
      padding: 2px 5px; border-radius: 99px;
    }

    .toast-msg {
      position: fixed; top: 80px; right: 24px;
      z-index: 999; padding: 12px 20px;
      box-shadow: var(--shadow); border-radius: 12px;
      animation: slideIn .2s ease;
    }
    @keyframes slideIn { from { opacity: 0; transform: translateX(20px); } to { opacity: 1; transform: translateX(0); } }

    .products-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 18px;
      margin-bottom: 24px;
    }

    @media (max-width: 500px) {
      .products-grid { grid-template-columns: 1fr 1fr; gap: 12px; }
    }

    .pagination-bar {
      display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 8px;
    }

    .page-nums { display: flex; gap: 4px; }
    .page-num {
      width: 32px; height: 32px; border-radius: 8px;
      border: 1px solid var(--border); background: var(--white);
      font-size: 13px; cursor: pointer;
      &.active { background: var(--primary); color: #fff; border-color: var(--primary); }
      &:hover:not(.active) { background: var(--neutral); }
    }
  `]
})
export class ProductListComponent implements OnInit {
  products:      ProductSummary[] = [];
  loading        = true;
  page           = 0;
  totalPages     = 1;
  totalElements  = 0;
  addingId:      number | null = null;
  addedName:     string | null = null;
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  filter: FilterState = {
    search: '', categoryId: null, species: '',
    minPrice: null, maxPrice: null, sort: 'createdAt,desc'
  };

  readonly size = 12;

  constructor(
    private storeService: StoreService,
    public  cartService:  CartService,
    public  auth:         AuthService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.storeService.getProducts(
      this.page, this.size,
      this.filter.categoryId ?? undefined,
      this.filter.species    || undefined,
      this.filter.search     || undefined,
      this.filter.minPrice   ?? undefined,
      this.filter.maxPrice   ?? undefined,
      this.filter.sort
    ).subscribe({
      next: res => {
        this.products     = res.content;
        this.totalPages   = res.totalPages;
        this.totalElements = res.totalElements;
        this.loading      = false;
      },
      error: () => { this.loading = false; }
    });
  }

  onFilter(f: FilterState): void {
    this.filter = f;
    this.page   = 0;
    this.load();
  }

  resetFilter(): void {
    this.filter = { search: '', categoryId: null, species: '', minPrice: null, maxPrice: null, sort: 'createdAt,desc' };
    this.page   = 0;
    this.load();
  }

  addToCart(p: ProductSummary): void {
    this.addingId = p.id;
    this.cartService.addItem({ productId: p.id, quantity: 1 }).subscribe({
      next: () => {
        this.addingId = null;
        this.showToast(p.name);
      },
      error: err => {
        this.addingId = null;
        alert(err.error?.message ?? 'Failed to add to cart');
      }
    });
  }

  goEdit(p: ProductSummary): void {
    window.location.href = `/store/manage/products/${p.id}/edit`;
  }

  removeProduct(p: ProductSummary): void {
    if (!confirm(`Delete "${p.name}"? This cannot be undone.`)) return;
    this.storeService.deleteProduct(p.id).subscribe({
      next: () => { this.products = this.products.filter(x => x.id !== p.id); this.totalElements--; },
      error: err => alert(err.error?.message ?? 'Failed to delete product')
    });
  }

  prevPage(): void { this.page--; this.load(); }
  nextPage(): void { this.page++; this.load(); }
  goToPage(p: number): void { this.page = p; this.load(); }

  get pageNumbers(): number[] {
    const start = Math.max(0, this.page - 2);
    const end   = Math.min(this.totalPages - 1, this.page + 2);
    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  }

  private showToast(name: string): void {
    this.addedName = name;
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => { this.addedName = null; }, 2500);
  }
}
