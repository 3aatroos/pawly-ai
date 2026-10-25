import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { Order } from '../../../core/models/order.model';
import { OrderService } from '../../../core/services/order.service';
import { OrderDetailComponent } from './order-detail.component';

describe('OrderDetailComponent', () => {
  let fixture: ComponentFixture<OrderDetailComponent>;
  let orders: jasmine.SpyObj<OrderService>;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    orders = jasmine.createSpyObj('OrderService', ['getMyOrderById', 'getOrderById']);
    auth = jasmine.createSpyObj('AuthService', ['isAdmin']);
    orders.getMyOrderById.and.returnValue(of(sampleOrder()));
    orders.getOrderById.and.returnValue(of(sampleOrder()));
    auth.isAdmin.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: orders },
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '9' }) } } }
      ]
    }).compileComponents();
  });

  it('loads the buyer order and shows the address', () => {
    fixture = TestBed.createComponent(OrderDetailComponent);
    fixture.detectChanges();
    expect(orders.getMyOrderById).toHaveBeenCalledWith(9);
    expect(orders.getOrderById).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('1 Rue des Lilas');
    expect(fixture.nativeElement.textContent).toContain('Dog Kibble');
    expect(fixture.nativeElement.textContent).not.toContain('Customer');
  });

  it('loads the admin order and shows the customer', () => {
    auth.isAdmin.and.returnValue(true);
    fixture = TestBed.createComponent(OrderDetailComponent);
    fixture.detectChanges();
    expect(orders.getOrderById).toHaveBeenCalledWith(9);
    expect(fixture.nativeElement.textContent).toContain('owner@store.test');
  });

  it('leaves an error state when the order is missing', () => {
    orders.getMyOrderById.and.returnValue(throwError(() => ({ status: 404 })));
    fixture = TestBed.createComponent(OrderDetailComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.loading).toBeFalse();
    expect(fixture.componentInstance.order).toBeNull();
  });

  it('refreshes the same order from the tracking control', () => {
    fixture = TestBed.createComponent(OrderDetailComponent);
    fixture.detectChanges();
    fixture.componentInstance.onRefresh();
    expect(orders.getMyOrderById).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.refreshing).toBeFalse();
  });
});

function sampleOrder(): Order {
  return {
    id: 9,
    buyerId: 3,
    buyerFirstName: 'Owner',
    buyerLastName: 'User',
    buyerEmail: 'owner@store.test',
    status: 'SHIPPED',
    paymentMethod: 'STRIPE',
    totalAmount: 24.99,
    shippingFee: 5,
    shippingAddress: '1 Rue des Lilas',
    shippingCity: 'Tunis',
    shippingCountry: 'TN',
    notes: null,
    items: [{ id: 1, productId: 5, productName: 'Dog Kibble', quantity: 1, unitPrice: 19.99, lineTotal: 19.99 }],
    payment: {
      id: 4,
      status: 'FAILED',
      amount: 24.99,
      currency: 'eur',
      stripePaymentIntentId: 'pi_1',
      paidAt: null,
      createdAt: ''
    },
    createdAt: '2026-10-25T10:00:00',
    updatedAt: '2026-10-25T10:00:00'
  };
}
