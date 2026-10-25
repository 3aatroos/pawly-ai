import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OrderService } from '../../../core/services/order.service';
import {
  Order, OrderStatus, ORDER_STATUS_LABELS, UpdateOrderStatusRequest
} from '../../../core/models/order.model';

@Component({
  selector: 'app-order-management',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="admin-orders-page">
      <h1 class="page-title">Order Management</h1>

      <!-- Filters -->
      <div class="filters">
        <input type="number" class="filter-input" placeholder="Order #"
               [(ngModel)]="filterOrderId" (ngModelChange)="search()" min="1" />

        <select class="filter-select" [(ngModel)]="filterStatus" (ngModelChange)="search()">
          <option value="">All statuses</option>
          @for (s of allStatuses; track s) {
            <option [value]="s">{{ statusLabel(s) }}</option>
          }
        </select>

        <button class="btn-reset" (click)="resetFilters()">Reset</button>
      </div>

      @if (loading) {
        <div class="loading">Loading orders…</div>
      } @else if (orders.length === 0) {
        <div class="empty">No orders found.</div>
      } @else {
        <div class="orders-table-wrapper">
          <table class="orders-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Customer</th>
                <th>Date</th>
                <th>Items</th>
                <th>Total</th>
                <th>Payment</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (order of orders; track order.id) {
                <tr>
                  <td class="order-id">{{ order.id }}</td>
                  <td>
                    <div class="customer-name">{{ order.buyerFirstName }} {{ order.buyerLastName }}</div>
                    <div class="customer-email">{{ order.buyerEmail }}</div>
                  </td>
                  <td>{{ order.createdAt | date:'dd/MM/yy HH:mm' }}</td>
                  <td>{{ order.items.length }}</td>
                  <td class="order-total">{{ order.totalAmount | currency:'EUR':'symbol':'1.2-2' }}</td>
                  <td>
                    <span class="payment-method">
                      {{ order.paymentMethod === 'STRIPE' ? '💳' : '💵' }}
                    </span>
                    @if (order.payment) {
                      <span class="payment-status" [ngClass]="paymentClass(order.payment.status)">
                        {{ order.payment.status }}
                      </span>
                    }
                  </td>
                  <td>
                    @if (isTerminal(order.status)) {
                      <span class="status-badge" [ngClass]="statusClass(order.status)">
                        {{ statusLabel(order.status) }}
                      </span>
                    } @else {
                      <select class="status-select"
                              [ngModel]="order.status"
                              (ngModelChange)="changeStatus(order, $event)"
                              [disabled]="updatingId === order.id">
                        @for (s of allStatuses; track s) {
                          @if (!isTerminal(s) || s === order.status) {
                            <option [value]="s">{{ statusLabel(s) }}</option>
                          }
                        }
                        <option value="DELIVERED">Delivered</option>
                        <option value="CANCELLED">Cancelled</option>
                      </select>
                    }
                  </td>
                  <td>
                    <a [routerLink]="['/store/orders', order.id]" class="btn-detail">
                      View
                    </a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        <div class="pagination">
          <button [disabled]="page === 0" (click)="prevPage()">← Prev</button>
          <span>Page {{ page + 1 }} / {{ totalPages }}</span>
          <button [disabled]="page >= totalPages - 1" (click)="nextPage()">Next →</button>
        </div>
      }

      @if (statusError) {
        <div class="toast-error">{{ statusError }}</div>
      }
    </div>
  `,
  styles: [`
    .admin-orders-page { }
    .page-title { font-size: 22px; font-weight: 700; margin-bottom: 20px; }
    .filters { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
    .filter-input, .filter-select { padding: 8px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 13px; min-width: 140px; }
    .btn-reset { padding: 8px 16px; border: 1px solid var(--border); background: #fff; border-radius: 8px; cursor: pointer; font-size: 13px; &:hover { background: var(--neutral); } }
    .loading, .empty { text-align: center; padding: 40px; color: var(--text-soft); }
    .orders-table-wrapper { overflow-x: auto; }
    .orders-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .orders-table th { background: var(--neutral); padding: 10px 14px; text-align: left; font-weight: 600; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }
    .orders-table td { padding: 12px 14px; border-bottom: 1px solid var(--border); vertical-align: middle; }
    .order-id { font-weight: 700; font-size: 14px; }
    .customer-name { font-weight: 600; }
    .customer-email { font-size: 11px; color: var(--text-soft); }
    .order-total { font-weight: 700; }
    .payment-method { font-size: 18px; margin-right: 4px; }
    .payment-status { padding: 2px 8px; border-radius: 99px; font-size: 11px; font-weight: 600; }
    .pay-succeeded { background: #dcfce7; color: #15803d; }
    .pay-pending   { background: #fef9c3; color: #92400e; }
    .pay-failed    { background: #fee2e2; color: #dc2626; }
    .status-badge { padding: 4px 10px; border-radius: 99px; font-size: 12px; font-weight: 600; }
    .badge-confirmed        { background: #e0f2fe; color: #0369a1; }
    .badge-preparing        { background: #fef9c3; color: #92400e; }
    .badge-shipped          { background: #ede9fe; color: #6d28d9; }
    .badge-in_transit       { background: #e0e7ff; color: #3730a3; }
    .badge-out_for_delivery { background: #ffedd5; color: #c2410c; }
    .badge-delivered        { background: #dcfce7; color: #15803d; }
    .badge-cancelled        { background: #fee2e2; color: #dc2626; }
    .status-select { padding: 5px 8px; border: 1px solid var(--border); border-radius: 7px; font-size: 13px; cursor: pointer; &:disabled { opacity: 0.5; } }
    .btn-detail { font-size: 12px; color: var(--primary); text-decoration: none; padding: 4px 10px; border: 1px solid var(--primary); border-radius: 6px; &:hover { background: var(--primary-light); } }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 20px; font-size: 13px; button { padding: 6px 16px; border: 1px solid var(--border); background: #fff; border-radius: 8px; cursor: pointer; &:disabled { opacity: 0.4; cursor: not-allowed; } &:hover:not(:disabled) { background: var(--neutral); } } }
    .toast-error { position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%); background: var(--danger); color: #fff; padding: 10px 24px; border-radius: 10px; font-size: 13px; z-index: 999; }
  `]
})
export class OrderManagementComponent implements OnInit {
  orders: Order[] = [];
  loading = true;
  page = 0;
  totalPages = 1;
  updatingId: number | null = null;
  statusError: string | null = null;
  filterStatus: OrderStatus | '' = '';
  filterOrderId: number | null = null;

  readonly allStatuses: OrderStatus[] = [
    'CONFIRMED', 'PREPARING', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'
  ];

  constructor(private orderService: OrderService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.orderService.getAllOrders(
      this.filterStatus || undefined,
      this.filterOrderId ?? undefined,
      this.page, 20
    ).subscribe({
      next: res => {
        this.orders = res.content;
        this.totalPages = res.totalPages;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  search(): void {
    this.page = 0;
    this.load();
  }

  resetFilters(): void {
    this.filterStatus = '';
    this.filterOrderId = null;
    this.page = 0;
    this.load();
  }

  prevPage(): void { if (this.page > 0) { this.page--; this.load(); } }
  nextPage(): void { if (this.page < this.totalPages - 1) { this.page++; this.load(); } }

  changeStatus(order: Order, newStatus: OrderStatus): void {
    this.updatingId = order.id;
    this.statusError = null;
    const req: UpdateOrderStatusRequest = { status: newStatus };
    this.orderService.updateStatus(order.id, req).subscribe({
      next: updated => {
        const idx = this.orders.findIndex(o => o.id === order.id);
        if (idx >= 0) this.orders[idx] = updated;
        this.updatingId = null;
      },
      error: err => {
        this.statusError = err.error?.message ?? 'Failed to update status';
        this.updatingId = null;
        setTimeout(() => this.statusError = null, 4000);
      }
    });
  }

  isTerminal(s: OrderStatus): boolean {
    return s === 'DELIVERED' || s === 'CANCELLED';
  }

  statusLabel(s: OrderStatus): string {
    return ORDER_STATUS_LABELS[s] ?? s;
  }

  statusClass(s: OrderStatus): string {
    return 'status-badge badge-' + s.toLowerCase().replace(/_/g, '_');
  }

  paymentClass(s: string): string {
    const m: Record<string, string> = {
      SUCCEEDED: 'pay-succeeded', PENDING: 'pay-pending', FAILED: 'pay-failed'
    };
    return m[s] ?? '';
  }
}
