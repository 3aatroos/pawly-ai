import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { VetProfile, VerificationStatus } from '../models/vet-profile.model';
import { PageResponse } from './user.service';
import { Pet } from '../models/pet.model';

@Injectable({ providedIn: 'root' })
export class VetService {
  private base = `${environment.apiUrl}/vets`;

  constructor(private http: HttpClient) {}

  getAll(status?: VerificationStatus, search?: string, isAvailable?: boolean, page = 0, size = 20): Observable<PageResponse<VetProfile>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (search) params = params.set('search', search);
    if (isAvailable !== undefined) params = params.set('isAvailable', isAvailable);
    return this.http.get<PageResponse<VetProfile>>(this.base, { params });
  }

  getById(id: number): Observable<VetProfile> {
    return this.http.get<VetProfile>(`${this.base}/${id}`);
  }

  getMyProfile(): Observable<VetProfile> {
    return this.http.get<VetProfile>(`${this.base}/me`);
  }

  updateMyProfile(dto: Partial<VetProfile>): Observable<VetProfile> {
    return this.http.put<VetProfile>(`${this.base}/me`, dto);
  }

  getMyAssignedPets(): Observable<Pet[]> {
    return this.http.get<Pet[]>(`${this.base}/me/pets`);
  }
}
