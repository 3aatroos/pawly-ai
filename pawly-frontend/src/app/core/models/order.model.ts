export type OrderStatus =
  | 'CONFIRMED'
  | 'PREPARING'
  | 'SHIPPED'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED';

export type PaymentMethod = 'STRIPE' | 'CASH_ON_DELIVERY';

export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'REFUNDED';

export interface OrderItem {
  id: number;
  productId: number | null;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface OrderPayment {
  id: number;
  status: PaymentStatus;
  amount: number;
  currency: string;
  stripePaymentIntentId: string | null;
  paidAt: string | null;
  createdAt: string;
}

export interface Order {
  id: number;
  buyerId: number;
  buyerFirstName: string;
  buyerLastName: string;
  buyerEmail: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  totalAmount: number;
  shippingFee: number;
  shippingAddress: string;
  shippingCity: string;
  shippingCountry: string;
  notes: string | null;
  items: OrderItem[];
  payment: OrderPayment | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrderRequest {
  paymentMethod: PaymentMethod;
  shippingAddress: string;
  shippingCity?: string;
  shippingCountry?: string;
  notes?: string;
}

export interface UpdateOrderStatusRequest {
  status: OrderStatus;
}

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  CONFIRMED:        'Confirmed',
  PREPARING:        'Preparing',
  SHIPPED:          'Shipped',
  IN_TRANSIT:       'In Transit',
  OUT_FOR_DELIVERY: 'Out for Delivery',
  DELIVERED:        'Delivered',
  CANCELLED:        'Cancelled',
};

export const ORDER_STATUS_STEPS: OrderStatus[] = [
  'CONFIRMED', 'PREPARING', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'
];
