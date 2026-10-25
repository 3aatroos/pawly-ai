import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CartService } from '../../../core/services/cart.service';
import { ProductSummary, StoreService } from '../../../core/services/store.service';
import { ProductFilterComponent } from '../shared/product-filter.component';
import { ProductListComponent } from './product-list.component';

describe('ProductListComponent', () => {
  let fixture: ComponentFixture<ProductListComponent>;
  let store: jasmine.SpyObj<StoreService>;
  let cart: jasmine.SpyObj<CartService>;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    store = jasmine.createSpyObj('StoreService', ['getProducts', 'getCategories', 'deleteProduct']);
    cart = jasmine.createSpyObj('CartService', ['addItem', 'itemCount']);
    auth = jasmine.createSpyObj('AuthService', ['isVet', 'isAdmin']);
    store.getProducts.and.returnValue(of(page([product()])));
    store.getCategories.and.returnValue(of([{ id: 2, name: 'Food' }]));
    cart.itemCount.and.returnValue(0);
    auth.isVet.and.returnValue(false);
    auth.isAdmin.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [
        provideRouter([]),
        { provide: StoreService, useValue: store },
        { provide: CartService, useValue: cart },
        { provide: AuthService, useValue: auth }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
  });

  it('lists the catalogue returned by the store', () => {
    expect(fixture.nativeElement.textContent).toContain('Dog Kibble');
    expect(fixture.nativeElement.textContent).toContain('1 products available');
    expect(fixture.nativeElement.textContent).not.toContain('Add Product');
  });

  it('sends a search and a species filter back to the first page', () => {
    const filter = fixture.debugElement.query(By.directive(ProductFilterComponent)).componentInstance as ProductFilterComponent;
    filter.state = { ...filter.state, search: 'kibble', species: 'dog', categoryId: 2, minPrice: 5, maxPrice: 40 };
    filter.emit();

    expect(store.getProducts).toHaveBeenCalledWith(0, 12, 2, 'dog', 'kibble', 5, 40, 'createdAt,desc');
  });

  it('shows the add action for a veterinarian', () => {
    auth.isVet.and.returnValue(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Add Product');
  });

  it('shows an empty catalogue when the request fails', () => {
    store.getProducts.and.returnValue(throwError(() => ({ error: { message: 'down' } })));
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(fixture.componentInstance.loading).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('No products found');
  });

  it('reports a cart error without leaving the add button spinning', () => {
    spyOn(window, 'alert');
    cart.addItem.and.returnValue(throwError(() => ({ error: { message: 'Insufficient stock' } })));
    fixture.componentInstance.addToCart(product());
    expect(fixture.componentInstance.addingId).toBeNull();
    expect(window.alert).toHaveBeenCalledWith('Insufficient stock');
  });
});

function product(): ProductSummary {
  return {
    id: 5,
    categoryId: 2,
    categoryName: 'Food',
    name: 'Dog Kibble',
    price: 19.99,
    isAvailable: true,
    averageRating: 5,
    reviewCount: 4,
    targetSpecies: 'dog',
    primaryImageUrl: null,
    stockQuantity: null,
    createdAt: ''
  };
}

function page(content: ProductSummary[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 12 };
}
