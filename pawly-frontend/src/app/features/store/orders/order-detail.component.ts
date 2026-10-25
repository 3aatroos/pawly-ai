import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrderService } from '../../../core/services/order.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  Order, OrderStatus, ORDER_STATUS_LABELS, ORDER_STATUS_STEPS
} from '../../../core/models/order.model';
import { TrackingComponent } from '../shared/tracking.component';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, TrackingComponent],
  template: `
    <div class="order-detail-page fade-up">
      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      } @else if (!order) {
        <div class="card">
          <div class="empty-state">
            <div class="empty-icon">📦</div>
            <div class="empty-title">Order not found</div>
            <a routerLink="/store/orders" class="btn btn-ghost btn-sm" style="margin-top:14px">← My Orders</a>
          </div>
        </div>
      } @else {
        <div class="detail-header">
          <a routerLink="/store/orders" class="back-link">← My Orders</a>
          <div class="header-title">
            <h1>Order #{{ order.id }}</h1>
            <span class="badge" [ngClass]="statusBadgeClass(order.status)">
              {{ statusLabel(order.status) }}
            </span>
          </div>
          <span class="order-date">Placed on {{ order.createdAt | date:'dd MMM yyyy, HH:mm' }}</span>
        </div>

        <!-- Tracking widget -->
        @if (order.status !== 'CANCELLED') {
          <app-tracking
            [status]="order.status"
            [refreshing]="refreshing"
            (refresh)="onRefresh()"
            style="margin-bottom:24px; display:block">
          </app-tracking>
        }

        <div class="detail-grid">
          <!-- Items -->
          <div class="detail-card">
            <h2 class="card-title">Items</h2>
            @for (item of order.items; track item.id) {
              <div class="order-item-row">
                <div class="item-info">
                  <span class="item-name">{{ item.productName }}</span>
                  <span class="item-qty">× {{ item.quantity }}</span>
                </div>
                <span class="item-line-total">{{ item.lineTotal | currency:'EUR':'symbol':'1.2-2' }}</span>
              </div>
            }

            <div class="totals-section">
              <div class="totals-row">
                <span>Subtotal</span>
                <span>{{ (order.totalAmount - order.shippingFee) | currency:'EUR':'symbol':'1.2-2' }}</span>
              </div>
              <div class="totals-row">
                <span>Shipping</span>
                @if (order.shippingFee === 0) {
                  <span class="free">Free 🎉</span>
                } @else {
                  <span>{{ order.shippingFee | currency:'EUR':'symbol':'1.2-2' }}</span>
                }
              </div>
              <div class="totals-row totals-total">
                <span>Total</span>
                <span>{{ order.totalAmount | currency:'EUR':'symbol':'1.2-2' }}</span>
              </div>
            </div>
          </div>

          <!-- Delivery -->
          <div class="detail-card">
            <h2 class="card-title">Delivery</h2>
            <div class="info-row"><span class="info-label">Address</span><span>{{ order.shippingAddress }}</span></div>
            @if (order.shippingCity) {
              <div class="info-row"><span class="info-label">City</span><span>{{ order.shippingCity }}</span></div>
            }
            <div class="info-row"><span class="info-label">Country</span><span>{{ order.shippingCountry }}</span></div>
            @if (order.notes) {
              <div class="info-row"><span class="info-label">Notes</span><span>{{ order.notes }}</span></div>
            }
          </div>

          <!-- Payment -->
          <div class="detail-card">
            <h2 class="card-title">Payment</h2>
            <div class="info-row">
              <span class="info-label">Method</span>
              <span>{{ order.paymentMethod === 'STRIPE' ? '💳 Card (Stripe)' : '💵 Cash on delivery' }}</span>
            </div>
            @if (order.payment) {
              <div class="info-row">
                <span class="info-label">Status</span>
                <span class="payment-status" [ngClass]="paymentStatusClass(order.payment.status)">
                  {{ order.payment.status }}
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">Amount</span>
                <span>{{ order.payment.amount | currency:order.payment.currency:'symbol':'1.2-2' }}</span>
              </div>
              @if (order.payment.paidAt) {
                <div class="info-row">
                  <span class="info-label">Paid at</span>
                  <span>{{ order.payment.paidAt | date:'dd/MM/yyyy HH:mm' }}</span>
                </div>
              }
            }
          </div>

          <!-- Customer (admin only) -->
          @if (isAdmin) {
            <div class="detail-card">
              <h2 class="card-title">Customer</h2>
              <div class="info-row"><span class="info-label">Name</span><span>{{ order.buyerFirstName }} {{ order.buyerLastName }}</span></div>
              <div class="info-row"><span class="info-label">Email</span><span>{{ order.buyerEmail }}</span></div>
              <div class="info-row"><span class="info-label">User ID</span><span>#{{ order.buyerId }}</span></div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .order-detail-page { max-width: 860px; margin: 0 auto; }
    .back-link { font-size: 13px; color: var(--primary); text-decoration: none; &:hover { text-decoration: underline; } }
    .detail-header { margin-bottom: 24px; }
    .header-title { display: flex; align-items: center; gap: 14px; margin: 8px 0 4px; }
    h1 { font-size: 24px; font-weight: 700; margin: 0; }
    .order-date { font-size: 13px; color: var(--text-soft); }
    .badge-confirmed        { background: #e0f2fe; color: #0369a1; }
    .badge-preparing        { background: #fef9c3; color: #92400e; }
    .badge-shipped          { background: #ede9fe; color: #6d28d9; }
    .badge-in_transit       { background: #e0e7ff; color: #3730a3; }
    .badge-out_for_delivery { background: #ffedd5; color: #c2410c; }
    .badge-delivered        { background: #dcfce7; color: #15803d; }
    .badge-cancelled        { background: #fee2e2; color: #dc2626; }
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
    @media (max-width: 640px) { .detail-grid { grid-template-columns: 1fr; } }
    .detail-card:first-child { grid-column: 1 / -1; }
    .detail-card { background: #fff; border-radius: 14px; border: 1px solid var(--border); padding: 20px 24px; }
    .card-title { font-size: 15px; font-weight: 700; margin-bottom: 16px; }
    .order-item-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--border); &:last-of-type { border-bottom: none; } }
    .item-info { display: flex; align-items: center; gap: 8px; }
    .item-name { font-size: 14px; font-weight: 500; }
    .item-qty  { font-size: 13px; color: var(--text-soft); }
    .item-line-total { font-weight: 600; font-size: 14px; }
    .totals-section { margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border); }
    .totals-row { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 8px; }
    .totals-total { font-size: 17px; font-weight: 700; margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--border); }
    .free { color: #22c55e; font-weight: 600; }
    .info-row { display: flex; gap: 12px; font-size: 14px; margin-bottom: 10px; }
    .info-label { font-weight: 600; color: var(--text-soft); min-width: 70px; flex-shrink: 0; }
    .payment-status { padding: 2px 10px; border-radius: 99px; font-size: 12px; font-weight: 600; }
    .pay-succeeded { background: #dcfce7; color: #15803d; }
    .pay-pending   { background: #fef9c3; color: #92400e; }
    .pay-failed    { background: #fee2e2; color: #dc2626; }
  `]
})
export class OrderDetailComponent implements OnInit {
  order: Order | null = null;
  loading   = true;
  refreshing = false;
  readonly steps = ORDER_STATUS_STEPS;

