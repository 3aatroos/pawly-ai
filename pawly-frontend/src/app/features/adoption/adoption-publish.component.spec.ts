import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AdoptionService } from '../../core/services/adoption.service';
import { AdoptionPublishComponent } from './adoption-publish.component';

describe('AdoptionPublishComponent', () => {
  let fixture: ComponentFixture<AdoptionPublishComponent>;
  let component: AdoptionPublishComponent;
  let adoption: jasmine.SpyObj<AdoptionService>;

  beforeEach(async () => {
    adoption = jasmine.createSpyObj('AdoptionService', ['previewDraft', 'create']);
    await TestBed.configureTestingModule({
      imports: [AdoptionPublishComponent],
      providers: [
        provideRouter([]),
        { provide: AdoptionService, useValue: adoption }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AdoptionPublishComponent);
    component = fixture.componentInstance;
    component.form.shelterNotes = 'Chatte rousse recueillie à Tunis.';
    component.form.animalName = 'Saisi à la main';
    fixture.detectChanges();
  });

  it('fills the form from the AI preview and does not publish', () => {
    adoption.previewDraft.and.returnValue(of({
      name: 'Louna',
      species: 'chat',
      breed: null,
      age: 'environ 2 ans',
      gender: 'femelle',
      personalityTags: ['calme'],
      description: 'Louna est une chatte du refuge à Tunis.',
      specialNeeds: null,
      uncertainties: ['statut vaccinal non indiqué']
    }));

    fixture.nativeElement.querySelector('[data-testid="generate"]').click();

    expect(component.form.animalName).toBe('Louna');
    expect(component.form.description).toContain('refuge');
    expect(component.uncertainties).toContain('statut vaccinal non indiqué');
    expect(component.notice).toContain('Rien n\'est publié');
    expect(adoption.create).not.toHaveBeenCalled();
  });

  it('keeps the manual form when the AI call fails', () => {
    adoption.previewDraft.and.returnValue(throwError(() => ({ error: { message: 'AI timed out' } })));

    component.generate();

    expect(component.form.animalName).toBe('Saisi à la main');
    expect(component.generateError).toContain('AI timed out');
    expect(adoption.create).not.toHaveBeenCalled();
  });
});
