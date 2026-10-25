import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TrackingComponent } from './tracking.component';

describe('TrackingComponent', () => {
  let fixture: ComponentFixture<TrackingComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TrackingComponent] }).compileComponents();
    fixture = TestBed.createComponent(TrackingComponent);
  });

  it('marks the steps up to the current local status', () => {
    fixture.componentRef.setInput('status', 'SHIPPED');
    fixture.detectChanges();
    expect(fixture.componentInstance.isStepDone('PREPARING')).toBeTrue();
    expect(fixture.componentInstance.isStepDone('IN_TRANSIT')).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Shipped');
    expect(fixture.nativeElement.textContent).not.toContain('cancelled');
  });

  it('shows a cancelled delivery instead of the step list', () => {
    fixture.componentRef.setInput('status', 'CANCELLED');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('This order has been cancelled.');
  });

  it('asks the parent to refresh', () => {
    fixture.componentRef.setInput('status', 'CONFIRMED');
    fixture.detectChanges();
    const refreshed = jasmine.createSpy('refresh');
    fixture.componentInstance.refresh.subscribe(refreshed);
    fixture.nativeElement.querySelector('button').click();
    expect(refreshed).toHaveBeenCalled();
  });
});
