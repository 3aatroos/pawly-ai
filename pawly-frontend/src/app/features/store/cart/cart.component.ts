import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CartService } from '../../../core/services/cart.service';
import { Cart, CartItem } from '../../../core/models/cart.model';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="cart-page">
      <div class="cart-header">
        <h1 class="page-title">🛒 My Cart</h1>
        @if (cart && cart.items.length > 0) {
          <button class="btn-clear" (click)="clearCart()">Clear all</button>
        }
      </div>

      @if (loading) {
        <div class="cart-loading">Loading cart…</div>
      } @else if (!cart || cart.items.length === 0) {
        <div class="cart-empty">
          <div class="empty-icon">🛒</div>
          <p>Your cart is empty.</p>
          <a routerLink="/store" class="btn-primary">Browse products</a>
        </div>
      } @else {
        <div class="cart-layout">
          <div class="cart-items">
            @if (cart.hasUnavailableItems) {
              <div class="cart-warning">
                ⚠️ Some items are unavailable or have insufficient stock. Remove them before checkout.
              </div>
            }

            @for (item of cart.items; track item.id) {
              <div class="cart-item" [class.item-unavailable]="!item.productAvailable || item.productStockQuantity < item.quantity">
                <div class="item-image">
                  @if (item.productImageUrl) {
                    <img [src]="item.productImageUrl" [alt]="item.productName" />
                  } @else {
                    <div class="image-placeholder">🐾</div>
                  }
                </div>

                <div class="item-info">
                  <a [routerLink]="['/store/products', item.productId]" class="item-name">
                    {{ item.productName }}
                  </a>
                  <span class="item-price">{{ item.unitPrice | currency:'EUR':'symbol':'1.2-2' }} each</span>
                  @if (!item.productAvailable) {
                    <span class="badge-unavailable">Unavailable</span>
                  } @else if (item.productStockQuantity < item.quantity) {
                    <span class="badge-low-stock">
                      Only {{ item.productStockQuantity }} left
                    </span>
                  }
                </div>

                <div class="item-qty">
                  <button
                    class="qty-btn"
                    [disabled]="item.quantity <= 1 || updatingId === item.id"
                    (click)="decrement(item)">−</button>
                  <span class="qty-value">{{ item.quantity }}</span>
                  <button
                    class="qty-btn"
                    [disabled]="item.quantity >= item.productStockQuantity || updatingId === item.id"
                    (click)="increment(item)">+</button>
                </div>

                <div class="item-total">
                  {{ item.lineTotal | currency:'EUR':'symbol':'1.2-2' }}
                </div>

                <button class="item-remove" [disabled]="updatingId === item.id" (click)="remove(item)">
                  🗑
                </button>
              </div>
            }
          </div>

          <div class="cart-summary">
            <h2 class="summary-title">Order Summary</h2>

            <div class="summary-row">
              <span>Subtotal ({{ cart.itemCount }} item{{ cart.itemCount !== 1 ? 's' : '' }})</span>
              <span>{{ cart.subtotal | currency:'EUR':'symbol':'1.2-2' }}</span>
            </div>

            <div class="summary-row">
              <span>Shipping</span>
              @if (cart.shippingFee === 0) {
                <span class="shipping-free">Free 🎉</span>
              } @else {
                <span>{{ cart.shippingFee | currency:'EUR':'symbol':'1.2-2' }}</span>
              }
            </div>

            @if (cart.shippingFee > 0) {
              <p class="free-shipping-hint">
                Add {{ (50 - cart.subtotal) | currency:'EUR':'symbol':'1.2-2' }} more for free shipping
              </p>
            }

            <div class="summary-divider"></div>

            <div class="summary-row summary-total">
              <span>Total</span>
              <span>{{ cart.total | currency:'EUR':'symbol':'1.2-2' }}</span>
            </div>

            <a
              routerLink="/store/checkout"
              class="btn-checkout"
              [class.btn-disabled]="cart.hasUnavailableItems"
              [attr.aria-disabled]="cart.hasUnavailableItems">
              Proceed to Checkout
            </a>

            <a routerLink="/store" class="btn-continue">← Continue Shopping</a>

            @if (error) {
              <p class="cart-error">{{ error }}</p>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .cart-page { max-width: 1100px; margin: 0 auto; }
    .cart-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
    .page-title { font-size: 24px; font-weight: 700; }
    .btn-clear { background: none; border: 1px solid var(--danger); color: var(--danger); padding: 6px 14px; border-radius: 8px; cursor: pointer; font-size: 13px; &:hover { background: #fff0f0; } }
    .cart-loading, .cart-empty { text-align: center; padding: 60px 20px; color: var(--text-soft); }
    .empty-icon { font-size: 56px; margin-bottom: 16px; }
    .cart-layout { display: grid; grid-template-columns: 1fr 340px; gap: 24px; align-items: start; }
    @media (max-width: 768px) { .cart-layout { grid-template-columns: 1fr; } }
    .cart-warning { background: #fff8e1; border: 1px solid #ffe082; border-radius: 10px; padding: 12px 16px; font-size: 13px; color: #7a5c00; margin-bottom: 16px; }
    .cart-item { display: flex; align-items: center; gap: 16px; padding: 16px; background: #fff; border-radius: 12px; border: 1px solid var(--border); margin-bottom: 12px; transition: opacity 0.2s; }
    .item-unavailable { opacity: 0.6; border-color: var(--danger); }
    .item-image { width: 72px; height: 72px; border-radius: 10px; overflow: hidden; flex-shrink: 0; background: var(--neutral); }
    .item-image img { width: 100%; height: 100%; object-fit: cover; }
    .image-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 28px; }
    .item-info { flex: 1; min-width: 0; }
    .item-name { font-weight: 600; font-size: 14px; color: var(--primary); text-decoration: none; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; &:hover { text-decoration: underline; } }
    .item-price { font-size: 12px; color: var(--text-soft); margin-top: 4px; display: block; }
    .badge-unavailable { display: inline-block; background: var(--danger); color: #fff; font-size: 10px; padding: 2px 8px; border-radius: 99px; margin-top: 4px; }
    .badge-low-stock { display: inline-block; background: #ff9800; color: #fff; font-size: 10px; padding: 2px 8px; border-radius: 99px; margin-top: 4px; }
    .item-qty { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
    .qty-btn { width: 28px; height: 28px; border-radius: 50%; border: 1px solid var(--border); background: var(--neutral); cursor: pointer; font-size: 16px; line-height: 1; display: flex; align-items: center; justify-content: center; &:hover:not(:disabled) { background: var(--primary-light); color: var(--primary); } &:disabled { opacity: 0.4; cursor: not-allowed; } }
    .qty-value { min-width: 24px; text-align: center; font-weight: 600; font-size: 15px; }
    .item-total { font-weight: 700; font-size: 15px; min-width: 70px; text-align: right; flex-shrink: 0; }
    .item-remove { background: none; border: none; cursor: pointer; font-size: 18px; color: var(--text-soft); padding: 4px; border-radius: 6px; flex-shrink: 0; &:hover:not(:disabled) { background: #fff0f0; color: var(--danger); } &:disabled { opacity: 0.4; cursor: not-allowed; } }
    .cart-summary { background: #fff; border-radius: 16px; border: 1px solid var(--border); padding: 24px; position: sticky; top: 24px; }
    .summary-title { font-size: 18px; font-weight: 700; margin-bottom: 20px; }
    .summary-row { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 12px; }
    .summary-total { font-size: 18px; font-weight: 700; }
    .shipping-free { color: #22c55e; font-weight: 600; }
    .free-shipping-hint { font-size: 12px; color: var(--text-soft); text-align: center; margin: 4px 0 12px; }
    .summary-divider { height: 1px; background: var(--border); margin: 16px 0; }
    .btn-checkout { display: block; width: 100%; background: var(--primary); color: #fff; text-align: center; padding: 14px; border-radius: 12px; font-weight: 700; font-size: 15px; text-decoration: none; margin-top: 20px; transition: opacity 0.2s; &:hover:not(.btn-disabled) { opacity: 0.9; } }
    .btn-disabled { background: var(--neutral); color: var(--text-soft); cursor: not-allowed; pointer-events: none; }
    .btn-continue { display: block; text-align: center; margin-top: 12px; font-size: 13px; color: var(--primary); text-decoration: none; &:hover { text-decoration: underline; } }
    .btn-primary { display: inline-block; background: var(--primary); color: #fff; padding: 10px 24px; border-radius: 10px; text-decoration: none; margin-top: 16px; font-weight: 600; }
    .cart-error { color: var(--danger); font-size: 13px; margin-top: 12px; text-align: center; }
  `]
})
export class CartComponent implements OnInit {
  cart: Cart | null = null;
  loading = true;
  updatingId: number | null = null;
  error: string | null = null;

  constructor(private cartService: CartService) {}

  ngOnInit(): void {
    this.cartService.load().subscribe({
      next: cart => { this.cart = cart; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  increment(item: CartItem): void {
    this.updateQty(item, item.quantity + 1);
  }

  decrement(item: CartItem): void {
    if (item.quantity > 1) this.updateQty(item, item.quantity - 1);
  }

  remove(item: CartItem): void {
    this.updatingId = item.id;
    this.error = null;
    this.cartService.removeItem(item.id).subscribe({
      next: cart => { this.cart = cart; this.updatingId = null; },
      error: err => { this.error = err.error?.message ?? 'Failed to remove item'; this.updatingId = null; }
    });
  }

  clearCart(): void {
    this.error = null;
    this.cartService.clear().subscribe({
      next: () => { this.cart = { items: [], itemCount: 0, subtotal: 0, shippingFee: 0, total: 0, hasUnavailableItems: false }; },
      error: err => { this.error = err.error?.message ?? 'Failed to clear cart'; }
    });
  }

  private updateQty(item: CartItem, qty: number): void {
    this.updatingId = item.id;
    this.error = null;
    this.cartService.updateItem(item.id, { quantity: qty }).subscribe({
      next: cart => { this.cart = cart; this.updatingId = null; },
      error: err => { this.error = err.error?.message ?? 'Failed to update quantity'; this.updatingId = null; }
    });
  }
}