  constructor(
    private route:        ActivatedRoute,
    private orderService: OrderService,
    public  auth:         AuthService
  ) {}

  get isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  ngOnInit(): void {
    this.fetchOrder();
  }

  onRefresh(): void {
    this.refreshing = true;
    this.fetchOrder(() => this.refreshing = false);
  }

  private fetchOrder(done?: () => void): void {
    const id  = Number(this.route.snapshot.paramMap.get('id'));
    const req = this.isAdmin
        ? this.orderService.getOrderById(id)
        : this.orderService.getMyOrderById(id);

    req.subscribe({
      next: order => {
        this.order   = order;
        this.loading = false;
        done?.();
      },
      error: () => { this.loading = false; done?.(); }
    });
  }

  statusLabel(s: OrderStatus): string {
    return ORDER_STATUS_LABELS[s] ?? s;
  }

  statusBadgeClass(s: OrderStatus): string {
    return 'badge badge-' + s.toLowerCase().replace(/_/g, '_');
  }

  isStepDone(step: OrderStatus): boolean {
    if (!this.order) return false;
    const current = this.steps.indexOf(this.order.status);
    const target  = this.steps.indexOf(step);
    return target <= current;
  }

  paymentStatusClass(s: string): string {
    const m: Record<string, string> = {
      SUCCEEDED: 'pay-succeeded', PENDING: 'pay-pending', FAILED: 'pay-failed'
    };
    return m[s] ?? '';
  }
}
