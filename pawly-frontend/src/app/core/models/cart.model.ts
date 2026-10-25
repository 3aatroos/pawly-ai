export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  productAvailable: boolean;
  productStockQuantity: number;
  addedAt: string;
}

export interface Cart {
  items: CartItem[];
  itemCount: number;
  subtotal: number;
  shippingFee: number;
  total: number;
  hasUnavailableItems: boolean;
}

export interface AddToCartRequest {
  productId: number;
  quantity: number;
}

export interface UpdateCartItemRequest {
  quantity: number;
}
