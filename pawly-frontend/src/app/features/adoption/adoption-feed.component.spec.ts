import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AdoptionService } from '../../core/services/adoption.service';
import { AdoptionFeedComponent } from './adoption-feed.component';

describe('AdoptionFeedComponent', () => {
  let fixture: ComponentFixture<AdoptionFeedComponent>;
  let component: AdoptionFeedComponent;
  let adoption: jasmine.SpyObj<AdoptionService>;

  beforeEach(async () => {
    adoption = jasmine.createSpyObj('AdoptionService', ['list', 'match']);
    const auth = jasmine.createSpyObj('AuthService', ['isVet', 'isAdmin']);
    auth.isVet.and.returnValue(false);
    auth.isAdmin.and.returnValue(false);
    adoption.list.and.returnValue(of([
      post(1, 'Louna', 'chat', 'Tunis'),
      post(2, 'Barki', 'chien', 'Tunis')
    ]));

    await TestBed.configureTestingModule({
      imports: [AdoptionFeedComponent],
      providers: [
        provideRouter([]),
        { provide: AdoptionService, useValue: adoption },
        { provide: AuthService, useValue: auth }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdoptionFeedComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('filters the feed by species and search', () => {
    component.species = 'chat';
    expect(component.visible.map(item => item.animalName)).toEqual(['Louna']);
    component.species = '';
    component.search = 'barki';
    expect(component.visible.map(item => item.id)).toEqual([2]);
  });

  it('shows the matching response time and the score badge', () => {
    adoption.match.and.returnValue(of({
      matches: [{ postId: 1, score: 74, reason: 'Chat calme à Tunis.', warning: null }]
    }));
    component.lifestyle.housing = 'appartement';

    fixture.nativeElement.querySelector('[data-testid="find-match"]').click();
    fixture.detectChanges();

    expect(component.matchMs).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="match-time"]').textContent).toContain('ms');
    expect(fixture.nativeElement.querySelector('[data-testid="match-badge"]').textContent).toContain('74% match');
  });

  it('keeps the feed usable when matching fails', () => {
    adoption.match.and.returnValue(throwError(() => ({ error: { message: 'rate limit' } })));
    component.lifestyle.housing = 'appartement';

    component.findMatch();
    fixture.detectChanges();

    expect(component.visible.length).toBe(2);
    expect(component.matchError).toContain('rate limit');
    expect(fixture.nativeElement.querySelector('[data-testid="match-time"]').textContent).toContain('ms');
  });
});

function post(id: number, name: string, species: string, area: string) {
  return {
    id,
    petId: null,
    authorId: 4,
    authorFirstName: 'Sam',
    status: 'AVAILABLE' as const,
    title: name,
    animalName: name,
    shelterNotes: null,
    description: null,
    species,
    breed: null,
    ageLabel: '2 ans',
    gender: null,
    personality: 'calme',
    specialNeeds: null,
    areaLabel: area,
    photoUrl: null,
    photoDescription: null,
    aiDraft: null,
    createdAt: null,
    adoptedAt: null
  };
}
