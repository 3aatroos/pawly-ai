import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StoreService, Review } from '../../../core/services/store.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-product-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="reviews-section fade-up">
      <div class="reviews-header">
        <h2 class="section-title">Customer Reviews</h2>
        @if (totalElements > 0) {
          <span class="review-count">{{ totalElements }} review{{ totalElements !== 1 ? 's' : '' }}</span>
        }
      </div>

      <!-- Write review -->
      @if (!hasReviewed && !showForm) {
        <button class="btn btn-primary btn-sm" (click)="showForm = true">✍️ Write a review</button>
      }

      @if (showForm) {
        <div class="review-form card">
          <h3 class="form-title">Your Review</h3>

          <div class="star-picker">
            @for (s of [1,2,3,4,5]; track s) {
              <button
                class="star-btn"
                [class.active]="s <= newRating"
                (click)="newRating = s"
                type="button">★</button>
            }
            <span class="star-label">{{ ratingLabel(newRating) }}</span>
          </div>

          <div class="form-group">
            <label>Comment (optional)</label>
            <textarea
              class="form-control"
              rows="3"
              [(ngModel)]="newComment"
              placeholder="Share your experience…"
              maxlength="2000"></textarea>
          </div>

          @if (formError) {
            <div class="alert alert-danger">{{ formError }}</div>
          }

          <div class="form-actions">
            <button class="btn btn-primary btn-sm" [disabled]="newRating === 0 || submitting" (click)="submitReview()">
              @if (submitting) { <span class="spinner"></span> } Submit
            </button>
            <button class="btn btn-ghost btn-sm" (click)="cancelForm()">Cancel</button>
          </div>
        </div>
      }

      <!-- Reviews list -->
      @if (loading) {
        <div class="loading-overlay"><div class="spinner"></div></div>
      } @else if (reviews.length === 0) {
        <div class="empty-state">
          <div class="empty-icon">💬</div>
          <div class="empty-title">No reviews yet</div>
          <div class="empty-sub">Be the first to review this product</div>
        </div>
      } @else {
        <div class="reviews-list">
          @for (review of reviews; track review.id) {
            <div class="review-card">
              <div class="review-header">
                <div class="review-author">
                  <div class="reviewer-avatar">
                    {{ review.userFirstName?.[0] ?? '?' }}{{ review.userLastName?.[0] ?? '' }}
                  </div>
                  <div>
                    <div class="reviewer-name">{{ review.userFirstName }} {{ review.userLastName }}</div>
                    <div class="review-date">{{ review.createdAt | date:'mediumDate' }}</div>
                  </div>
                </div>
                <div class="review-stars">
                  @for (s of [1,2,3,4,5]; track s) {
                    <span [class.star-filled]="s <= review.rating">★</span>
                  }
                </div>
              </div>
              @if (review.comment) {
                <p class="review-text">{{ review.comment }}</p>
              }

              @if (isMyReview(review) || auth.isAdmin()) {
                <div class="review-actions">
                  <button class="btn btn-ghost btn-xs" (click)="deleteReview(review)">🗑️ Delete</button>
                </div>
              }
            </div>
          }
        </div>

        @if (page < totalPages - 1) {
          <button class="btn btn-ghost btn-sm load-more" [disabled]="loadingMore" (click)="loadMore()">
            @if (loadingMore) { Loading… } @else { Load more reviews }
          </button>
        }
      }
    </div>
  `,
  styles: [`
    .reviews-section { margin-top: 32px; }
    .reviews-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
    .section-title { font-size: 18px; font-weight: 700; }
    .review-count { font-size: 13px; color: var(--text-soft); }

    .review-form { margin: 16px 0 24px; }
    .form-title { font-size: 15px; font-weight: 700; margin-bottom: 14px; }

    .star-picker {
      display: flex; align-items: center; gap: 4px; margin-bottom: 14px;
    }
    .star-btn {
      font-size: 28px; background: none; border: none; cursor: pointer;
      color: var(--border); transition: color 0.1s; line-height: 1;
      &.active { color: #f59e0b; }
      &:hover { color: #f59e0b; }
    }
    .star-label { font-size: 12px; color: var(--text-soft); margin-left: 8px; }

    .form-actions { display: flex; gap: 8px; margin-top: 12px; }

    .reviews-list { display: flex; flex-direction: column; gap: 14px; margin-top: 16px; }

    .review-card {
      background: var(--neutral);
      border-radius: 12px;
      padding: 16px 18px;
    }

    .review-header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: 10px;
    }

    .review-author { display: flex; align-items: center; gap: 10px; }

    .reviewer-avatar {
      width: 36px; height: 36px; border-radius: 50%;
      background: var(--primary); color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 13px; flex-shrink: 0;
    }

    .reviewer-name { font-weight: 600; font-size: 14px; }
    .review-date   { font-size: 11px; color: var(--text-soft); }

    .review-stars {
      font-size: 16px;
      span { color: var(--border); }
      span.star-filled { color: #f59e0b; }
    }

    .review-text { font-size: 14px; line-height: 1.6; color: var(--text-soft); }
    .review-actions { margin-top: 10px; }

    .load-more { display: block; margin: 16px auto 0; }
  `]
})
export class ProductReviewComponent implements OnInit {
  @Input({ required: true }) productId!: number;

  reviews:       Review[] = [];
  loading        = true;
  loadingMore    = false;
  submitting     = false;
  showForm       = false;
  hasReviewed    = false;
  newRating      = 0;
  newComment     = '';
  formError:     string | null = null;
  page           = 0;
  totalPages     = 1;
  totalElements  = 0;
  private readonly size = 10;

  constructor(private storeService: StoreService, public auth: AuthService) {}

  ngOnInit(): void {
    this.loadReviews();
  }

  loadReviews(): void {
    this.storeService.getReviews(this.productId, 0, this.size).subscribe({
      next: res => {
        this.reviews       = res.content;
        this.totalPages    = res.totalPages;
        this.totalElements = res.totalElements;
        this.page          = 0;
        this.checkHasReviewed();
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  loadMore(): void {
    this.loadingMore = true;
    this.page++;
    this.storeService.getReviews(this.productId, this.page, this.size).subscribe({
      next: res => {
        this.reviews = [...this.reviews, ...res.content];
        this.loadingMore = false;
      },
      error: () => { this.loadingMore = false; }
    });
  }

  submitReview(): void {
    if (this.newRating === 0) return;
    this.submitting = true;
    this.formError  = null;

    this.storeService.addReview(this.productId, {
      rating:  this.newRating,
      comment: this.newComment.trim() || undefined
    }).subscribe({
      next: review => {
        this.reviews       = [review, ...this.reviews];
        this.totalElements++;
        this.hasReviewed   = true;
        this.showForm      = false;
        this.submitting    = false;
        this.resetForm();
      },
      error: err => {
        this.formError  = err.error?.message ?? 'Failed to submit review';
        this.submitting = false;
      }
    });
  }

  deleteReview(review: Review): void {
    if (!confirm('Delete this review?')) return;
    this.storeService.deleteReview(this.productId, review.id!).subscribe({
      next: () => {
        this.reviews = this.reviews.filter(r => r.id !== review.id);
        this.totalElements--;
        if (this.isMyReview(review)) this.hasReviewed = false;
      },
      error: err => alert(err.error?.message ?? 'Failed to delete review')
    });
  }

  cancelForm(): void {
    this.showForm = false;
    this.resetForm();
  }

  isMyReview(review: Review): boolean {
    return review.userId === this.auth.currentUser()?.userId;
  }

  ratingLabel(r: number): string {
    return ['', 'Poor', 'Fair', 'Good', 'Very good', 'Excellent'][r] ?? '';
  }

  private checkHasReviewed(): void {
    const myId = this.auth.currentUser()?.userId;
    this.hasReviewed = this.reviews.some(r => r.userId === myId);
  }

  private resetForm(): void {
    this.newRating  = 0;
    this.newComment = '';
    this.formError  = null;
  }
}
