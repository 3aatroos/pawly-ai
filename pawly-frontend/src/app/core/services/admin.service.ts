import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User, UserRole, UserStatus } from '../models/user.model';
import { VetProfile, VerificationStatus } from '../models/vet-profile.model';
import { PageResponse } from './user.service';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private base = `${environment.apiUrl}/admin`;

  constructor(private http: HttpClient) {}

  getStats(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.base}/stats`);
  }

  getUsers(
    role?: UserRole,
    status?: UserStatus,
    search?: string,
    page = 0,
    size = 20
  ): Observable<PageResponse<User>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (role)   params = params.set('role', role);
    if (status) params = params.set('status', status);
    if (search) params = params.set('search', search);
    return this.http.get<PageResponse<User>>(`${this.base}/users`, { params });
  }

  getUserById(id: number): Observable<User> {
    return this.http.get<User>(`${this.base}/users/${id}`);
  }

  activateUser(id: number): Observable<User> {
    return this.http.patch<User>(`${this.base}/users/${id}/activate`, {});
  }

  deactivateUser(id: number): Observable<User> {
    return this.http.patch<User>(`${this.base}/users/${id}/deactivate`, {});
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }

  getVets(
    status?: VerificationStatus,
    search?: string,
    page = 0,
    size = 20
  ): Observable<PageResponse<VetProfile>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (search) params = params.set('search', search);
    return this.http.get<PageResponse<VetProfile>>(`${this.base}/vets`, { params });
  }

  verifyVet(id: number): Observable<VetProfile> {
    return this.http.patch<VetProfile>(`${this.base}/vets/${id}/verify`, {});
  }

  rejectVet(id: number, reason: string): Observable<VetProfile> {
    return this.http.patch<VetProfile>(`${this.base}/vets/${id}/reject`, { reason });
  }
}
