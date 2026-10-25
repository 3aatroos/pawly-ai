import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Order, CreateOrderRequest, UpdateOrderStatusRequest, OrderStatus
} from '../models/order.model';
import { PageResponse } from './user.service';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly base = `${environment.apiUrl}/store/orders`;

  constructor(private http: HttpClient) {}

  // ── Buyer ─────────────────────────────────────────────────

  placeOrder(req: CreateOrderRequest): Observable<Order> {
    return this.http.post<Order>(this.base, req);
  }

  getMyOrders(page = 0, size = 10): Observable<PageResponse<Order>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Order>>(`${this.base}/my`, { params });
  }

  getMyOrderById(id: number): Observable<Order> {
    return this.http.get<Order>(`${this.base}/my/${id}`);
  }

  // ── Admin ─────────────────────────────────────────────────

  getAllOrders(status?: OrderStatus, orderId?: number, page = 0, size = 20): Observable<PageResponse<Order>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status)  params = params.set('status', status);
    if (orderId) params = params.set('orderId', orderId);
    return this.http.get<PageResponse<Order>>(this.base, { params });
  }

  getOrderById(id: number): Observable<Order> {
    return this.http.get<Order>(`${this.base}/${id}`);
  }

  updateStatus(id: number, req: UpdateOrderStatusRequest): Observable<Order> {
    return this.http.patch<Order>(`${this.base}/${id}/status`, req);
  }
}
