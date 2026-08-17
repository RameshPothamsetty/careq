/**
 * Shared API types + the auth functions used by AuthContext.
 *
 * All *server data* endpoints (profile, departments, doctors, queue) moved to
 * RTK Query slices in ./rtk/ — see docs/03_ARCHITECTURE.md § Frontend State
 * Management. This file keeps the type contract (re-exported to slices and
 * screens) plus login/signup, which remain session-state calls owned by
 * AuthContext.
 */
// An EMPTY VITE_API_BASE_URL means relative /api calls — Vite's dev
// proxy (localhost:3030) handles them in dev, and the Docker Nginx container
// reverse-proxies /api to the api-gateway in production. No gateway URL is
// baked into the bundle. (??, not ||, so an empty string stays "relative".)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

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
  // Email verification. token is null when verification is required;
  // message / verificationRequired describe the signup outcome.
  message?: string;
  verificationRequired?: boolean;
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

// ---- Ranked search (RAG-style retrieval) ----

/** One result from GET /api/doctors/search — catalog fields + relevance. */
interface DoctorSearchResult {
  id: number;
  name: string;
  departmentName: string;
  specialization: string;
  qualification: string;
  experienceYears: number;
  consultationFee: number;
  avgConsultationTimeMinutes: number;
  isAvailable: boolean;
  relevanceScore: number;
}

// ---- Queue types ----

type TriageLevel = 'EMERGENCY' | 'HIGH' | 'NORMAL' | 'FOLLOW_UP';

type QueueStatus = 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

interface JoinQueuePayload {
  doctorCatalogEntryId: number;
  symptomText: string;
  patientName?: string;
}

/** AI doctor-recommendation (Phase 1): symptoms → top ranked available doctors. */
interface DoctorSuggestionPayload {
  symptomText: string;
}

interface DoctorSuggestion {
  doctorCatalogEntryId: number;
  name: string;
  departmentName: string;
  specialization: string;
  qualification: string;
  experienceYears: number;
  consultationFee: number;
  avgConsultationTimeMinutes: number;
  isAvailable: boolean;
  position: number;
  predictedWaitMinutes: number;
  matchReason: string;
}

interface DoctorSuggestionResponse {
  triageLevel: TriageLevel;
  suggestedDepartment: string | null;
  emergency: boolean;
  urgencyNote: string | null;
  suggestions: DoctorSuggestion[];
}

// ---- Billing (sandbox) ----

/** A bill for a completed consultation (queue-service). */
interface BillResponse {
  id: number;
  queueEntryId: number;
  doctorName: string;
  departmentName: string;
  amount: number;
  status: 'PENDING' | 'PAID';
  paymentMethod: string | null;
  createdAt: string;
  paidAt: string | null;
}

interface PayBillPayload {
  paymentMethod: string;
}

// ---- AI chat assistant ----

interface ChatPayload {
  message: string;
}

/**
 * POST /api/queue/chat response — the assistant's reply plus optional
 * ranked doctor suggestions (FIND_DOCTOR intents) for the UI to render
 * as tappable chips.
 */
interface ChatResponse {
  intent: string;
  reply: string;
  suggestions: DoctorSuggestion[];
  suggestedDepartment: string | null;
  emergency: boolean;
}

// ---- Auto-assign (Phase 2) ----

/** Why an auto-assign call ended the way it did. */
type AutoAssignReason = 'ASSIGNED' | 'AMBIGUOUS_SYMPTOMS' | 'NO_AVAILABLE_DOCTORS';

interface AutoAssignPayload {
  symptomText: string;
  patientName?: string;
}

/**
 * Response for POST /api/queue/auto-assign.
 * assigned=true → entry + assignedDoctor (the AI joined automatically);
 * assigned=false → suggestions for the patient to confirm (ambiguous / none).
 */
