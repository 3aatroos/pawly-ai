import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderStatus, ORDER_STATUS_LABELS, ORDER_STATUS_STEPS } from '../../../core/models/order.model';

@Component({
  selector: 'app-tracking',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="tracking-widget">
      <div class="tracking-header">
        <h3 class="tracking-title">📦 Delivery Tracking</h3>
        <button class="btn btn-ghost btn-sm refresh-btn" [disabled]="refreshing" (click)="onRefresh()">
          <span [class.spin]="refreshing">🔄</span>
          @if (refreshing) { Updating… } @else { Refresh }
        </button>
      </div>

      @if (status === 'CANCELLED') {
        <div class="alert alert-danger tracking-cancelled">
          🚫 This order has been cancelled.
        </div>
      } @else {
        <div class="stepper">
          @for (step of steps; track step; let i = $index) {
            <div class="step-wrap">
              <div class="step" [class.done]="isStepDone(step)" [class.active]="status === step">
                <div class="step-circle">
                  @if (isStepDone(step) && status !== step) { ✓ }
                  @else { {{ i + 1 }} }
                </div>
                <div class="step-label">{{ label(step) }}</div>
              </div>
              @if (i < steps.length - 1) {
                <div class="connector" [class.done]="isStepDone(steps[i + 1])"></div>
              }
            </div>
          }
        </div>

        <div class="tracking-current">
          <span class="current-dot"></span>
          Current status: <strong>{{ label(status) }}</strong>
        </div>
      }
    </div>
  `,
  styles: [`
    .tracking-widget {
      background: var(--white);
      border-radius: var(--radius);
      padding: 20px 24px;
      border: 1px solid var(--border);
    }

    .tracking-header {
      display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px;
    }

    .tracking-title { font-size: 15px; font-weight: 700; }

    .refresh-btn { font-size: 12px; }
    .spin { display: inline-block; animation: spin .8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

    .stepper {
      display: flex; align-items: flex-start; overflow-x: auto; padding-bottom: 4px; gap: 0;
    }

    .step-wrap { display: flex; align-items: flex-start; }

    .step {
      display: flex; flex-direction: column; align-items: center; min-width: 72px;
    }

    .step-circle {
      width: 32px; height: 32px; border-radius: 50%;
      border: 2px solid var(--border);
      background: var(--neutral);
      display: flex; align-items: center; justify-content: center;
      font-size: 12px; font-weight: 700; color: var(--text-soft);
      transition: all .2s;
    }

    .step.done .step-circle {
      background: var(--primary); border-color: var(--primary); color: #fff;
    }

    .step.active .step-circle {
      background: var(--primary); border-color: var(--primary); color: #fff;
      box-shadow: 0 0 0 4px var(--primary-light);
    }

    .step-label {
      font-size: 10px; margin-top: 6px; text-align: center;
      color: var(--text-soft); max-width: 65px; line-height: 1.3;
    }

    .step.done .step-label, .step.active .step-label {
      color: var(--primary); font-weight: 600;
    }

    .connector {
      flex: 1; height: 2px; background: var(--border); margin-top: 15px; min-width: 20px;
      transition: background .2s;
      &.done { background: var(--primary); }
    }

    .tracking-current {
      display: flex; align-items: center; gap: 8px;
      margin-top: 16px; font-size: 13px; color: var(--text-soft);
    }

    .current-dot {
      width: 8px; height: 8px; border-radius: 50%;
      background: var(--primary);
      animation: pulse 1.5s infinite;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; transform: scale(1); }
      50%       { opacity: .5; transform: scale(.8); }
    }

    .tracking-cancelled { margin-top: 0; }
  `]
})
export class TrackingComponent {
  @Input({ required: true }) status!: OrderStatus;
  @Input() refreshing = false;
  @Output() refresh = new EventEmitter<void>();

  readonly steps = ORDER_STATUS_STEPS;

  onRefresh(): void {
    this.refresh.emit();
  }

  label(s: OrderStatus): string {
    return ORDER_STATUS_LABELS[s] ?? s;
  }

  isStepDone(step: OrderStatus): boolean {
    const current = this.steps.indexOf(this.status);
    const target  = this.steps.indexOf(step);
    return target <= current;
  }
}
