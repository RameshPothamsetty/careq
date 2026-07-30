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
    const errorMessage =
      data.message || data.error || 'An unexpected error occurred';
    const error = new Error(errorMessage) as Error & { status: number; details: string[] };
    error.status = response.status;
    error.details = data.details || [];
    throw error;
  }

  return data as T;
}

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

  getProfile: () =>
    request<UserProfileResponse>('/api/users/me'),

  updateProfile: (payload: UpdateProfilePayload) =>
    request<UserProfileResponse>('/api/users/me', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  getUserProfileById: (userId: string) =>
    request<UserProfileResponse>(`/api/users/${userId}`),

  // ---- Department API ----

  getDepartments: () =>
    request<DepartmentResponse[]>('/api/departments'),

  getDepartmentById: (id: number) =>
    request<DepartmentResponse>(`/api/departments/${id}`),

  createDepartment: (payload: DepartmentRequest) =>
    request<DepartmentResponse>('/api/departments', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateDepartment: (id: number, payload: DepartmentRequest) =>
    request<DepartmentResponse>(`/api/departments/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  deleteDepartment: (id: number) =>
    request<void>(`/api/departments/${id}`, {
      method: 'DELETE',
    }),

  // ---- Doctor Catalog API ----

  getDoctors: (params?: { departmentId?: number; specialization?: string }) => {
    const query = new URLSearchParams();
    if (params?.departmentId) query.set('departmentId', String(params.departmentId));
    if (params?.specialization) query.set('specialization', params.specialization);
    const qs = query.toString();
    return request<DoctorCatalogResponse[]>(`/api/doctors${qs ? `?${qs}` : ''}`);
  },

  getDoctorById: (id: number) =>
    request<DoctorCatalogResponse>(`/api/doctors/${id}`),

  createDoctor: (payload: DoctorCatalogRequest) =>
    request<DoctorCatalogResponse>('/api/doctors', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateDoctor: (id: number, payload: DoctorCatalogRequest) =>
    request<DoctorCatalogResponse>(`/api/doctors/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  deleteDoctor: (id: number) =>
    request<void>(`/api/doctors/${id}`, {
      method: 'DELETE',
    }),

  toggleAvailability: (payload: AvailabilityRequest) =>
    request<DoctorCatalogResponse>('/api/doctors/me/availability', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  uploadProfilePicture: async (file: File): Promise<UserProfileResponse> => {
    const token = localStorage.getItem('careq_token');
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch(`${API_BASE_URL}/api/users/me/profile-picture`, {
      method: 'POST',
      headers: {
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
      },
      body: formData,
    });

    const data = await response.json();

    if (!response.ok) {
      const errorMessage = data.message || data.error || 'Failed to upload picture';
      throw new Error(errorMessage);
    }

    return data as UserProfileResponse;
  },
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
};
