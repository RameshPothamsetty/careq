import { createApi } from '@reduxjs/toolkit/query/react';
import { authenticatedBaseQuery } from './baseQuery';
import type {
  QueueEntryResponse,
  QueueStatusResponse,
  JoinQueuePayload,
  DoctorSuggestionPayload,
  DoctorSuggestionResponse,
  OverrideTriagePayload,
  TriageLevel,
  LiveQueueOverview,
  AnalyticsSummary,
  DoctorAnalyticsSummary,
} from '../api';

/**
 * queueApi — queue-service endpoints: join, live status, doctor queue,
 * doctor actions (override / call-next / complete) and admin live overview.
 *
 * Polling is configured per-screen via the pollingInterval option on the
 * hooks (live queue screens poll every 10s) — RTK Query replaces the
 * hand-rolled setInterval calls from Day 5.
 */
export const queueApi = createApi({
  reducerPath: 'queueApi',
  baseQuery: authenticatedBaseQuery,
  tagTypes: ['QueueStatus', 'DoctorQueue', 'LiveQueue', 'Analytics'],
  endpoints: (builder) => ({
    joinQueue: builder.mutation<QueueEntryResponse, JoinQueuePayload>({
      query: (body) => ({
        url: '/api/queue/join',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue', 'Analytics'],
    }),

    // AI doctor recommendation (Phase 1) — symptoms first, then the patient
    // picks one of the suggested doctors. A mutation so it only fires on demand
    // (it makes a live Groq call plus live queue-load lookups).
    doctorSuggestions: builder.mutation<DoctorSuggestionResponse, DoctorSuggestionPayload>({
      query: (body) => ({
        url: '/api/queue/doctor-suggestions',
        method: 'POST',
        body,
      }),
    }),

    getMyQueueStatus: builder.query<QueueStatusResponse, void>({
      query: () => '/api/queue/my-status',
      providesTags: ['QueueStatus'],
    }),

    // Patient's recent visit history (completed/cancelled, newest first).
    getMyQueueHistory: builder.query<QueueEntryResponse[], number | void>({
      query: (limit) => `/api/queue/my-history?limit=${limit ?? 10}`,
      providesTags: ['QueueStatus'],
    }),

    getDoctorQueue: builder.query<
      QueueEntryResponse[],
      { doctorCatalogEntryId: number; search?: string }
    >({
      query: ({ doctorCatalogEntryId, search }) => {
        const qs = search ? `?search=${encodeURIComponent(search)}` : '';
        return `/api/queue/doctor/${doctorCatalogEntryId}${qs}`;
      },
      providesTags: ['DoctorQueue'],
    }),

    overrideTriage: builder.mutation<
      QueueEntryResponse,
      { id: number; triageLevel: TriageLevel }
    >({
      query: ({ id, triageLevel }) => ({
        url: `/api/queue/${id}/override-triage`,
        method: 'PUT',
        body: { triageLevel } satisfies OverrideTriagePayload,
      }),
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue', 'Analytics'],
    }),

    callNext: builder.mutation<QueueEntryResponse, number>({
      query: (id) => ({
        url: `/api/queue/${id}/call-next`,
        method: 'PUT',
      }),
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue', 'Analytics'],
    }),

    completeQueueEntry: builder.mutation<QueueEntryResponse, number>({
      query: (id) => ({
        url: `/api/queue/${id}/complete`,
        method: 'PUT',
      }),
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue', 'Analytics'],
    }),

    // Patient leaves the queue before being seen (WAITING only). The status
    // refetch then reports no active entry, returning the patient to the join
    // flow; the cancelled visit also lands in their history.
    cancelQueueEntry: builder.mutation<QueueEntryResponse, number>({
      query: (id) => ({
        url: `/api/queue/${id}/cancel`,
        method: 'PUT',
      }),
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue', 'Analytics'],
    }),

    getLiveQueueOverview: builder.query<LiveQueueOverview, void>({
      query: () => '/api/queue/live',
      providesTags: ['LiveQueue'],
    }),

    getAnalyticsSummary: builder.query<AnalyticsSummary, void>({
      query: () => '/api/queue/analytics/summary',
      // Analytics is aggregated from completed/called entries, so every
      // queue mutation invalidates it — the charts stay fresh.
      providesTags: ['Analytics'],
    }),

    // Per-doctor analytics (dashboard upgrade) — today's scalars + 7-day
    // trends for one doctor. Shares the Analytics tag so queue mutations
    // keep the doctor's numbers fresh too.
    getDoctorAnalytics: builder.query<DoctorAnalyticsSummary, number>({
      query: (doctorCatalogEntryId) => `/api/queue/doctor/${doctorCatalogEntryId}/analytics`,
      providesTags: ['Analytics'],
    }),
  }),
});

export const {
  useJoinQueueMutation,
  useDoctorSuggestionsMutation,
  useGetMyQueueStatusQuery,
  useGetMyQueueHistoryQuery,
  useGetDoctorQueueQuery,
  useOverrideTriageMutation,
  useCallNextMutation,
  useCompleteQueueEntryMutation,
  useCancelQueueEntryMutation,
  useGetLiveQueueOverviewQuery,
  useGetAnalyticsSummaryQuery,
  useGetDoctorAnalyticsQuery,
} = queueApi;
