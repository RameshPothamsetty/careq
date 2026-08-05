/**
 * Shared API types + the auth functions used by AuthContext.
 *
 * All *server data* endpoints (profile, departments, doctors, queue) moved to
 * RTK Query slices in ./rtk/ — see docs/03_ARCHITECTURE.md § Frontend State
 * Management. This file keeps the type contract (re-exported to slices and
 * screens) plus login/signup, which remain session-state calls owned by
 * AuthContext.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

interface SignupPayload {
  fullName: string;
  email: string;
  password: string;
  role: string;
}

interface LoginPayload {
  email: string;
  password: string;
}

interface AuthResponse {
  token: string;
  tokenType: string;
  userId: string;
  email: string;
  fullName: string;
  role: string;
}

interface UserProfileResponse {
  id: number;
  userId: string;
  fullName: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  profilePictureUrl: string | null;
  role: string;
}

interface UpdateProfilePayload {
  phone?: string;
  address?: string;
  dateOfBirth?: string;
  gender?: string;
  profilePictureUrl?: string;
}

interface DepartmentResponse {
  id: number;
  name: string;
  description: string | null;
  isActive: boolean;
}

interface DepartmentRequest {
  name: string;
  description?: string;
}

interface DoctorCatalogResponse {
  id: number;
  name: string;
  userId: string;
  departmentId: number;
  departmentName: string;
  specialization: string;
  qualification: string;
  experienceYears: number;
  consultationFee: number;
  avgConsultationTimeMinutes: number;
  isAvailable: boolean;
}

interface DoctorCatalogRequest {
  name: string;
  userId: string;
  departmentId: number;
  specialization: string;
  qualification: string;
  experienceYears: number;
  consultationFee: number;
  avgConsultationTimeMinutes: number;
}

interface AvailabilityRequest {
  isAvailable: boolean;
}

// ---- Queue types (Day 5) ----

type TriageLevel = 'EMERGENCY' | 'HIGH' | 'NORMAL' | 'FOLLOW_UP';

type QueueStatus = 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

interface JoinQueuePayload {
  doctorCatalogEntryId: number;
  symptomText: string;
  patientName?: string;
}

interface QueueEntryResponse {
  id: number;
  patientId: string;
  patientName: string | null;
  doctorCatalogEntryId: number;
  doctorName: string;
  departmentName: string;
  specialization: string;
  symptomText: string;
  aiSuggestedTriage: TriageLevel;
  doctorOverrideTriage: TriageLevel | null;
  effectiveTriage: TriageLevel;
  status: QueueStatus;
  position: number | null;
  predictedWaitMinutes: number | null;
  joinedAt: string;
  calledAt: string | null;
  completedAt: string | null;
}

interface QueueStatusResponse {
  active: boolean;
  entry: QueueEntryResponse | null;
}

interface OverrideTriagePayload {
  triageLevel: TriageLevel;
}

interface DoctorQueueStats {
  doctorCatalogEntryId: number;
  doctorName: string;
  doctorUserId: string;
  departmentName: string;
  specialization: string;
  avgConsultationTimeMinutes: number;
  isAvailable: boolean;
  waitingCount: number;
  inProgressCount: number;
  delayedCount: number;
  longestWaitMinutes: number | null;
}

interface LiveQueueOverview {
  totalWaiting: number;
  totalInProgress: number;
  doctorsOnline: number;
  doctorsOffline: number;
  delayedConsultations: number;
  averageWaitMinutes: number;
  doctors: DoctorQueueStats[];
}

// ---- Analytics types (Day 7b) ----

interface DailyPatientCount {
  date: string; // yyyy-MM-dd
  count: number;
}

interface DailyAvgWait {
  date: string; // yyyy-MM-dd
  avgWaitMinutes: number | null;
}

interface DepartmentDistribution {
  departmentName: string;
  patientCount: number;
}

interface AnalyticsSummary {
  patientsPerDay: DailyPatientCount[];
  avgWaitTimeTrend: DailyAvgWait[];
  departmentDistribution: DepartmentDistribution[];
}

/** Per-doctor analytics (dashboard upgrade) — today's scalars + 7-day trends. */
interface DoctorAnalyticsSummary {
  patientsCompletedToday: number;
  avgWaitTodayMinutes: number | null;
  avgConsultTimeTodayMinutes: number | null;
  patientsPerDay: DailyPatientCount[];
  avgWaitTimeTrend: DailyAvgWait[];
}

/** Server-side page wrapper (Spring Data Page JSON). */
interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = localStorage.getItem('careq_token');

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  const data = await response.json();

  if (!response.ok) {
    // Day 9: backend error shape is now { message, error, validationErrors } (was { details }).
    const errorMessage =
      data.message || data.error || 'An unexpected error occurred';
    const error = new Error(errorMessage) as Error & { status: number; validationErrors: { field: string; message: string }[] };
    error.status = response.status;
    error.validationErrors = data.validationErrors || [];
    throw error;
  }

  return data as T;
}

/** Auth-only API — session state owned by AuthContext. */
export const api = {
  signup: (payload: SignupPayload) =>
    request<AuthResponse>('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  login: (payload: LoginPayload) =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
};

export type {
  AuthResponse,
  SignupPayload,
  LoginPayload,
  UserProfileResponse,
  UpdateProfilePayload,
  DepartmentResponse,
  DepartmentRequest,
  DoctorCatalogResponse,
  DoctorCatalogRequest,
  AvailabilityRequest,
  TriageLevel,
  QueueStatus,
  JoinQueuePayload,
  QueueEntryResponse,
  QueueStatusResponse,
  OverrideTriagePayload,
  DoctorQueueStats,
  LiveQueueOverview,
  DailyPatientCount,
  DailyAvgWait,
  DepartmentDistribution,
  AnalyticsSummary,
  DoctorAnalyticsSummary,
  PaginatedResponse,
};
