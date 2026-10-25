import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AdoptionApplication,
  AdoptionDraft,
  AdoptionMatchRequest,
  AdoptionMatchResult,
  AdoptionPost,
  AdoptionReview,
  AdoptionStatus,
  ApplyAdoptionRequest,
  PublishAdoptionRequest
} from '../models/adoption.model';

@Injectable({ providedIn: 'root' })
export class AdoptionService {
  private base = `${environment.apiUrl}/adoption`;

  constructor(private http: HttpClient) {}

  list(status: AdoptionStatus = 'AVAILABLE'): Observable<AdoptionPost[]> {
    return this.http.get<AdoptionPost[]>(`${this.base}/posts`, { params: { status } });
  }

  get(id: number): Observable<AdoptionPost> {
    return this.http.get<AdoptionPost>(`${this.base}/posts/${id}`);
  }

  create(body: PublishAdoptionRequest): Observable<AdoptionPost> {
    return this.http.post<AdoptionPost>(`${this.base}/posts`, body);
  }

  previewDraft(notes: string, photoDescription?: string | null): Observable<AdoptionDraft> {
    return this.http.post<AdoptionDraft>(`${this.base}/draft-preview`, {
      notes,
      photoDescription: photoDescription || null
    });
  }

  match(body: AdoptionMatchRequest): Observable<AdoptionMatchResult> {
    return this.http.post<AdoptionMatchResult>(`${this.base}/match`, body);
  }

  apply(postId: number, body: ApplyAdoptionRequest): Observable<AdoptionApplication> {
    return this.http.post<AdoptionApplication>(`${this.base}/posts/${postId}/applications`, body);
  }

  applications(postId: number): Observable<AdoptionApplication[]> {
    return this.http.get<AdoptionApplication[]>(`${this.base}/posts/${postId}/applications`);
  }

  previewReview(applicationId: number): Observable<AdoptionReview> {
    return this.http.post<AdoptionReview>(`${this.base}/applications/${applicationId}/review-preview`, null);
  }

  decide(applicationId: number, status: 'ACCEPTED' | 'REJECTED', decisionNote?: string): Observable<AdoptionApplication> {
    return this.http.patch<AdoptionApplication>(`${this.base}/applications/${applicationId}/decision`, {
      status,
      decisionNote: decisionNote || null
    });
  }

  favorite(postId: number): Observable<AdoptionPost> {
    return this.http.post<AdoptionPost>(`${this.base}/posts/${postId}/favorite`, {});
  }
}
