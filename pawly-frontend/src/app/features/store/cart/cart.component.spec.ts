import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Cart, CartItem } from '../../../core/models/cart.model';
import { CartService } from '../../../core/services/cart.service';
import { CartComponent } from './cart.component';

describe('CartComponent', () => {
  let fixture: ComponentFixture<CartComponent>;
  let cartService: jasmine.SpyObj<CartService>;

  beforeEach(async () => {
    cartService = jasmine.createSpyObj('CartService', ['load', 'updateItem', 'removeItem', 'clear']);
    cartService.load.and.returnValue(of(sampleCart()));

    await TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [provideRouter([]), { provide: CartService, useValue: cartService }]
    }).compileComponents();

    fixture = TestBed.createComponent(CartComponent);
    fixture.detectChanges();
  });

  it('shows the line, the shipping fee and the total', () => {
    expect(fixture.nativeElement.textContent).toContain('Dog Kibble');
    expect(fixture.nativeElement.textContent).toContain('19.99');
  });

  it('asks for one more unit', () => {
    cartService.updateItem.and.returnValue(of(sampleCart()));
    fixture.componentInstance.increment(sampleCart().items[0]);
    expect(cartService.updateItem).toHaveBeenCalledWith(8, { quantity: 2 });
  });

  it('shows the server message when the stock is insufficient', () => {
    cartService.updateItem.and.returnValue(throwError(() => ({ error: { message: 'Insufficient stock' } })));
    fixture.componentInstance.increment(sampleCart().items[0]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Insufficient stock');
  });
});

function sampleCart(): Cart {
  const item: CartItem = {
    id: 8,
    productId: 5,
    productName: 'Dog Kibble',
    productImageUrl: null,
    unitPrice: 19.99,
    quantity: 1,
    lineTotal: 19.99,
    productAvailable: true,
    productStockQuantity: 4,
    addedAt: ''
  };
  return {
    items: [item],
    itemCount: 1,
    subtotal: 19.99,
    shippingFee: 5,
    total: 24.99,
    hasUnavailableItems: false
  };
}
