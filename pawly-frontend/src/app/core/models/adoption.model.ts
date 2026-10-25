export type AdoptionStatus = 'AVAILABLE' | 'PENDING' | 'ADOPTED';
export type ApplicationStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'ACCEPTED' | 'REJECTED';

export interface AdoptionPost {
  id: number;
  petId: number | null;
  authorId: number;
  authorFirstName: string | null;
  status: AdoptionStatus;
  title: string;
  animalName: string | null;
  shelterNotes: string | null;
  description: string | null;
  species: string | null;
  breed: string | null;
  ageLabel: string | null;
  gender: string | null;
  personality: string | null;
  specialNeeds: string | null;
  areaLabel: string | null;
  photoUrl: string | null;
  photoDescription: string | null;
  aiDraft: string | null;
  createdAt: string | null;
  adoptedAt: string | null;
}

export interface AdoptionDraft {
  name: string | null;
  species: string | null;
  breed: string | null;
  age: string | null;
  gender: string | null;
  personalityTags: string[];
  description: string;
  specialNeeds: string | null;
  uncertainties: string[];
}

export interface AdoptionMatchRequest {
  housing?: string | null;
  hasGarden?: boolean | null;
  hoursAwayPerDay?: number | null;
  otherPets?: boolean | null;
  children?: boolean | null;
  experience?: string | null;
  sector?: string | null;
}

export interface AdoptionMatchItem {
  postId: number;
  score: number;
  reason: string;
  warning: string | null;
}

export interface AdoptionMatchResult {
  matches: AdoptionMatchItem[];
}

export interface AdoptionApplication {
  id: number;
  postId: number;
  applicantId: number;
  applicantFirstName: string | null;
  status: ApplicationStatus;
  housing: string | null;
  hasGarden: boolean | null;
  hoursAwayPerDay: number | null;
  otherPets: boolean | null;
  childrenAtHome: boolean | null;
  experience: string | null;
  motivation: string | null;
  reviewerSummary: string | null;
  pointsToVerify: string[];
  suggestedAttention: string | null;
  decisionNote: string | null;
  createdAt: string | null;
}

export interface AdoptionReview {
  summary: string;
  pointsToVerify: string[];
  suggestedAttention: 'low' | 'medium' | 'high' | string;
}

export interface PublishAdoptionRequest {
  title: string;
  animalName?: string | null;
  shelterNotes: string;
  description?: string | null;
  species?: string | null;
  breed?: string | null;
  ageLabel?: string | null;
  gender?: string | null;
  personality?: string | null;
  specialNeeds?: string | null;
  areaLabel?: string | null;
  photoUrl?: string | null;
  photoDescription?: string | null;
  petId?: number | null;
}

export interface ApplyAdoptionRequest {
  housing: string;
  hasGarden?: boolean | null;
  hoursAwayPerDay?: number | null;
  otherPets?: boolean | null;
  childrenAtHome?: boolean | null;
  experience?: string | null;
  motivation?: string | null;
}

export function speciesEmoji(species: string | null | undefined): string {
  const value = (species || '').toLowerCase();
  if (value.includes('chat') || value.includes('cat')) return '🐈';
  if (value.includes('chien') || value.includes('dog')) return '🐕';
  if (value.includes('lapin') || value.includes('rabbit')) return '🐇';
  if (value.includes('oiseau') || value.includes('bird') || value.includes('perroquet')) return '🦜';
  return '🐾';
}

export function speciesMatches(species: string | null | undefined, filter: string): boolean {
  if (!filter) return true;
  const value = (species || '').toLowerCase();
  const groups: Record<string, string[]> = {
    chat: ['chat', 'cat'],
    chien: ['chien', 'dog'],
    lapin: ['lapin', 'rabbit'],
    oiseau: ['oiseau', 'bird', 'perroquet']
  };
  return (groups[filter] || [filter]).some(token => value.includes(token));
}

export function apiMessage(err: unknown, fallback: string): string {
  const body = (err as { error?: { message?: string } })?.error;
  return body?.message || fallback;
}
