import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Order } from '../../../core/models/order.model';
import { OrderService } from '../../../core/services/order.service';
import { OrderListComponent } from './order-list.component';

describe('OrderListComponent', () => {
  let fixture: ComponentFixture<OrderListComponent>;
  let orders: jasmine.SpyObj<OrderService>;

  beforeEach(async () => {
    orders = jasmine.createSpyObj('OrderService', ['getMyOrders']);
    orders.getMyOrders.and.returnValue(of({
      content: [sampleOrder()],
      totalElements: 11,
      totalPages: 2,
      number: 0,
      size: 10
    }));

    await TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [provideRouter([]), { provide: OrderService, useValue: orders }]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderListComponent);
    fixture.detectChanges();
  });

  it('shows the buyer history and a progress for the local status steps', () => {
    expect(fixture.nativeElement.textContent).toContain('# 9');
    expect(fixture.componentInstance.trackingPercent('SHIPPED')).toBe(50);
    expect(fixture.componentInstance.trackingPercent('CANCELLED')).toBe(0);
    expect(fixture.componentInstance.hasMore).toBeTrue();
  });

  it('appends the next page', () => {
    orders.getMyOrders.and.returnValue(of({
      content: [{ ...sampleOrder(), id: 10 }],
      totalElements: 11,
      totalPages: 2,
      number: 1,
      size: 10
    }));
    fixture.componentInstance.loadMore();
    expect(orders.getMyOrders).toHaveBeenCalledWith(1, 10);
    expect(fixture.componentInstance.orders.map(order => order.id)).toEqual([9, 10]);
    expect(fixture.componentInstance.hasMore).toBeFalse();
  });

  it('stops the spinner when the history cannot be loaded', () => {
    orders.getMyOrders.and.returnValue(throwError(() => ({ status: 500 })));
    fixture.componentInstance.ngOnInit();
    expect(fixture.componentInstance.loading).toBeFalse();
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
    paymentMethod: 'CASH_ON_DELIVERY',
    totalAmount: 24.99,
    shippingFee: 5,
    shippingAddress: '1 Rue des Lilas',
    shippingCity: 'Tunis',
    shippingCountry: 'TN',
    notes: null,
    items: [{ id: 1, productId: 5, productName: 'Dog Kibble', quantity: 1, unitPrice: 19.99, lineTotal: 19.99 }],
    payment: null,
    createdAt: '2026-10-25T10:00:00',
    updatedAt: '2026-10-25T10:00:00'
  };
}
