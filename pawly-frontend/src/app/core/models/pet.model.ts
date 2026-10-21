export type PetGender = 'MALE' | 'FEMALE' | 'UNKNOWN';

export interface Pet {
  id?: number;
  ownerId?: number;
  ownerName?: string;
  name: string;
  species: string;
  breed?: string;
  dateOfBirth?: string;
  gender: PetGender;
  photoUrl?: string;
  healthInfo?: string;
  weightKg?: number;
  color?: string;
  microchipId?: string;
  isVaccinated?: boolean;
  createdAt?: string;
  assignedVetId?: number;
  assignedVetName?: string; // "Dr. FirstName LastName"
}
