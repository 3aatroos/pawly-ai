import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CartService } from '../../../core/services/cart.service';
import { ProductDetail, StoreService } from '../../../core/services/store.service';
import { ProductDetailsComponent } from './product-details.component';

describe('ProductDetailsComponent', () => {
  let fixture: ComponentFixture<ProductDetailsComponent>;
  let store: jasmine.SpyObj<StoreService>;
  let cart: jasmine.SpyObj<CartService>;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    store = jasmine.createSpyObj('StoreService', ['getProductById', 'deleteProduct']);
    cart = jasmine.createSpyObj('CartService', ['addItem']);
    auth = jasmine.createSpyObj('AuthService', ['isVet', 'isAdmin']);
    store.getProductById.and.returnValue(of(detail()));
    auth.isVet.and.returnValue(false);
    auth.isAdmin.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [ProductDetailsComponent],
      providers: [
        provideRouter([]),
        { provide: StoreService, useValue: store },
        { provide: CartService, useValue: cart },
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductDetailsComponent);
    fixture.detectChanges();
  });

  it('shows the product loaded from the route', () => {
    expect(store.getProductById).toHaveBeenCalledWith(5);
    expect(fixture.nativeElement.textContent).toContain('Dog Kibble');
    expect(fixture.nativeElement.textContent).toContain('Complete food');
    expect(fixture.componentInstance.canManage).toBeFalse();
  });

  it('lets a veterinarian manage the product', () => {
    auth.isVet.and.returnValue(true);
    expect(fixture.componentInstance.canManage).toBeTrue();
  });

  it('shows the cart error when the product is unavailable', () => {
    cart.addItem.and.returnValue(throwError(() => ({ error: { message: 'Product is not available' } })));
    fixture.componentInstance.addToCart();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Product is not available');
  });

  it('stops loading when the product cannot be read', () => {
    store.getProductById.and.returnValue(throwError(() => ({ status: 404 })));
    fixture.componentInstance.ngOnInit();
    expect(fixture.componentInstance.loading).toBeFalse();
    expect(fixture.componentInstance.product).toBeNull();
  });
});

function detail(): ProductDetail {
  return {
    id: 5,
    categoryId: 2,
    categoryName: 'Food',
    name: 'Dog Kibble',
    price: 19.99,
    isAvailable: false,
    averageRating: 5,
    reviewCount: 4,
    targetSpecies: 'dog',
    primaryImageUrl: null,
    stockQuantity: 0,
    createdAt: '',
    description: 'Complete food',
    createdById: 1,
    createdByName: 'Vet User',
    images: [],
    updatedAt: ''
  };
}
