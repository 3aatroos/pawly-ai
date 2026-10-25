import {
  Component, OnInit, OnDestroy, AfterViewInit, Input, Output, EventEmitter
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { environment } from '../../../../environments/environment';
import { PaymentService } from '../../../core/services/payment.service';
import { PaymentIntentResponse, StripePaymentState } from '../../../core/models/payment.model';

// Stripe.js is loaded via CDN in index.html — declared here so TypeScript does not complain.
declare const Stripe: (key: string) => StripeInstance;

interface StripeInstance {
  elements: (opts?: object) => StripeElements;
  confirmCardPayment: (clientSecret: string, data: object) => Promise<StripePaymentResult>;
}

interface StripeElements {
  create: (type: string, opts?: object) => StripeElement;
  getElement: (type: string) => StripeElement | null;
}

interface StripeElement {
  mount: (selector: string) => void;
  destroy: () => void;
  on: (event: string, handler: (e: StripeElementEvent) => void) => void;
}

interface StripeElementEvent {
  error?: { message: string };
  complete?: boolean;
  empty?: boolean;
}

interface StripePaymentResult {
  error?: { message: string; type: string };
  paymentIntent?: { status: string; id: string };
}

@Component({
  selector: 'app-stripe-payment',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="stripe-payment">
      <h3 class="stripe-title">💳 Card payment — Test Mode</h3>

      @if (state === 'loading') {
        <div class="stripe-loading">Initialising secure payment…</div>
      }

      @if (initError) {
        <div class="stripe-error">{{ initError }}</div>
      }

      <div id="card-element" [class.hidden]="state === 'loading' || !!initError"></div>

      @if (cardError) {
        <div class="stripe-card-error">{{ cardError }}</div>
      }

      <div class="stripe-test-hint">
        <strong>Test card:</strong> 4242 4242 4242 4242 — any future date — any CVC
      </div>

      <button
        class="btn-pay"
        [disabled]="state === 'loading' || state === 'processing' || !cardComplete || !!initError"
        (click)="pay()">
        @if (state === 'processing') {
          <span class="btn-spinner"></span> Processing…
        } @else {
          Pay {{ amount | currency:'EUR':'symbol':'1.2-2' }}
        }
      </button>
    </div>
  `,
  styles: [`
    .stripe-payment { }
    .stripe-title { font-size: 15px; font-weight: 700; margin-bottom: 16px; }
    .stripe-loading { text-align: center; padding: 20px; color: var(--text-soft); font-size: 13px; }
    .stripe-error { background: #fee2e2; border: 1px solid #fca5a5; border-radius: 8px; padding: 10px 14px; font-size: 13px; color: #dc2626; margin-bottom: 12px; }
    #card-element { padding: 14px; border: 1px solid var(--border); border-radius: 10px; background: #fff; margin-bottom: 10px; &.hidden { display: none; } }
    .stripe-card-error { font-size: 12px; color: var(--danger); margin-bottom: 10px; }
    .stripe-test-hint { background: #f0fdf4; border: 1px solid #86efac; border-radius: 8px; padding: 10px 14px; font-size: 12px; color: #15803d; margin-bottom: 16px; }
    .btn-pay { width: 100%; padding: 14px; background: #635bff; color: #fff; border: none; border-radius: 12px; font-weight: 700; font-size: 15px; cursor: pointer; transition: opacity 0.2s; display: flex; align-items: center; justify-content: center; gap: 8px; &:hover:not(:disabled) { opacity: 0.9; } &:disabled { opacity: 0.5; cursor: not-allowed; } }
    .btn-spinner { width: 16px; height: 16px; border: 2px solid rgba(255,255,255,0.4); border-top-color: #fff; border-radius: 50%; animation: spin 0.7s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
  `]
})
export class StripePaymentComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input({ required: true }) orderId!: number;
  @Input() amount = 0;
  @Output() succeeded = new EventEmitter<string>();
  @Output() failed    = new EventEmitter<string>();

  state: StripePaymentState = 'loading';
  cardError: string | null = null;
  initError: string | null = null;
  cardComplete = false;

  private stripe: StripeInstance | null = null;
  private elements: StripeElements | null = null;
  private cardElement: StripeElement | null = null;
  private clientSecret: string | null = null;

  constructor(private paymentService: PaymentService) {}

  ngOnInit(): void {
    this.paymentService.createIntent(this.orderId).subscribe({
      next: (res: PaymentIntentResponse) => {
        this.clientSecret = res.clientSecret;
        this.state = 'ready';
        // mountCard() is called in ngAfterViewInit, but if intent arrives after that, call now
        if (this.stripe) this.mountCard();
      },
      error: err => {
        this.initError = err.error?.message ?? 'Could not initialise payment. Please try again.';
        this.state = 'failed';
      }
    });
  }

  ngAfterViewInit(): void {
    if (typeof Stripe === 'undefined') {
      this.initError = 'Stripe.js failed to load. Check your internet connection.';
      return;
    }
    this.stripe   = Stripe(environment.stripePublishableKey);
    this.elements = this.stripe.elements({ locale: 'en' });
    if (this.clientSecret) this.mountCard();
  }

  ngOnDestroy(): void {
    this.cardElement?.destroy();
  }

  pay(): void {
    if (!this.stripe || !this.clientSecret || !this.cardElement) return;
    this.state = 'processing';
    this.cardError = null;

    this.stripe.confirmCardPayment(this.clientSecret, {
      payment_method: { card: this.cardElement }
    }).then((result: StripePaymentResult) => {
      if (result.error) {
        this.cardError = result.error.message ?? 'Payment failed';
        this.state = 'ready';
        this.failed.emit(result.error.message ?? 'Payment failed');
      } else if (result.paymentIntent?.status === 'succeeded') {
        this.state = 'succeeded';
        this.succeeded.emit(result.paymentIntent.id);
      }
    });
  }

  private mountCard(): void {
    if (!this.elements) return;
    this.cardElement = this.elements.create('card', {
      style: {
        base: {
          fontSize: '15px',
          color: '#1a1a2e',
          fontFamily: 'system-ui, -apple-system, sans-serif',
          '::placeholder': { color: '#9ca3af' }
        },
        invalid: { color: '#dc2626' }
      }
    });
    this.cardElement.mount('#card-element');
    this.cardElement.on('change', (e: StripeElementEvent) => {
      this.cardError  = e.error?.message ?? null;
      this.cardComplete = e.complete ?? false;
    });
  }
}
