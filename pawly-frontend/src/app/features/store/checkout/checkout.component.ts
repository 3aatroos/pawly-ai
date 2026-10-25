import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CartService } from '../../../core/services/cart.service';
import { OrderService } from '../../../core/services/order.service';
import { Cart } from '../../../core/models/cart.model';
import { CreateOrderRequest } from '../../../core/models/order.model';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="checkout-page">
      <h1 class="page-title">Checkout</h1>

      @if (loading) {
        <div class="checkout-loading">Loading…</div>
      } @else if (!cart || cart.items.length === 0) {
        <div class="checkout-empty">
          <p>Your cart is empty.</p>
          <a routerLink="/store/cart" class="btn-back">← Back to cart</a>
        </div>
      } @else {

        <div class="checkout-layout">

          <!-- Left: form -->
          <div class="checkout-form">

            <section class="form-section">
              <h2 class="section-title">Delivery address</h2>

              <div class="form-group">
                <label for="address">Street address *</label>
                <input id="address" type="text" [(ngModel)]="form.shippingAddress"
                       placeholder="123 Main Street" class="form-input"
                       [class.input-error]="submitted && !form.shippingAddress" />
                @if (submitted && !form.shippingAddress) {
                  <span class="field-error">Address is required</span>
                }
              </div>

              <div class="form-row">
                <div class="form-group">
                  <label for="city">City</label>
                  <input id="city" type="text" [(ngModel)]="form.shippingCity"
                         placeholder="Tunis" class="form-input" />
                </div>
                <div class="form-group">
                  <label for="country">Country</label>
                  <input id="country" type="text" [(ngModel)]="form.shippingCountry"
                         placeholder="TN" class="form-input" />
                </div>
              </div>

              <div class="form-group">
                <label for="notes">Notes (optional)</label>
                <textarea id="notes" [(ngModel)]="form.notes" rows="2"
                          placeholder="Leave at the door…" class="form-input"></textarea>
              </div>
            </section>

            <section class="form-section">
              <h2 class="section-title">Payment method</h2>

              <div class="payment-option selected">
                <span class="payment-icon">💵</span>
                <div>
                  <div class="payment-label">Cash on delivery</div>
                  <div class="payment-sub">Pay when your order arrives</div>
                </div>
              </div>

              @if (error) { <p class="checkout-error">{{ error }}</p> }

              <button class="btn-continue" [disabled]="placing" (click)="continueToPayment()">
                @if (placing) { Processing… } @else { Place order }
              </button>

              <a routerLink="/store/cart" class="btn-back">← Edit cart</a>
            </section>
          </div>

          <!-- Right: order summary -->
          <div class="checkout-summary">
            <h2 class="section-title">Order summary</h2>

            @for (item of cart.items; track item.id) {
              <div class="summary-item">
                <span class="summary-item-name">{{ item.productName }} × {{ item.quantity }}</span>
                <span>{{ item.lineTotal | currency:'EUR':'symbol':'1.2-2' }}</span>
              </div>
            }

            <div class="summary-divider"></div>

            <div class="summary-row">
              <span>Subtotal</span>
              <span>{{ cart.subtotal | currency:'EUR':'symbol':'1.2-2' }}</span>
            </div>
            <div class="summary-row">
              <span>Shipping</span>
              @if (cart.shippingFee === 0) {
                <span class="free">Free 🎉</span>
              } @else {
                <span>{{ cart.shippingFee | currency:'EUR':'symbol':'1.2-2' }}</span>
              }
            </div>

            <div class="summary-divider"></div>
            <div class="summary-row summary-total">
              <span>Total</span>
              <span>{{ cart.total | currency:'EUR':'symbol':'1.2-2' }}</span>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .checkout-page { max-width: 1000px; margin: 0 auto; }
    .page-title { font-size: 24px; font-weight: 700; margin-bottom: 20px; }
    .checkout-loading, .checkout-empty { text-align: center; padding: 60px; color: var(--text-soft); }
    .steps { display: flex; align-items: center; gap: 0; margin-bottom: 28px; }
    .step { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: var(--text-soft); }
    .step.step-active { color: var(--primary); }
    .step.step-done { color: #22c55e; }
    .step-num { width: 24px; height: 24px; border-radius: 50%; background: var(--neutral); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; }
    .step-active .step-num { background: var(--primary); color: #fff; }
    .step-done .step-num { background: #22c55e; color: #fff; }
    .step-line { flex: 1; height: 1px; background: var(--border); margin: 0 12px; }
    .checkout-layout { display: grid; grid-template-columns: 1fr 320px; gap: 28px; align-items: start; }
    @media (max-width: 768px) { .checkout-layout { grid-template-columns: 1fr; } }
    .form-section { background: #fff; border-radius: 14px; border: 1px solid var(--border); padding: 24px; margin-bottom: 20px; }
    .section-title { font-size: 16px; font-weight: 700; margin-bottom: 20px; }
    .form-group { margin-bottom: 16px; }
    .form-group label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: var(--text-soft); }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .form-input { width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 14px; box-sizing: border-box; &:focus { outline: none; border-color: var(--primary); } }
    .input-error { border-color: var(--danger) !important; }
    .field-error { font-size: 12px; color: var(--danger); margin-top: 4px; display: block; }
    textarea.form-input { resize: vertical; }
    .payment-option { display: flex; align-items: center; gap: 14px; padding: 14px; border: 2px solid var(--border); border-radius: 12px; cursor: pointer; margin-bottom: 12px; transition: border-color 0.15s; &.selected { border-color: var(--primary); background: var(--primary-light); } input[type=radio] { display: none; } }
    .payment-icon { font-size: 26px; }
    .payment-label { font-weight: 600; font-size: 14px; }
    .payment-sub { font-size: 12px; color: var(--text-soft); margin-top: 2px; }
    .checkout-error { color: var(--danger); font-size: 13px; margin: 12px 0; }
    .btn-continue { width: 100%; padding: 14px; background: var(--primary); color: #fff; border: none; border-radius: 12px; font-weight: 700; font-size: 15px; cursor: pointer; margin-top: 16px; transition: opacity 0.2s; &:hover:not(:disabled) { opacity: 0.9; } &:disabled { opacity: 0.5; cursor: not-allowed; } }
    .btn-back { display: block; text-align: center; margin-top: 12px; font-size: 13px; color: var(--primary); text-decoration: none; &:hover { text-decoration: underline; } }
    .checkout-summary { background: #fff; border-radius: 14px; border: 1px solid var(--border); padding: 24px; position: sticky; top: 24px; }
    .summary-item { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 10px; }
    .summary-item-name { flex: 1; margin-right: 8px; color: var(--text-soft); }
    .summary-divider { height: 1px; background: var(--border); margin: 12px 0; }
    .summary-row { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 10px; }
    .summary-total { font-size: 18px; font-weight: 700; }
    .free { color: #22c55e; font-weight: 600; }
  `]
})
export class CheckoutComponent implements OnInit {
  cart: Cart | null = null;
  loading = true;
  placing = false;
  submitted = false;
  error: string | null = null;

  form: CreateOrderRequest = {
    paymentMethod: 'CASH_ON_DELIVERY',
    shippingAddress: '',
    shippingCity: '',
    shippingCountry: 'TN',
    notes: ''
  };

  constructor(
    private cartService: CartService,
    private orderService: OrderService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.cartService.load().subscribe({
      next: cart => { this.cart = cart; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  continueToPayment(): void {
    this.submitted = true;
    if (!this.form.shippingAddress.trim()) return;

    this.placing = true;
    this.error = null;

    this.orderService.placeOrder(this.form).subscribe({
      next: order => {
        this.cartService.reset();
        this.router.navigate(['/store/orders', order.id]);
      },
      error: err => {
        this.error = err.error?.message ?? 'Failed to place order. Please try again.';
        this.placing = false;
      }
    });
  }
}
