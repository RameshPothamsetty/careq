import { createApi } from '@reduxjs/toolkit/query/react';
import { authenticatedBaseQuery } from './baseQuery';
import type {
  NotificationItem,
  NotificationPage,
  NotificationPreferences,
  PushSubscriptionPayload,
} from '../api';

/**
 * notificationApi — notification-service endpoints (Day 13) + Web Push
 * delivery preferences (Day 16).
 *
 * The bell's history is backed by REAL persisted notifications instead of the
 * Day 7b session-only derived list: GET /api/notifications/me is polled from
 * the bell (pollingInterval set at the hook call site, like the queue
 * screens), and marking read is a PUT that invalidates the list tag.
 *
 * Day 16 adds the delivery-preferences + push-subscription endpoints the bell
 * toggle drives (see src/hooks/useWebPush.ts).
 */
export const notificationApi = createApi({
  reducerPath: 'notificationApi',
  baseQuery: authenticatedBaseQuery,
  tagTypes: ['Notifications', 'NotificationPreferences'],
  endpoints: (builder) => ({
    getMyNotifications: builder.query<NotificationPage, { page?: number; size?: number } | void>({
      query: (params) => {
        const query = new URLSearchParams();
        if (params?.page != null) query.set('page', String(params.page));
        if (params?.size != null) query.set('size', String(params.size));
        const qs = query.toString();
        return `/api/notifications/me${qs ? `?${qs}` : ''}`;
      },
      providesTags: ['Notifications'],
    }),

    markNotificationRead: builder.mutation<NotificationItem, number>({
      query: (id) => ({
        url: `/api/notifications/${id}/read`,
        method: 'PUT',
      }),
      invalidatesTags: ['Notifications'],
    }),

    // ── Day 16: Web Push delivery ──────────────────────────────────────

    getNotificationPreferences: builder.query<NotificationPreferences, void>({
      query: () => '/api/notifications/preferences',
      providesTags: ['NotificationPreferences'],
    }),

    updateNotificationPreferences: builder.mutation<
      NotificationPreferences,
      NotificationPreferences
    >({
      query: (body) => ({
        url: '/api/notifications/preferences',
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['NotificationPreferences'],
    }),

    registerPushSubscription: builder.mutation<void, PushSubscriptionPayload>({
      query: (body) => ({
        url: '/api/notifications/push/subscriptions',
        method: 'POST',
        body,
      }),
    }),

    removePushSubscription: builder.mutation<void, { endpoint: string }>({
      query: ({ endpoint }) => ({
        url: `/api/notifications/push/subscriptions?endpoint=${encodeURIComponent(endpoint)}`,
        method: 'DELETE',
      }),
    }),
  }),
});

export const {
  useGetMyNotificationsQuery,
  useMarkNotificationReadMutation,
  useGetNotificationPreferencesQuery,
  useUpdateNotificationPreferencesMutation,
  useRegisterPushSubscriptionMutation,
  useRemovePushSubscriptionMutation,
} = notificationApi;
