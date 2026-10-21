import { Component, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../core/auth/auth.service';
import { UserService } from '../core/services/user.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  template: `
    <div class='app-shell'>
      <aside class='sidebar'>
        <div class='sidebar-logo'>
          <div class='logo-icon'>🐾</div>
          <span class='logo-text'>Pawly</span>
        </div>
        <span class='nav-section-label'>Main</span>
        <a class='nav-item' routerLink='/dashboard' routerLinkActive='active' [routerLinkActiveOptions]='{exact:true}'>
          <span class='nav-icon'>🏠</span> Dashboard
        </a>
        <a class='nav-item' routerLink='/profile' routerLinkActive='active'>
          <span class='nav-icon'>👤</span> My Profile
        </a>
        <a class='nav-item' routerLink='/pets' routerLinkActive='active'>
          <span class='nav-icon'>🐕</span> My Pets
        </a>
        @if (auth.isVet()) {
          <a class='nav-item' routerLink='/vet-profile' routerLinkActive='active'>
            <span class='nav-icon'>🩺</span> My Vet Profile
          </a>
        }
        <a class='nav-item' routerLink='/notifications' routerLinkActive='active'>
          <span class='nav-icon'>🔔</span> Notifications
          @if (unreadCount > 0) { <span class='nav-badge'>{{ unreadCount }}</span> }
        </a>
        <a class='nav-item' routerLink='/vets' routerLinkActive='active'>
          <span class='nav-icon'>🩺</span> Veterinarians
        </a>
        @if (auth.isAdmin()) {
          <span class='nav-section-label' style='margin-top:12px'>Admin</span>
          <a class='nav-item' routerLink='/admin/users' routerLinkActive='active'>
            <span class='nav-icon'>👥</span> User Management
          </a>
          <a class='nav-item' routerLink='/admin/vets' routerLinkActive='active'>
            <span class='nav-icon'>🛡️</span> Vet Verification
          </a>
        }
        <a class='sidebar-emergency' routerLink='/ai-emergency' routerLinkActive='active'>
          <div class='em-title'>🚨 AI Emergency</div>
          <div class='em-sub'>Tap for AI triage</div>
        </a>
        <div class='sidebar-user'>
          <div class='sidebar-user-avatar'>
            {{ auth.currentUser()?.firstName?.[0] }}{{ auth.currentUser()?.lastName?.[0] }}
          </div>
          <div class='sidebar-user-info'>
            <div class='sidebar-user-name'>{{ auth.currentUser()?.firstName }} {{ auth.currentUser()?.lastName }}</div>
            <div class='sidebar-user-role'>{{ roleLabel }}</div>
          </div>
          <button class='sidebar-logout' (click)='auth.logout()' title='Sign out'>⏻</button>
        </div>
      </aside>
      <main class='main-content'><router-outlet></router-outlet></main>
    </div>
  `,
  styles: [`
    .app-shell { display: flex; height: 100vh; overflow: hidden; }
    .sidebar { width: 240px; flex-shrink: 0; background: #fff; border-right: 1px solid var(--border); display: flex; flex-direction: column; padding: 24px 0 0; overflow-y: auto; }
    .sidebar-logo { display: flex; align-items: center; gap: 10px; padding: 0 20px 24px; border-bottom: 1px solid var(--border); margin-bottom: 16px; }
    .logo-icon { width: 40px; height: 40px; background: var(--primary); border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 20px; }
    .logo-text { font-weight: 700; font-size: 20px; color: var(--primary); }
    .nav-section-label { font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.1em; color: var(--text-soft); padding: 8px 20px 4px; }
    .nav-item { display: flex; align-items: center; gap: 10px; padding: 10px 20px; font-size: 14px; font-weight: 500; color: var(--text-soft); cursor: pointer; transition: background 0.15s, color 0.15s; text-decoration: none; border-left: 3px solid transparent; &:hover, &.active { background: var(--primary-light); color: var(--primary); border-left-color: var(--primary); } &.active { font-weight: 600; } }
    .nav-icon { font-size: 18px; width: 22px; text-align: center; }
    .nav-badge { margin-left: auto; background: var(--danger); color: #fff; font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 99px; }
    .sidebar-emergency { margin: 16px 12px; text-decoration: none; display: block; background: linear-gradient(135deg, var(--danger) 0%, #FF6584 100%); border-radius: 14px; padding: 14px; cursor: pointer; transition: opacity 0.15s; &:hover, &.active { opacity: 0.85; } .em-title { color: #fff; font-weight: 700; font-size: 13px; } .em-sub { color: rgba(255,255,255,.8); font-size: 11px; margin-top: 2px; } }
    .sidebar-user { margin-top: auto; display: flex; align-items: center; gap: 10px; padding: 14px 20px; border-top: 1px solid var(--border); }
    .sidebar-user-avatar { width: 36px; height: 36px; border-radius: 50%; background: var(--primary); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 13px; flex-shrink: 0; }
    .sidebar-user-name { font-size: 13px; font-weight: 600; }
    .sidebar-user-role { font-size: 11px; color: var(--text-soft); }
    .sidebar-logout { margin-left: auto; background: none; border: none; cursor: pointer; font-size: 16px; color: var(--text-soft); padding: 4px; border-radius: 6px; &:hover { background: var(--neutral); } }
    .main-content { flex: 1; overflow-y: auto; padding: 28px 32px; }
  `]
})
export class ShellComponent implements OnInit {
  unreadCount = 0;
  constructor(public auth: AuthService, private userSvc: UserService) {}
  ngOnInit(): void {
    this.userSvc.getUnreadCount().subscribe(r => this.unreadCount = r.count);
  }
  get roleLabel(): string {
    const m: Record<string, string> = {
      ADMINISTRATOR: 'Administrator', VETERINARIAN: 'Veterinarian', PET_OWNER: 'Pet Owner'
    };
    return m[this.auth.currentUser()?.role ?? ''] ?? '';
  }
}