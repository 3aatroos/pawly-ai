import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TriageRequest, TriageResponse } from '../models/triage.model';

@Injectable({ providedIn: 'root' })
export class TriageService {
  private base = `${environment.apiUrl}/triage`;

  constructor(private http: HttpClient) {}

  performTriage(req: TriageRequest): Observable<TriageResponse> {
    return this.http.post<TriageResponse>(this.base, req);
  }

  seedKnowledgeBase(): Observable<string> {
    return this.http.post<string>(`${this.base}/seed`, {}, { responseType: 'text' as any });
  }

  getStatus(): Observable<string> {
    return this.http.get<string>(`${this.base}/status`, { responseType: 'text' as any });
  }
}