interface AutoAssignResponse {
  assigned: boolean;
  reason: AutoAssignReason | null;
  message: string | null;
  triageLevel: TriageLevel;
  suggestedDepartment: string | null;
  emergency: boolean;
  urgencyNote: string | null;
  assignedDoctor: DoctorSuggestion | null;
  entry: QueueEntryResponse | null;
  suggestions: DoctorSuggestion[];
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

// ---- Analytics types ----

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

// ---- Notification types (persisted notifications) ----

/** A persisted in-app notification (notification-service). */
interface NotificationItem {
  id: number;
  /** queue.joined | queue.triaged | queue.called | queue.completed */
  type: string;
  message: string;
  read: boolean;
  /** ISO local date-time, e.g. 2026-08-10T09:00:00 */
  createdAt: string;
}

/**
 * GET /api/notifications/me response — mirrors the Spring Page JSON shape
 * (so it fits PaginatedResponse<T>) plus a total unreadCount for the badge
 * (the badge must reflect ALL unread rows, not just the loaded page).
 */
interface NotificationPage extends PaginatedResponse<NotificationItem> {
  unreadCount: number;
}

// ---- Web Push + delivery preferences ----

/** Per-user delivery preferences (GET/PUT /api/notifications/preferences). */
interface NotificationPreferences {
  /** User's opt-out switch; delivery ALSO requires the browser permission. */
  webPushEnabled: boolean;
}

/**
 * Browser PushSubscription JSON as sent to POST /api/notifications/push/subscriptions
 * (the Web Push spec shape: endpoint + p256dh/auth keys).
 */
interface PushSubscriptionPayload {
  endpoint: string;
  keys: {
    p256dh: string;
    auth: string;
  };
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

/**
 * Cold-start tolerance (Azure): every container app scales to zero, so
 * the first request after idle can return 502/503/504 with an EMPTY body while
 * the app wakes up (measured 10-60s, docs/10_DEPLOYMENT.md §4.2), and a CD
 * rollout briefly drops connections entirely (fetch rejects). Retry for that
 * whole window — mirroring scripts/azure-smoke.mjs — instead of
 * surfacing a cryptic parse error. Attempts wait 8s each, so the window is
 * ~56s of retrying plus the request time itself.
 */
const COLD_START_STATUSES = [502, 503, 504];
const COLD_START_MAX_ATTEMPTS = 8;
const COLD_START_RETRY_DELAY_MS = 8000;

/**
 * True if the failure is a transient scale-to-zero / rollout condition we
 * should retry: a 502/503/504 status, OR a fetch that rejected without a
 * response (connection dropped mid-rollout). Anything else (4xx, real 5xx
 * with a body, auth failures) is returned as-is.
 */
function isTransientFailure(response: Response | null): boolean {
  return response === null || COLD_START_STATUSES.includes(response.status);
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

  let response: Response | null = null;
  for (let attempt = 1; ; attempt++) {
    try {
      response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
      });
    } catch {
      // fetch rejected — connection dropped while a scale-to-zero app (or a
      // CD rollout) was waking. Treat as transient and retry.
      response = null;
    }

    // Retry cold-start 502/503/504s and dropped connections; give up on
    // anything else immediately.
    if (!isTransientFailure(response) || attempt >= COLD_START_MAX_ATTEMPTS) {
      break;
    }
    await new Promise((resolve) => setTimeout(resolve, COLD_START_RETRY_DELAY_MS));
  }

  // Parse defensively: 502/503 cold-start responses (and any gateway error
  // page) can have an EMPTY body — response.json() would throw
  // "Unexpected end of JSON input". Fall back to a readable message instead.
  const text = response ? await response.text() : '';
  let data: Record<string, unknown> = {};
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = {};
    }
  }

  if (!response || !response.ok) {
    // Backend error shape: { message, error, validationErrors }.
    // An optional machine-readable `code` (EMAIL_NOT_VERIFIED, RATE_LIMITED,
    // INVALID_TOKEN) lets pages branch on the failure instead of parsing text.
    const errorMessage =
      (data.message as string) ||
      (data.error as string) ||
      (text
        ? 'An unexpected error occurred'
        : 'Service is warming up — please try again in a moment.');
    const error = new Error(errorMessage) as Error & { status: number; code?: string; validationErrors: { field: string; message: string }[] };
    error.status = response?.status ?? 0;
    error.code = data.code as string | undefined;
    error.validationErrors = (data.validationErrors as { field: string; message: string }[]) || [];
    throw error;
  }

  return data as unknown as T;
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

  // Email verification + password reset.
  verifyEmail: (token: string) =>
    request<AuthResponse>(`/api/auth/verify?token=${encodeURIComponent(token)}`, {
      method: 'GET',
    }),

  resendVerification: (email: string) =>
    request<AuthResponse>('/api/auth/resend-verification', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),

  forgotPassword: (email: string) =>
    request<AuthResponse>('/api/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),

  resetPassword: (token: string, newPassword: string) =>
    request<AuthResponse>('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token, newPassword }),
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
  DoctorSearchResult,
  AvailabilityRequest,
  TriageLevel,
  QueueStatus,
  JoinQueuePayload,
  DoctorSuggestionPayload,
  DoctorSuggestion,
  DoctorSuggestionResponse,
  ChatPayload,
  ChatResponse,
  AutoAssignPayload,
  AutoAssignReason,
  AutoAssignResponse,
  BillResponse,
  PayBillPayload,
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
  NotificationItem,
  NotificationPage,
  NotificationPreferences,
  PushSubscriptionPayload,
  PaginatedResponse,
};
