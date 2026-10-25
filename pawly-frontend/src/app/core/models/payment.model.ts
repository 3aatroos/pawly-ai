export interface PaymentIntentResponse {
  orderId: number;
  clientSecret: string;
  paymentIntentId: string;
  amount: number;
  currency: string;
}

export type StripePaymentState =
  | 'idle'
  | 'loading'
  | 'ready'
  | 'processing'
  | 'succeeded'
  | 'failed';
