import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StoreService, Category } from '../../../core/services/store.service';

@Component({
  selector: 'app-category-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">📂 Category Management</div>
          <div class="page-sub">Manage product categories</div>
        </div>
        <button class="btn btn-primary btn-sm" (click)="openNew()">+ New Category</button>
      </div>

      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      } @else {
        <div class="card">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Products</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (cat of categories; track cat.id) {
                  @if (editingId === cat.id) {
                    <tr class="editing-row">
                      <td>
                        <input class="form-control" type="text" [(ngModel)]="editName" maxlength="100" />
                      </td>
                      <td>{{ cat.productCount ?? 0 }}</td>
                      <td>
                        <label class="toggle-label">
                          <input type="checkbox" [(ngModel)]="editActive" />
                          Active
                        </label>
                      </td>
                      <td>
                        <div style="display:flex; gap:6px">
                          <button class="btn btn-primary btn-xs" [disabled]="saving" (click)="saveEdit(cat)">Save</button>
                          <button class="btn btn-ghost btn-xs" (click)="cancelEdit()">Cancel</button>
                        </div>
                      </td>
                    </tr>
                  } @else {
                    <tr>
                      <td class="cat-name">{{ cat.name }}</td>
                      <td>{{ cat.productCount ?? 0 }}</td>
                      <td>
                        <span class="badge" [ngClass]="cat.isActive ? 'badge-active' : 'badge-inactive'">
                          {{ cat.isActive ? 'Active' : 'Inactive' }}
                        </span>
                      </td>
                      <td>
                        <div style="display:flex; gap:6px">
                          <button class="btn btn-ghost btn-xs" (click)="startEdit(cat)">✏️</button>
                          <button class="btn btn-danger btn-xs" (click)="deleteCategory(cat)"
                                  [disabled]="(cat.productCount ?? 0) > 0"
                                  [title]="(cat.productCount ?? 0) > 0 ? 'Cannot delete: has products' : 'Delete'">🗑️</button>
                        </div>
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        </div>

        <!-- New category form -->
        @if (showNewForm) {
          <div class="card" style="max-width:480px">
            <div class="card-header">
              <span class="card-title">New Category</span>
            </div>

            <div class="form-group">
              <label>Name *</label>
              <input class="form-control" type="text" [(ngModel)]="newName" maxlength="100" placeholder="e.g. Accessories" />
            </div>

            <div class="form-group">
              <label>Description</label>
              <input class="form-control" type="text" [(ngModel)]="newDescription" placeholder="Short description" />
            </div>

            @if (formError) {
              <div class="alert alert-danger">{{ formError }}</div>
            }

            <div style="display:flex; gap:8px">
              <button class="btn btn-primary btn-sm" [disabled]="!newName.trim() || saving" (click)="createCategory()">
                @if (saving) { <span class="spinner"></span> } Create
              </button>
              <button class="btn btn-ghost btn-sm" (click)="showNewForm = false">Cancel</button>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .cat-name { font-weight: 600; }
    .editing-row td { background: var(--primary-light); }
    .toggle-label { display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: 13px; text-transform: none; letter-spacing: 0; }
  `]
})
export class CategoryManagementComponent implements OnInit {
  categories: Category[] = [];
  loading = true;
  saving  = false;
  showNewForm = false;
  formError: string | null = null;

  editingId:   number | null = null;
  editName     = '';
  editActive   = true;

  newName        = '';
  newDescription = '';

  constructor(private storeService: StoreService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.storeService.getAllCategories().subscribe({
      next: cats => { this.categories = cats; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  openNew(): void { this.showNewForm = true; this.formError = null; }

  createCategory(): void {
    if (!this.newName.trim()) return;
    this.saving = true;
    this.storeService.createCategory({
      name: this.newName.trim(),
      description: this.newDescription.trim() || undefined,
      isActive: true
    }).subscribe({
      next: cat => {
        this.categories = [...this.categories, cat];
        this.newName        = '';
        this.newDescription = '';
        this.showNewForm    = false;
        this.saving         = false;
      },
      error: err => { this.saving = false; this.formError = err.error?.message ?? 'Create failed'; }
    });
  }

  startEdit(cat: Category): void {
    this.editingId  = cat.id!;
    this.editName   = cat.name;
    this.editActive = cat.isActive ?? true;
  }

  saveEdit(cat: Category): void {
    this.saving = true;
    this.storeService.updateCategory(cat.id!, {
      name: this.editName.trim(), isActive: this.editActive
    }).subscribe({
      next: updated => {
        const idx = this.categories.findIndex(c => c.id === cat.id);
        if (idx >= 0) this.categories[idx] = updated;
        this.cancelEdit();
        this.saving = false;
      },
      error: err => { this.saving = false; alert(err.error?.message ?? 'Update failed'); }
    });
  }

  cancelEdit(): void { this.editingId = null; }

  deleteCategory(cat: Category): void {
    if (!confirm(`Delete "${cat.name}"?`)) return;
    this.storeService.deleteCategory(cat.id!).subscribe({
      next: () => { this.categories = this.categories.filter(c => c.id !== cat.id); },
      error: err => alert(err.error?.message ?? 'Delete failed')
    });
  }
}
