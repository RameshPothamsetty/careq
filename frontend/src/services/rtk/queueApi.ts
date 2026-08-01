import { createApi } from '@reduxjs/toolkit/query/react';
import { authenticatedBaseQuery } from './baseQuery';
import type {
  QueueEntryResponse,
  QueueStatusResponse,
  JoinQueuePayload,
  OverrideTriagePayload,
  TriageLevel,
  LiveQueueOverview,
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
  tagTypes: ['QueueStatus', 'DoctorQueue', 'LiveQueue'],
  endpoints: (builder) => ({
    joinQueue: builder.mutation<QueueEntryResponse, JoinQueuePayload>({
      query: (body) => ({
        url: '/api/queue/join',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue'],
    }),

    getMyQueueStatus: builder.query<QueueStatusResponse, void>({
      query: () => '/api/queue/my-status',
      providesTags: ['QueueStatus'],
    }),

    getDoctorQueue: builder.query<QueueEntryResponse[], number>({
      query: (doctorCatalogEntryId) => `/api/queue/doctor/${doctorCatalogEntryId}`,
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
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue'],
    }),

    callNext: builder.mutation<QueueEntryResponse, number>({
      query: (id) => ({
        url: `/api/queue/${id}/call-next`,
        method: 'PUT',
      }),
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue'],
    }),

    completeQueueEntry: builder.mutation<QueueEntryResponse, number>({
      query: (id) => ({
        url: `/api/queue/${id}/complete`,
        method: 'PUT',
      }),
      invalidatesTags: ['QueueStatus', 'DoctorQueue', 'LiveQueue'],
    }),

    getLiveQueueOverview: builder.query<LiveQueueOverview, void>({
      query: () => '/api/queue/live',
      providesTags: ['LiveQueue'],
    }),
  }),
});

export const {
  useJoinQueueMutation,
  useGetMyQueueStatusQuery,
  useGetDoctorQueueQuery,
  useOverrideTriageMutation,
  useCallNextMutation,
  useCompleteQueueEntryMutation,
  useGetLiveQueueOverviewQuery,
} = queueApi;
