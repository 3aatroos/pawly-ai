import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pet } from '../models/pet.model';

@Injectable({ providedIn: 'root' })
export class PetService {
  private base = `${environment.apiUrl}/pets`;

  constructor(private http: HttpClient) {}

  getMyPets(): Observable<Pet[]> {
    return this.http.get<Pet[]>(this.base);
  }

  getById(id: number): Observable<Pet> {
    return this.http.get<Pet>(`${this.base}/${id}`);
  }

  create(pet: Pet): Observable<Pet> {
    return this.http.post<Pet>(this.base, pet);
  }

  update(id: number, pet: Partial<Pet>): Observable<Pet> {
    return this.http.put<Pet>(`${this.base}/${id}`, pet);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  assignVet(petId: number, vetProfileId: number): Observable<Pet> {
    return this.http.patch<Pet>(`${this.base}/${petId}/assign-vet`, { vetProfileId });
  }

  removeVet(petId: number): Observable<Pet> {
    return this.http.delete<Pet>(`${this.base}/${petId}/assign-vet`);
  }
}
