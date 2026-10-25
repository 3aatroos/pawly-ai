import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cart, AddToCartRequest, UpdateCartItemRequest } from '../models/cart.model';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly base = `${environment.apiUrl}/store/cart`;

  cart = signal<Cart | null>(null);
  itemCount = computed(() => this.cart()?.itemCount ?? 0);

  constructor(private http: HttpClient) {}

  load(): Observable<Cart> {
    return this.http.get<Cart>(this.base).pipe(
      tap(cart => this.cart.set(cart))
    );
  }

  addItem(req: AddToCartRequest): Observable<Cart> {
    return this.http.post<Cart>(this.base, req).pipe(
      tap(cart => this.cart.set(cart))
    );
  }

  updateItem(itemId: number, req: UpdateCartItemRequest): Observable<Cart> {
    return this.http.put<Cart>(`${this.base}/${itemId}`, req).pipe(
      tap(cart => this.cart.set(cart))
    );
  }

  removeItem(itemId: number): Observable<Cart> {
    return this.http.delete<Cart>(`${this.base}/${itemId}`).pipe(
      tap(cart => this.cart.set(cart))
    );
  }

  clear(): Observable<void> {
    return this.http.delete<void>(this.base).pipe(
      tap(() => this.cart.set(null))
    );
  }

  reset(): void {
    this.cart.set(null);
  }
}
