import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CartService } from '../../../core/services/cart.service';
import { ProductRecommendation, StoreService } from '../../../core/services/store.service';
import { RecommendationsComponent } from './recommendations.component';

describe('RecommendationsComponent', () => {
  let fixture: ComponentFixture<RecommendationsComponent>;
  let store: jasmine.SpyObj<StoreService>;
  let cart: jasmine.SpyObj<CartService>;

  beforeEach(async () => {
    store = jasmine.createSpyObj('StoreService', ['getRecommendations']);
    cart = jasmine.createSpyObj('CartService', ['addItem']);
    const auth = jasmine.createSpyObj('AuthService', ['isVet', 'isAdmin']);
    store.getRecommendations.and.returnValue(of([recommendation('qlora')]));
    auth.isVet.and.returnValue(false);
    auth.isAdmin.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [RecommendationsComponent],
      providers: [
        provideRouter([]),
        { provide: StoreService, useValue: store },
        { provide: CartService, useValue: cart },
        { provide: AuthService, useValue: auth }
      ]
    }).compileComponents();
  });

  it('shows a model score and its reason', () => {
    fixture = TestBed.createComponent(RecommendationsComponent);
    fixture.detectChanges();
    expect(store.getRecommendations).toHaveBeenCalledWith(12);
    expect(fixture.nativeElement.textContent).toContain('Scored by the Pawly recommendation model');
    expect(fixture.nativeElement.textContent).toContain('Species match.');
  });

  it('uses the catalogue subtitle for a content-based score', () => {
    store.getRecommendations.and.returnValue(of([recommendation('content_based')]));
    fixture = TestBed.createComponent(RecommendationsComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Based on your pets and the catalogue');
  });

  it('shows an error state when the recommendation call fails', () => {
    store.getRecommendations.and.returnValue(throwError(() => ({ status: 503 })));
    fixture = TestBed.createComponent(RecommendationsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.loadError).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Recommendations are unavailable');
  });
});

function recommendation(source: 'qlora' | 'content_based'): ProductRecommendation {
  return {
    productId: 5,
    categoryId: 2,
    categoryName: 'Food',
    name: 'Dog Kibble',
    price: 19.99,
    isAvailable: true,
    averageRating: 5,
    reviewCount: 4,
    targetSpecies: 'dog',
    primaryImageUrl: null,
    score: 0.91,
    reason: 'Species match.',
    source
  };
}
