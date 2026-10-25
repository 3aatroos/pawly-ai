import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserService } from '../../core/services/user.service';
import { Notification } from '../../core/models/notification.model';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fade-up">
      <div class="page-header">
        <div>
          <div class="page-title">Notifications</div>
          <div class="page-sub">{{ unreadCount }} unread notification{{ unreadCount !== 1 ? 's' : '' }}</div>
        </div>
        <button class="btn btn-ghost btn-sm" (click)="markAllRead()">✓ Mark all read</button>
      </div>

      <div class="card">
        @if (loading) {
          <div class="loading-overlay"><div class="spinner"></div></div>
        }

        @if (!loading && notifications.length === 0) {
          <div class="empty-state">
            <div class="empty-icon">🔔</div>
            <div class="empty-title">No notifications</div>
            <div class="empty-sub">You're all caught up!</div>
          </div>
        }

        @for (n of notifications; track n.id) {
          <div class="notif-item"
            [class.unread]="!n.isRead && n.type !== 'EMERGENCY'"
            [class.unread-danger]="!n.isRead && n.type === 'EMERGENCY'"
            (click)="markRead(n)">
            <div class="notif-icon">{{ icon(n.type) }}</div>
            <div style="flex:1">
              <div class="notif-title">{{ n.title }}</div>
              <div class="notif-desc">{{ n.message }}</div>
              <div class="notif-time">
                {{ n.createdAt | date:'medium' }}
                @if (!n.isRead) { · <span class="badge badge-pending" style="font-size:10px">New</span> }
              </div>
            </div>
          </div>
        }
      </div>
    </div>
  `
})
export class NotificationsComponent implements OnInit {
  notifications: Notification[] = [];
  loading = true;

  get unreadCount(): number {
    return this.notifications.filter(n => !n.isRead).length;
  }

  constructor(private userSvc: UserService) {}

  ngOnInit(): void {
    this.userSvc.getNotifications().subscribe({
      next: n  => { this.notifications = n; this.loading = false; },
      error: () => this.loading = false
    });
  }

  markRead(n: Notification): void {
    if (n.isRead) return;
    this.userSvc.markNotificationRead(n.id).subscribe(updated => {
      const idx = this.notifications.findIndex(x => x.id === n.id);
      if (idx !== -1) this.notifications[idx] = updated;
    });
  }

  markAllRead(): void {
    this.userSvc.markAllNotificationsRead().subscribe(() => {
      this.notifications = this.notifications.map(n => ({ ...n, isRead: true }));
    });
  }

  icon(type: string): string {
    const m: Record<string, string> = {
      EMERGENCY: '🚨', WARNING: '⚠️', INFO: '💬', SYSTEM: '🔧', REMINDER: '💉'
    };
    return m[type] ?? '🔔';
  }
}
