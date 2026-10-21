export type NotificationType = 'INFO' | 'WARNING' | 'EMERGENCY' | 'SYSTEM' | 'REMINDER';

export interface Notification {
  id: number;
  userId: number;
  title: string;
  message: string;
  type: NotificationType;
  isRead: boolean;
  createdAt: string;
}
