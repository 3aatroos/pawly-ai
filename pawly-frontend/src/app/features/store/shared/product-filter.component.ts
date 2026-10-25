import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StoreService, Category } from '../../../core/services/store.service';

export interface FilterState {
  search:     string;
  categoryId: number | null;
  species:    string;
  minPrice:   number | null;
  maxPrice:   number | null;
  sort:       string;
}

@Component({
  selector: 'app-product-filter',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="filter-panel">
      <div class="filter-row">
        <!-- Search -->
        <div class="search-wrap">
          <span class="search-icon">🔍</span>
          <input
            type="text"
            class="form-control search-input"
            placeholder="Search products…"
            [(ngModel)]="state.search"
            (input)="emitDebounced()" />
          @if (state.search) {
            <button class="search-clear" (click)="clearSearch()">✕</button>
          }
        </div>

        <!-- Category -->
        <select class="form-control filter-select" [(ngModel)]="state.categoryId" (change)="emit()">
          <option [ngValue]="null">All categories</option>
          @for (cat of categories; track cat.id) {
            <option [ngValue]="cat.id">{{ cat.name }}</option>
          }
        </select>

        <!-- Species -->
        <select class="form-control filter-select" [(ngModel)]="state.species" (change)="emit()">
          <option value="">All species</option>
          <option value="dog">🐕 Dogs</option>
          <option value="cat">🐈 Cats</option>
          <option value="rabbit">🐇 Rabbits</option>
          <option value="bird">🦜 Birds</option>
          <option value="all">🐾 All pets</option>
        </select>

        <!-- Sort -->
        <select class="form-control filter-select" [(ngModel)]="state.sort" (change)="emit()">
          <option value="createdAt,desc">Newest first</option>
          <option value="price,asc">Price: low → high</option>
          <option value="price,desc">Price: high → low</option>
          <option value="averageRating,desc">Best rated</option>
        </select>

        @if (hasActiveFilters) {
          <button class="btn btn-ghost btn-sm" (click)="reset()">✕ Reset</button>
        }
      </div>

      <!-- Price range -->
      <div class="price-row">
        <span class="price-label">Price range:</span>
        <input type="number" class="form-control price-input" placeholder="Min"
               [(ngModel)]="state.minPrice" (change)="emit()" min="0" />
        <span class="price-sep">—</span>
        <input type="number" class="form-control price-input" placeholder="Max"
               [(ngModel)]="state.maxPrice" (change)="emit()" min="0" />
        <span class="price-label">EUR</span>
      </div>
    </div>
  `,
  styles: [`
    .filter-panel {
      background: var(--white);
      border-radius: var(--radius);
      padding: 16px 20px;
      box-shadow: var(--shadow-sm);
      margin-bottom: 20px;
    }

    .filter-row {
      display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 12px;
    }

    .search-wrap {
      position: relative; flex: 1; min-width: 200px;
    }

    .search-icon {
      position: absolute; left: 12px; top: 50%; transform: translateY(-50%);
      font-size: 14px; pointer-events: none;
    }

    .search-input { padding-left: 34px; padding-right: 28px; }

    .search-clear {
      position: absolute; right: 10px; top: 50%; transform: translateY(-50%);
      background: none; border: none; cursor: pointer; color: var(--text-soft);
      font-size: 12px; padding: 2px;
      &:hover { color: var(--danger); }
    }

    .filter-select { width: auto; min-width: 140px; }

    .price-row {
      display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
    }

    .price-label { font-size: 12px; color: var(--text-soft); white-space: nowrap; }
    .price-input { width: 90px; }
    .price-sep   { color: var(--text-soft); }
  `]
})
export class ProductFilterComponent implements OnInit {
  @Input() state: FilterState = {
    search: '', categoryId: null, species: '',
    minPrice: null, maxPrice: null, sort: 'createdAt,desc'
  };
  @Output() filterChange = new EventEmitter<FilterState>();

  categories: Category[] = [];
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(private storeService: StoreService) {}

  ngOnInit(): void {
    this.storeService.getCategories().subscribe({
      next: cats => this.categories = cats,
      error: () => {}
    });
  }

  emit(): void {
    this.filterChange.emit({ ...this.state });
  }

  emitDebounced(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.emit(), 350);
  }

  clearSearch(): void {
    this.state.search = '';
    this.emit();
  }

  reset(): void {
    this.state = {
      search: '', categoryId: null, species: '',
      minPrice: null, maxPrice: null, sort: 'createdAt,desc'
    };
    this.emit();
  }

  get hasActiveFilters(): boolean {
    return !!(
      this.state.search || this.state.categoryId ||
      this.state.species || this.state.minPrice !== null || this.state.maxPrice !== null
    );
  }
}
