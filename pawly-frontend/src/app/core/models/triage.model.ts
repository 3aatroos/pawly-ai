export interface TriageRequest {
  symptoms: string;
  species?: string;
  petName?: string;
  petId?: number;
}

export interface TriageResponse {
  urgencyLevel: 'CRITICAL' | 'URGENT' | 'MODERATE' | 'MILD' | 'UNKNOWN';
  assessment: string;
  knowledgeSources: string[];
  timestamp: string;
  aiGenerated: boolean;
}

export type TriageStep = 'pet-select' | 'symptoms' | 'analyzing' | 'result';