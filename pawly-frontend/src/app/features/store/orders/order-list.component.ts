import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OrderService } from '../../../core/services/order.service';
import { Order, OrderStatus, ORDER_STATUS_LABELS } from '../../../core/models/order.model';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="orders-page">
      <h1 class="page-title">My Orders</h1>

      @if (loading) {
        <div class="orders-loading">Loading orders…</div>
      } @else if (orders.length === 0) {
        <div class="orders-empty">
          <div class="empty-icon">📦</div>
          <p>You haven't placed any orders yet.</p>
          <a routerLink="/store/cart" class="btn-primary">Start shopping</a>
        </div>
      } @else {
        <div class="orders-list">
          @for (order of orders; track order.id) {
            <a [routerLink]="['/store/orders', order.id]" class="order-card">
              <div class="order-card-header">
                <div>
                  <span class="order-id"># {{ order.id }}</span>
                  <span class="order-date">{{ order.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
                </div>
                <span class="status-badge" [ngClass]="statusClass(order.status)">
                  {{ statusLabel(order.status) }}
                </span>
              </div>

              <div class="order-card-body">
                <div class="order-items-preview">
                  @for (item of order.items.slice(0, 3); track item.id) {
                    <span class="item-chip">{{ item.productName }}</span>
                  }
                  @if (order.items.length > 3) {
                    <span class="item-chip-more">+{{ order.items.length - 3 }} more</span>
                  }
                </div>
                <div class="order-total">
                  {{ order.totalAmount | currency:'EUR':'symbol':'1.2-2' }}
                </div>
              </div>

              <div class="order-tracking-bar">
                <div class="tracking-progress" [style.width.%]="trackingPercent(order.status)"></div>
              </div>
            </a>
          }
        </div>

        @if (hasMore) {
          <button class="btn-load-more" [disabled]="loadingMore" (click)="loadMore()">
            @if (loadingMore) { Loading… } @else { Load more }
          </button>
        }
      }
    </div>
  `,
  styles: [`
    .orders-page { max-width: 780px; margin: 0 auto; }
    .page-title { font-size: 24px; font-weight: 700; margin-bottom: 24px; }
    .orders-loading, .orders-empty { text-align: center; padding: 60px; color: var(--text-soft); }
    .empty-icon { font-size: 52px; margin-bottom: 14px; }
    .btn-primary { display: inline-block; background: var(--primary); color: #fff; padding: 10px 24px; border-radius: 10px; text-decoration: none; margin-top: 14px; font-weight: 600; }
    .order-card { display: block; background: #fff; border-radius: 14px; border: 1px solid var(--border); padding: 18px 20px; margin-bottom: 14px; text-decoration: none; color: inherit; transition: box-shadow 0.15s; overflow: hidden; &:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); } }
    .order-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .order-id { font-weight: 700; font-size: 15px; margin-right: 10px; }
    .order-date { font-size: 12px; color: var(--text-soft); }
    .status-badge { padding: 4px 12px; border-radius: 99px; font-size: 12px; font-weight: 600; }
    .badge-confirmed        { background: #e0f2fe; color: #0369a1; }
    .badge-preparing        { background: #fef9c3; color: #92400e; }
    .badge-shipped          { background: #ede9fe; color: #6d28d9; }
    .badge-in_transit       { background: #e0e7ff; color: #3730a3; }
    .badge-out_for_delivery { background: #ffedd5; color: #c2410c; }
    .badge-delivered        { background: #dcfce7; color: #15803d; }
    .badge-cancelled        { background: #fee2e2; color: #dc2626; }
    .order-card-body { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
    .order-items-preview { display: flex; flex-wrap: wrap; gap: 6px; }
    .item-chip { background: var(--neutral); border-radius: 99px; padding: 3px 10px; font-size: 12px; max-width: 160px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .item-chip-more { background: var(--neutral); border-radius: 99px; padding: 3px 10px; font-size: 12px; color: var(--text-soft); }
    .order-total { font-weight: 700; font-size: 16px; flex-shrink: 0; margin-left: 12px; }
    .order-tracking-bar { height: 4px; background: var(--neutral); border-radius: 99px; overflow: hidden; margin-top: 4px; }
    .tracking-progress { height: 100%; background: var(--primary); border-radius: 99px; transition: width 0.3s; }
    .btn-load-more { display: block; margin: 20px auto 0; padding: 10px 32px; border: 1px solid var(--border); background: #fff; border-radius: 10px; cursor: pointer; font-size: 14px; &:hover:not(:disabled) { background: var(--neutral); } &:disabled { opacity: 0.5; } }
  `]
})
export class OrderListComponent implements OnInit {
  orders: Order[] = [];
  loading = true;
  loadingMore = false;
  hasMore = false;
  private page = 0;
  private readonly size = 10;

  constructor(private orderService: OrderService) {}

  ngOnInit(): void {
    this.orderService.getMyOrders(0, this.size).subscribe({
      next: res => {
        this.orders = res.content;
        this.hasMore = !res.content.length || res.number < res.totalPages - 1;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  loadMore(): void {
    this.loadingMore = true;
    this.page++;
    this.orderService.getMyOrders(this.page, this.size).subscribe({
      next: res => {
        this.orders = [...this.orders, ...res.content];
        this.hasMore = res.number < res.totalPages - 1;
        this.loadingMore = false;
      },
      error: () => { this.loadingMore = false; }
    });
  }

  statusLabel(s: OrderStatus): string {
    return ORDER_STATUS_LABELS[s] ?? s;
  }

  statusClass(s: OrderStatus): string {
    return 'status-badge badge-' + s.toLowerCase().replace(/_/g, '_');
  }

  trackingPercent(s: OrderStatus): number {
    const steps: OrderStatus[] = [
      'CONFIRMED', 'PREPARING', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'
    ];
    if (s === 'CANCELLED') return 0;
    const idx = steps.indexOf(s);
    return idx < 0 ? 0 : Math.round(((idx + 1) / steps.length) * 100);
  }
}
