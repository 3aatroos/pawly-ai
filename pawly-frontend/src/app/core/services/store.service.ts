import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from './user.service';

export interface ProductImage {
  id?: number;
  imageUrl: string;
  isPrimary?: boolean;
  sortOrder?: number;
}

export interface ProductSummary {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  price: number;
  isAvailable: boolean;
  averageRating: number;
  reviewCount: number;
  targetSpecies: string | null;
  primaryImageUrl: string | null;
  stockQuantity: number | null;
  createdAt: string;
}

export interface ProductDetail extends ProductSummary {
  description: string | null;
  createdById: number;
  createdByName: string;
  images: ProductImage[];
  updatedAt: string;
}

export interface ProductRecommendation {
  productId: number;
  categoryId: number;
  categoryName: string;
  name: string;
  price: number;
  isAvailable: boolean;
  averageRating: number;
  reviewCount: number;
  targetSpecies: string | null;
  primaryImageUrl: string | null;
  score: number;
  reason: string;
  source: 'qlora' | 'content_based';
}

export interface Category {
  id?: number;
  name: string;
  description?: string;
  imageUrl?: string;
  isActive?: boolean;
  productCount?: number;
  createdAt?: string;
}

export interface Review {
  id?: number;
  productId?: number;
  userId?: number;
  userFirstName?: string;
  userLastName?: string;
  rating: number;
  comment?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateProductRequest {
  name: string;
  description?: string;
  price: number;
  categoryId: number;
  stockQuantity?: number;
  isAvailable?: boolean;
  targetSpecies?: string;
  images?: ProductImage[];
}

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  price?: number;
  categoryId?: number;
  stockQuantity?: number;
  isAvailable?: boolean;
  targetSpecies?: string;
  images?: ProductImage[];
}

@Injectable({ providedIn: 'root' })
export class StoreService {
  private readonly products   = `${environment.apiUrl}/store/products`;
  private readonly categories = `${environment.apiUrl}/store/categories`;

  constructor(private http: HttpClient) {}

  // ── Products ──────────────────────────────────────────────

  getProducts(
    page = 0, size = 12,
    categoryId?: number, species?: string,
    search?: string, minPrice?: number, maxPrice?: number,
    sort = 'createdAt,desc'
  ): Observable<PageResponse<ProductSummary>> {
    let params = new HttpParams()
      .set('page', page).set('size', size).set('sort', sort);
    if (categoryId !== undefined) params = params.set('categoryId', categoryId);
    if (species)   params = params.set('species',  species);
    if (search)    params = params.set('search',   search);
    if (minPrice !== undefined) params = params.set('minPrice', minPrice);
    if (maxPrice !== undefined) params = params.set('maxPrice', maxPrice);
    return this.http.get<PageResponse<ProductSummary>>(this.products, { params });
  }

  getProductById(id: number): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`${this.products}/${id}`);
  }

  getFeatured(limit = 8): Observable<ProductSummary[]> {
    return this.http.get<ProductSummary[]>(`${this.products}/featured`, {
      params: new HttpParams().set('limit', limit)
    });
  }

  getRecommendations(limit = 8): Observable<ProductRecommendation[]> {
    return this.http.get<ProductRecommendation[]>(`${environment.apiUrl}/store/recommendations`, {
      params: new HttpParams().set('limit', limit)
    });
  }

  createProduct(req: CreateProductRequest): Observable<ProductDetail> {
    return this.http.post<ProductDetail>(this.products, req);
  }

  updateProduct(id: number, req: UpdateProductRequest): Observable<ProductDetail> {
    return this.http.put<ProductDetail>(`${this.products}/${id}`, req);
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.products}/${id}`);
  }

  updateStock(id: number, stockQuantity: number): Observable<ProductDetail> {
    return this.http.patch<ProductDetail>(`${this.products}/${id}/stock`, { stockQuantity });
  }

  updateAvailability(id: number, available: boolean): Observable<ProductDetail> {
    return this.http.patch<ProductDetail>(
      `${this.products}/${id}/availability`,
      null,
      { params: new HttpParams().set('available', available) }
    );
  }

  // ── Reviews ────────────────────────────────────────────────

  getReviews(productId: number, page = 0, size = 10): Observable<PageResponse<Review>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Review>>(
      `${this.products}/${productId}/reviews`, { params });
  }

  addReview(productId: number, review: Review): Observable<Review> {
    return this.http.post<Review>(`${this.products}/${productId}/reviews`, review);
  }

  updateReview(productId: number, reviewId: number, review: Review): Observable<Review> {
    return this.http.put<Review>(`${this.products}/${productId}/reviews/${reviewId}`, review);
  }

  deleteReview(productId: number, reviewId: number): Observable<void> {
    return this.http.delete<void>(`${this.products}/${productId}/reviews/${reviewId}`);
  }

  // ── Categories ─────────────────────────────────────────────

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(this.categories);
  }

  getAllCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.categories}/all`);
  }

  createCategory(cat: Category): Observable<Category> {
    return this.http.post<Category>(this.categories, cat);
  }

  updateCategory(id: number, cat: Category): Observable<Category> {
    return this.http.put<Category>(`${this.categories}/${id}`, cat);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.categories}/${id}`);
  }
}
