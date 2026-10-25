import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PaymentService } from '../../../core/services/payment.service';
import { StripePaymentComponent } from './stripe-payment.component';

describe('StripePaymentComponent', () => {
  let fixture: ComponentFixture<StripePaymentComponent>;
  let payments: jasmine.SpyObj<PaymentService>;
  let confirmCardPayment: jasmine.Spy;

  beforeEach(async () => {
    payments = jasmine.createSpyObj('PaymentService', ['createIntent']);
    confirmCardPayment = jasmine.createSpy('confirmCardPayment').and.returnValue(Promise.resolve({
      paymentIntent: { status: 'succeeded', id: 'pi_1' }
    }));
    (globalThis as unknown as { Stripe: unknown }).Stripe = () => ({
      elements: () => ({
        create: () => ({
          mount: jasmine.createSpy('mount'),
          destroy: jasmine.createSpy('destroy'),
          on: jasmine.createSpy('on')
        })
      }),
      confirmCardPayment
    });

    await TestBed.configureTestingModule({
      imports: [StripePaymentComponent],
      providers: [{ provide: PaymentService, useValue: payments }]
    }).compileComponents();
  });

  afterEach(() => {
    delete (globalThis as { Stripe?: unknown }).Stripe;
  });

  it('reports a refused payment initialisation', () => {
    payments.createIntent.and.returnValue(throwError(() => ({ error: { message: 'Order is already paid' } })));
    fixture = TestBed.createComponent(StripePaymentComponent);
    fixture.componentRef.setInput('orderId', 9);
    fixture.detectChanges();
    expect(fixture.componentInstance.state).toBe('failed');
    expect(fixture.nativeElement.textContent).toContain('Order is already paid');
  });

  it('emits the Stripe error when the card is declined', async () => {
    payments.createIntent.and.returnValue(of({
      orderId: 9,
      clientSecret: 'sec_test',
      paymentIntentId: 'pi_1',
      amount: 24.99,
      currency: 'eur'
    }));
    confirmCardPayment.and.returnValue(Promise.resolve({ error: { message: 'Your card was declined' } }));
    fixture = TestBed.createComponent(StripePaymentComponent);
    fixture.componentRef.setInput('orderId', 9);
    fixture.detectChanges();
    const failed = jasmine.createSpy('failed');
    fixture.componentInstance.failed.subscribe(failed);

    fixture.componentInstance.pay();
    await fixture.whenStable();

    expect(failed).toHaveBeenCalledWith('Your card was declined');
    expect(fixture.componentInstance.state).toBe('ready');
  });
});
