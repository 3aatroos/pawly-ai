import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { StoreService, ProductDetail, Category, CreateProductRequest, UpdateProductRequest } from '../../../core/services/store.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-product-management',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">{{ isEdit ? '✏️ Edit Product' : '+ New Product' }}</div>
          <div class="page-sub">{{ isEdit ? 'Update product information' : 'Add a new product to the store' }}</div>
        </div>
        <a routerLink="/store/products" class="btn btn-ghost btn-sm">← Back to Store</a>
      </div>

      <div class="card" style="max-width:720px">
        @if (loadError) {
          <div class="alert alert-danger">{{ loadError }}</div>
        }

        @if (saveSuccess) {
          <div class="alert alert-success">Product saved successfully!</div>
        }

        <form [formGroup]="form" (ngSubmit)="save()">
          <div class="two-col-form">
            <div class="form-group">
              <label>Product name *</label>
              <input type="text" class="form-control" formControlName="name"
                     placeholder="Royal Canin Adult 4kg"
                     [class.is-invalid]="submitted && form.get('name')?.invalid" />
              @if (submitted && form.get('name')?.invalid) {
                <div class="invalid-feedback">Name is required (max 200 chars)</div>
              }
            </div>

            <div class="form-group">
              <label>Category *</label>
              <select class="form-control" formControlName="categoryId"
                      [class.is-invalid]="submitted && form.get('categoryId')?.invalid">
                <option [ngValue]="null">Select a category…</option>
                @for (cat of categories; track cat.id) {
                  <option [ngValue]="cat.id">{{ cat.name }}</option>
                }
              </select>
              @if (submitted && form.get('categoryId')?.invalid) {
                <div class="invalid-feedback">Category is required</div>
              }
            </div>
          </div>

          <div class="form-group">
            <label>Description</label>
            <textarea class="form-control" rows="4" formControlName="description"
                      placeholder="Describe the product…"></textarea>
          </div>

          <div class="two-col-form">
            <div class="form-group">
              <label>Price (EUR) *</label>
              <input type="number" class="form-control" formControlName="price"
                     placeholder="29.99" step="0.01" min="0.01"
                     [class.is-invalid]="submitted && form.get('price')?.invalid" />
              @if (submitted && form.get('price')?.invalid) {
                <div class="invalid-feedback">Valid price is required</div>
              }
            </div>

            <div class="form-group">
              <label>Stock quantity *</label>
              <input type="number" class="form-control" formControlName="stockQuantity"
                     placeholder="0" min="0"
                     [class.is-invalid]="submitted && form.get('stockQuantity')?.invalid" />
              @if (submitted && form.get('stockQuantity')?.invalid) {
                <div class="invalid-feedback">Stock cannot be negative</div>
              }
            </div>
          </div>

          <div class="two-col-form">
            <div class="form-group">
              <label>Target species</label>
              <select class="form-control" formControlName="targetSpecies">
                <option value="">All species</option>
                <option value="dog">🐕 Dog</option>
                <option value="cat">🐈 Cat</option>
                <option value="rabbit">🐇 Rabbit</option>
                <option value="bird">🦜 Bird</option>
                <option value="all">🐾 All</option>
              </select>
            </div>

            <div class="form-group">
              <label>Image URL (primary)</label>
              <input type="text" class="form-control" formControlName="primaryImageUrl"
                     placeholder="https://example.com/image.jpg" />
            </div>
          </div>

          <div class="form-group availability-toggle">
            <label class="toggle-label">
              <input type="checkbox" formControlName="isAvailable" />
              <span class="toggle-text">Product is available for sale</span>
            </label>
          </div>

          @if (saveError) {
            <div class="alert alert-danger">{{ saveError }}</div>
          }

          <div class="form-actions">
            <button type="submit" class="btn btn-primary" [disabled]="saving">
              @if (saving) { <span class="spinner"></span> }
              {{ isEdit ? 'Save changes' : 'Create product' }}
            </button>
            <a routerLink="/store/products" class="btn btn-ghost">Cancel</a>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .two-col-form { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    @media (max-width: 600px) { .two-col-form { grid-template-columns: 1fr; } }

    .availability-toggle {
      display: flex; align-items: center; gap: 10px;
    }

    .toggle-label {
      display: flex; align-items: center; gap: 8px; cursor: pointer;
      font-size: 14px;
      text-transform: none !important; letter-spacing: 0 !important;
      input[type=checkbox] { width: auto; }
    }

    .form-actions { display: flex; gap: 10px; margin-top: 20px; }
  `]
})
export class ProductManagementComponent implements OnInit {
  categories: Category[] = [];
  loading   = false;
  saving    = false;
  submitted = false;
  isEdit    = false;
  productId: number | null = null;
  saveError:   string | null = null;
  saveSuccess = false;
  loadError:   string | null = null;

  form = this.fb.group({
    name:            ['', [Validators.required, Validators.maxLength(200)]],
    description:     [''],
    price:           [null as number | null, [Validators.required, Validators.min(0.01)]],
    categoryId:      [null as number | null, Validators.required],
    stockQuantity:   [0, [Validators.required, Validators.min(0)]],
    targetSpecies:   [''],
    primaryImageUrl: [''],
    isAvailable:     [true]
  });

  constructor(
    private fb:           FormBuilder,
    private route:        ActivatedRoute,
    private router:       Router,
    private storeService: StoreService,
    public  auth:         AuthService
  ) {}

  ngOnInit(): void {
    this.storeService.getCategories().subscribe({ next: c => this.categories = c });

    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.isEdit    = true;
      this.productId = Number(id);
      this.storeService.getProductById(this.productId).subscribe({
        next: p => {
          const primary = p.images.find(i => i.isPrimary)?.imageUrl ?? p.images[0]?.imageUrl ?? '';
          this.form.patchValue({
            name: p.name, description: p.description ?? '',
            price: p.price, categoryId: p.categoryId,
            stockQuantity: p.stockQuantity, targetSpecies: p.targetSpecies ?? '',
            primaryImageUrl: primary, isAvailable: p.isAvailable
          });
        },
        error: () => { this.loadError = 'Failed to load product'; }
      });
    }
  }

  save(): void {
    this.submitted = true;
    if (this.form.invalid) return;

    this.saving    = true;
    this.saveError = null;
    const v        = this.form.value;

    const images = v.primaryImageUrl ? [{
      imageUrl: v.primaryImageUrl, isPrimary: true, sortOrder: 0
    }] : [];

    if (this.isEdit && this.productId) {
      const req: UpdateProductRequest = {
        name: v.name!, description: v.description ?? undefined,
        price: v.price!, categoryId: v.categoryId!,
        stockQuantity: v.stockQuantity!, targetSpecies: v.targetSpecies ?? undefined,
        isAvailable: v.isAvailable ?? true, images
      };
      this.storeService.updateProduct(this.productId, req).subscribe({
        next: () => {
          this.saving      = false;
          this.saveSuccess = true;
          setTimeout(() => this.router.navigate(['/store/products']), 1500);
        },
        error: err => { this.saving = false; this.saveError = err.error?.message ?? 'Save failed'; }
      });
    } else {
      const req: CreateProductRequest = {
        name: v.name!, description: v.description ?? undefined,
        price: v.price!, categoryId: v.categoryId!,
        stockQuantity: v.stockQuantity!, targetSpecies: v.targetSpecies ?? undefined,
        isAvailable: v.isAvailable ?? true, images
      };
      this.storeService.createProduct(req).subscribe({
        next: () => {
          this.saving      = false;
          this.saveSuccess = true;
          setTimeout(() => this.router.navigate(['/store/products']), 1500);
        },
        error: err => { this.saving = false; this.saveError = err.error?.message ?? 'Create failed'; }
      });
    }
  }
}
