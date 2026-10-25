import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PaymentIntentResponse } from '../models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly base = `${environment.apiUrl}/store/payments`;

  constructor(private http: HttpClient) {}

  createIntent(orderId: number): Observable<PaymentIntentResponse> {
    return this.http.post<PaymentIntentResponse>(`${this.base}/${orderId}/intent`, {});
  }
}
