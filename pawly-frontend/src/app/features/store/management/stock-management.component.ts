import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StoreService, ProductSummary } from '../../../core/services/store.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-stock-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">📦 Stock Management</div>
          <div class="page-sub">Update product stock and availability</div>
        </div>
      </div>

      <!-- Filter -->
      <div class="filter-bar">
        <input class="form-control search-input" type="text" placeholder="🔍 Search…"
               [(ngModel)]="search" (input)="onSearch()" />
        <select class="form-control" style="width:auto" [(ngModel)]="filterAvail" (change)="onSearch()">
          <option value="">All status</option>
          <option value="true">Available</option>
          <option value="false">Unavailable</option>
        </select>
      </div>

      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      } @else {
        <div class="card">
          @if (filtered.length === 0) {
            <div class="empty-state">
              <div class="empty-icon">📦</div>
              <div class="empty-title">No products found</div>
            </div>
          } @else {
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>Category</th>
                    <th>Price</th>
                    <th>Stock</th>
                    <th>Available</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (p of filtered; track p.id) {
                    <tr>
                      <td>
                        <div class="product-cell">
                          @if (p.primaryImageUrl) {
                            <img class="cell-img" [src]="p.primaryImageUrl" [alt]="p.name" />
                          } @else {
                            <div class="cell-img-placeholder">🐾</div>
                          }
                          <div>
                            <div class="cell-name">{{ p.name }}</div>
                            @if (p.targetSpecies) {
                              <div class="cell-sub">{{ p.targetSpecies }}</div>
                            }
                          </div>
                        </div>
                      </td>
                      <td>{{ p.categoryName }}</td>
                      <td>{{ p.price | currency:'EUR':'symbol':'1.2-2' }}</td>
                      <td>
                        @if (editingStockId === p.id) {
                          <div class="stock-edit">
                            <input type="number" class="form-control stock-input"
                                   [(ngModel)]="editStockVal" min="0" />
                            <button class="btn btn-primary btn-xs" (click)="saveStock(p)">✓</button>
                            <button class="btn btn-ghost btn-xs" (click)="editingStockId = null">✕</button>
                          </div>
                        } @else {
                          <span class="stock-value"
                                [class.low]="(p.stockQuantity ?? 0) <= 3"
                                [class.out]="(p.stockQuantity ?? 0) === 0">
                            {{ p.stockQuantity ?? '—' }}
                          </span>
                          <button class="btn btn-ghost btn-xs ml-4" (click)="startStockEdit(p)">✏️</button>
                        }
                      </td>
                      <td>
                        <label class="toggle-switch">
                          <input type="checkbox"
                                 [checked]="p.isAvailable"
                                 [disabled]="togglingId === p.id"
                                 (change)="toggleAvailability(p)" />
                          <span class="slider"></span>
                        </label>
                      </td>
                      <td>
                        <span class="badge" [ngClass]="p.isAvailable ? 'badge-active' : 'badge-inactive'">
                          {{ p.isAvailable ? 'Available' : 'Hidden' }}
                        </span>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            <!-- Pagination -->
            @if (totalPages > 1) {
              <div class="pagination-bar">
                <button class="btn btn-ghost btn-sm" [disabled]="page === 0" (click)="prev()">← Prev</button>
                <span>Page {{ page + 1 }} / {{ totalPages }}</span>
                <button class="btn btn-ghost btn-sm" [disabled]="page >= totalPages - 1" (click)="next()">Next →</button>
              </div>
            }
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .product-cell { display: flex; align-items: center; gap: 10px; }
    .cell-img { width: 40px; height: 40px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
    .cell-img-placeholder { width: 40px; height: 40px; border-radius: 8px; background: var(--neutral); display: flex; align-items: center; justify-content: center; font-size: 18px; flex-shrink: 0; }
    .cell-name { font-weight: 600; font-size: 14px; }
    .cell-sub  { font-size: 11px; color: var(--text-soft); text-transform: capitalize; }

    .stock-value { font-weight: 700; font-size: 15px; }
    .stock-value.low { color: #ff9800; }
    .stock-value.out { color: var(--danger); }
    .ml-4 { margin-left: 4px; }

    .stock-edit { display: flex; align-items: center; gap: 4px; }
    .stock-input { width: 70px; padding: 4px 8px; }

    /* Toggle switch */
    .toggle-switch { position: relative; display: inline-block; width: 40px; height: 22px; cursor: pointer; }
    .toggle-switch input { opacity: 0; width: 0; height: 0; }
    .slider {
      position: absolute; inset: 0; background: var(--border); border-radius: 22px; transition: .2s;
      &::before { content: ''; position: absolute; height: 16px; width: 16px; left: 3px; bottom: 3px; background: #fff; border-radius: 50%; transition: .2s; }
    }
    .toggle-switch input:checked + .slider { background: var(--primary); }
    .toggle-switch input:checked + .slider::before { transform: translateX(18px); }

    .pagination-bar { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 16px; font-size: 13px; }
  `]
})
export class StockManagementComponent implements OnInit {
  allProducts: ProductSummary[] = [];
  filtered:    ProductSummary[] = [];
  loading    = true;
  search     = '';
  filterAvail = '';
  page       = 0;
  totalPages = 1;
  private readonly size = 50;

  editingStockId: number | null = null;
  editStockVal    = 0;
  togglingId:     number | null = null;

  constructor(private storeService: StoreService, public auth: AuthService) {}

  ngOnInit(): void { this.loadAll(); }

  loadAll(): void {
    this.storeService.getProducts(this.page, this.size, undefined, undefined, undefined, undefined, undefined, 'name,asc').subscribe({
      next: res => {
        this.allProducts = res.content;
        this.totalPages  = res.totalPages;
        this.applyFilter();
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  onSearch(): void { this.applyFilter(); }

  applyFilter(): void {
    this.filtered = this.allProducts.filter(p => {
      const matchSearch = !this.search || p.name.toLowerCase().includes(this.search.toLowerCase());
      const matchAvail  = !this.filterAvail || String(p.isAvailable) === this.filterAvail;
      return matchSearch && matchAvail;
    });
  }

  startStockEdit(p: ProductSummary): void {
    this.editingStockId = p.id;
    this.editStockVal   = p.stockQuantity ?? 0;
  }

  saveStock(p: ProductSummary): void {
    this.storeService.updateStock(p.id, this.editStockVal).subscribe({
      next: updated => {
        const idx = this.allProducts.findIndex(x => x.id === p.id);
        if (idx >= 0) this.allProducts[idx] = { ...this.allProducts[idx], stockQuantity: updated.stockQuantity };
        this.applyFilter();
        this.editingStockId = null;
      },
      error: err => alert(err.error?.message ?? 'Update failed')
    });
  }

  toggleAvailability(p: ProductSummary): void {
    this.togglingId = p.id;
    this.storeService.updateAvailability(p.id, !p.isAvailable).subscribe({
      next: updated => {
        const idx = this.allProducts.findIndex(x => x.id === p.id);
        if (idx >= 0) this.allProducts[idx] = { ...this.allProducts[idx], isAvailable: updated.isAvailable };
        this.applyFilter();
        this.togglingId = null;
      },
      error: err => { this.togglingId = null; alert(err.error?.message ?? 'Toggle failed'); }
    });
  }

  prev(): void { this.page--; this.loadAll(); }
  next(): void { this.page++; this.loadAll(); }
}
