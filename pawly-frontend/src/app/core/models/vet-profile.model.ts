export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export interface VetProfile {
  id?: number;
  userId?: number;
  userEmail?: string;
  userFirstName?: string;
  userLastName?: string;
  userPhone?: string;
  userPhotoUrl?: string;
  specialization: string;
  experienceYears?: number;
  clinicName?: string;
  clinicAddress?: string;
  city?: string;
  workingHours?: string;
  services?: string;
  licenseNumber?: string;
  bio?: string;
  isAvailable?: boolean;
  verificationStatus?: VerificationStatus;
  verifiedAt?: string;
  rejectionReason?: string;
  createdAt?: string;
}
