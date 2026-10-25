import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notification } from '../models/notification.model';
import { User, UpdateProfileRequest, UserRole, UserStatus } from '../models/user.model';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private base = `${environment.apiUrl}/users`;

  constructor(private http: HttpClient) {}

  getMe(): Observable<User> {
    return this.http.get<User>(`${this.base}/me`);
  }

  updateMe(req: UpdateProfileRequest): Observable<User> {
    return this.http.put<User>(`${this.base}/me`, req);
  }

  getById(id: number): Observable<User> {
    return this.http.get<User>(`${this.base}/${id}`);
  }

  getNotifications(): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${this.base}/me/notifications`);
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/me/notifications/unread-count`);
  }

  markNotificationRead(id: number): Observable<Notification> {
    return this.http.patch<Notification>(`${this.base}/me/notifications/${id}/read`, {});
  }

  markAllNotificationsRead(): Observable<{ updated: number }> {
    return this.http.patch<{ updated: number }>(`${this.base}/me/notifications/mark-all-read`, {});
  }
}
