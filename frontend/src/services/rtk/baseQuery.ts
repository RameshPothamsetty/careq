import {
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from '@reduxjs/toolkit/query/react';

/**
 * Single shared baseQuery for every RTK Query slice.
 *
 * The JWT is injected centrally here — no slice or screen repeats the
 * Authorization header logic. The token is read from localStorage, which is
 * the same source of truth AuthContext writes to on login/signup, so this
 * stays decoupled from React context (baseQuery is module-scoped and cannot
 * call useAuth()).
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const baseFetch = fetchBaseQuery({
  baseUrl: API_BASE_URL,
  prepareHeaders: (headers) => {
    const token = localStorage.getItem('careq_token');
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  },
});

/**
 * Every authenticated endpoint goes through this baseQuery, so a single 401
 * (expired/invalid JWT) is handled centrally:
 *  1. wipe the stored session from localStorage, and
 *  2. notify AuthContext via a DOM event (avoids a circular import between
 *     this module and store.ts), which forces logout and resets the RTK
 *     caches — the user is never left stuck on stale cached data.
 *
 * The login/signup calls in AuthContext use the plain `request()` helper in
 * api.ts, NOT this baseQuery, so a wrong-password 401 will never trigger this.
 */
/** Fired on a 401 so AuthContext can force logout — shared with AuthContext to prevent string drift. */
export const UNAUTHORIZED_EVENT = 'careq:unauthorized';

function handleUnauthorized() {
  localStorage.removeItem('careq_token');
  localStorage.removeItem('careq_user');
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
}

export const authenticatedBaseQuery: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  const result = await baseFetch(args, api, extraOptions);
  if (result.error && result.error.status === 401) {
    handleUnauthorized();
  }
  return result;
};

/**
 * Normalizes RTK Query errors (FetchBaseQueryError / SerializedError / Error)
 * into a single user-facing message, matching the backend's shared error
 * shape: { message, error, validationErrors: [{ field, message }] } (Day 9).
 */
interface BackendErrorData {
  message?: string;
  error?: string;
  details?: string[]; // legacy — pre-Day-9 shape, kept for safety
  validationErrors?: { field?: string; message?: string }[];
}

export function getErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'data' in error) {
    const data = (error as { data?: BackendErrorData }).data;
    // Field-level validation errors are more useful than the generic
    // "Request validation failed" message on 400s — surface them first.
    if (data?.validationErrors?.length) {
      const first = data.validationErrors[0];
      return first.field ? `${first.field}: ${first.message}` : (first.message ?? '');
    }
    if (data?.message) return data.message;
    if (data?.error) return data.error;
  }
  if (error instanceof Error) return error.message;
  return 'An unexpected error occurred';
}
