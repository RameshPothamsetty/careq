import { createApi } from '@reduxjs/toolkit/query/react';
import { authenticatedBaseQuery } from './baseQuery';
import type { NotificationItem, NotificationPage } from '../api';

/**
 * notificationApi — notification-service endpoints (Day 13).
 *
 * The bell's history is now backed by REAL persisted notifications instead of
 * the Day 7b session-only derived list: GET /api/notifications/me is polled
 * from the bell (pollingInterval set at the hook call site, like the queue
 * screens), and marking read is a PUT that invalidates the list tag.
 */
export const notificationApi = createApi({
  reducerPath: 'notificationApi',
  baseQuery: authenticatedBaseQuery,
  tagTypes: ['Notifications'],
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
  }),
});

export const {
  useGetMyNotificationsQuery,
  useMarkNotificationReadMutation,
} = notificationApi;
