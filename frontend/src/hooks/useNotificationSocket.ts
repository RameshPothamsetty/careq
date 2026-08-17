import { useEffect, useRef } from 'react';
import { useAppDispatch } from '../store';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';
import { connectNotificationSocket, type NotificationSocket } from '../services/ws';
import { notificationApi } from '../services/rtk/notificationApi';

/** Event types the backend can push (mirrors NotificationConsumer). */
const TYPE_TITLE: Record<string, { title: string; kind: 'success' | 'info' | 'warning' }> = {
  'queue.joined': { title: 'Queue joined', kind: 'success' },
  'queue.triaged': { title: 'Triage updated', kind: 'info' },
  'queue.called': { title: "It's your turn!", kind: 'warning' },
  'queue.completed': { title: 'Consultation complete', kind: 'success' },
};

/**
 * Real-time notification socket.
 *
 * Opens a STOMP connection to /ws while a user is signed in and subscribes to
 * their /topic/notifications/{userId}. On every pushed notification it:
 *   1. invalidates the bell's RTK cache so the list refetches instantly, and
 *   2. fires an auto-dismissing toast (the same UX the polling path produces).
 *
 * Polling stays untouched as the fallback — the socket is an enhancement, not
 * a replacement, so a closed tab or a reconnect gap loses nothing.
 */
export function useNotificationSocket(): void {
  const { isAuthenticated, user, token } = useAuth();
  const dispatch = useAppDispatch();
  const { pushToast } = useNotifications();
  const socketRef = useRef<NotificationSocket | null>(null);

  useEffect(() => {
    if (!isAuthenticated || !user || !token) {
      return;
    }

    const socket = connectNotificationSocket(token, user.id, (payload) => {
      const type = (payload as { type?: string })?.type ?? '';
      const message = (payload as { message?: string })?.message ?? '';
      const meta = TYPE_TITLE[type];
      if (meta) {
        pushToast(meta.title, message, meta.kind);
      }
      // Instant bell refresh — the next poll tick would catch it anyway.
      dispatch(notificationApi.util.invalidateTags(['Notifications']));
    });
    socketRef.current = socket;

    return () => {
      socket.disconnect();
      socketRef.current = null;
    };
    // Reconnect only when the session identity changes, not on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, user?.id, token]);

  // If the session is gone, make sure a stale socket is torn down.
  useEffect(() => {
    if (!isAuthenticated && socketRef.current) {
      socketRef.current.disconnect();
      socketRef.current = null;
    }
  }, [isAuthenticated]);
}
