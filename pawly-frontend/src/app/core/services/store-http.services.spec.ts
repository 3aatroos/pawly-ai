import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CartService } from './cart.service';
import { OrderService } from './order.service';
import { PaymentService } from './payment.service';
import { StoreService } from './store.service';
import { environment } from '../../../environments/environment';

describe('Pet Store HTTP services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpTestingController);
    expect(TestBed.inject(HttpClient)).toBeTruthy();
  });

  afterEach(() => http.verify());

  it('searches and filters the product catalogue', () => {
    const store = TestBed.inject(StoreService);
    store.getProducts(0, 12, 3, 'dog', 'kibble', 5, 40, 'price,asc').subscribe();

    const req = http.expectOne(call =>
      call.url === `${environment.apiUrl}/store/products`
      && call.params.get('search') === 'kibble'
      && call.params.get('species') === 'dog'
      && call.params.get('categoryId') === '3'
      && call.params.get('minPrice') === '5'
      && call.params.get('maxPrice') === '40'
      && call.params.get('sort') === 'price,asc');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 12 });
  });

  it('loads one product and recommendations', () => {
    const store = TestBed.inject(StoreService);
    store.getProductById(5).subscribe();
    store.getRecommendations(12).subscribe();

    http.expectOne(`${environment.apiUrl}/store/products/5`).flush({});
    const recos = http.expectOne(call => call.url === `${environment.apiUrl}/store/recommendations`);
    expect(recos.request.params.get('limit')).toBe('12');
    recos.flush([]);
  });

  it('posts a cart line and reads the cart', () => {
    const cart = TestBed.inject(CartService);
    cart.addItem({ productId: 5, quantity: 2 }).subscribe();
    const req = http.expectOne(`${environment.apiUrl}/store/cart`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ productId: 5, quantity: 2 });
    req.flush(emptyCart());
    expect(cart.itemCount()).toBe(0);
  });

  it('places an order and reads the buyer history', () => {
    const orders = TestBed.inject(OrderService);
    orders.placeOrder({
      paymentMethod: 'CASH_ON_DELIVERY',
      shippingAddress: '1 Rue des Lilas'
    }).subscribe();
    const created = http.expectOne(`${environment.apiUrl}/store/orders`);
    expect(created.request.method).toBe('POST');
    created.flush({ id: 9 });

    orders.getMyOrders(0, 10).subscribe();
    const mine = http.expectOne(call => call.url === `${environment.apiUrl}/store/orders/my`);
    expect(mine.request.params.get('page')).toBe('0');
    mine.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });

    orders.getMyOrderById(9).subscribe();
    http.expectOne(`${environment.apiUrl}/store/orders/my/9`).flush({ id: 9 });
  });

  it('asks the backend for a Stripe payment intent', () => {
    const payments = TestBed.inject(PaymentService);
    payments.createIntent(9).subscribe(body => {
      expect(body.clientSecret).toBe('sec_test');
    });
    const req = http.expectOne(`${environment.apiUrl}/store/payments/9/intent`);
    expect(req.request.method).toBe('POST');
    req.flush({
      orderId: 9,
      clientSecret: 'sec_test',
      paymentIntentId: 'pi_test',
      amount: 19.99,
      currency: 'eur'
    });
  });
});

function emptyCart() {
  return {
    items: [],
    itemCount: 0,
    subtotal: 0,
    shippingFee: 0,
    total: 0,
    hasUnavailableItems: false
  };
}
