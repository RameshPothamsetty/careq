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
};

export type { AuthResponse, SignupPayload, LoginPayload, UserProfileResponse, UpdateProfilePayload };
