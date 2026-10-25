import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Cart } from '../../../core/models/cart.model';
import { Order } from '../../../core/models/order.model';
import { CartService } from '../../../core/services/cart.service';
import { OrderService } from '../../../core/services/order.service';
import { CheckoutComponent } from './checkout.component';

describe('CheckoutComponent', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  let orders: jasmine.SpyObj<OrderService>;
  let cart: jasmine.SpyObj<CartService>;
  let router: Router;

  beforeEach(async () => {
    orders = jasmine.createSpyObj('OrderService', ['placeOrder']);
    cart = jasmine.createSpyObj('CartService', ['load', 'reset']);
    cart.load.and.returnValue(of(sampleCart()));

    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: orders },
        { provide: CartService, useValue: cart }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
  });

  it('does not place an order when the address is blank', () => {
    fixture.componentInstance.continueToPayment();
    expect(orders.placeOrder).not.toHaveBeenCalled();
    expect(fixture.componentInstance.submitted).toBeTrue();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Address is required');
  });

  it('goes to the order after cash on delivery', () => {
    orders.placeOrder.and.returnValue(of(sampleOrder()));
    fixture.componentInstance.form.shippingAddress = '1 Rue des Lilas';
    fixture.componentInstance.continueToPayment();
    expect(cart.reset).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/store/orders', 9]);
  });

  it('shows the backend error and keeps the form usable', () => {
    orders.placeOrder.and.returnValue(throwError(() => ({ error: { message: 'Insufficient stock' } })));
    fixture.componentInstance.form.shippingAddress = '1 Rue des Lilas';
    fixture.componentInstance.continueToPayment();
    fixture.detectChanges();
    expect(fixture.componentInstance.placing).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Insufficient stock');
  });
});

function sampleCart(): Cart {
  return {
    items: [{
      id: 8, productId: 5, productName: 'Dog Kibble', productImageUrl: null,
      unitPrice: 19.99, quantity: 1, lineTotal: 19.99,
      productAvailable: true, productStockQuantity: 4, addedAt: ''
    }],
    itemCount: 1,
    subtotal: 19.99,
    shippingFee: 5,
    total: 24.99,
    hasUnavailableItems: false
  };
}

function sampleOrder(): Order {
  return {
    id: 9,
    buyerId: 3,
    buyerFirstName: 'Owner',
    buyerLastName: 'User',
    buyerEmail: 'owner@store.test',
    status: 'CONFIRMED',
    paymentMethod: 'CASH_ON_DELIVERY',
    totalAmount: 24.99,
    shippingFee: 5,
    shippingAddress: '1 Rue des Lilas',
    shippingCity: 'Tunis',
    shippingCountry: 'TN',
    notes: null,
    items: [],
    payment: null,
    createdAt: '',
    updatedAt: ''
  };
}
