import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useQueueNotifications, type QueueNotificationEvent } from '../hooks/useQueueNotifications';
import { useAuth } from './AuthContext';

/**
 * Session-only notification store (Day 7b).
 *
 * Events are DERIVED client-side from the my-status polling by
 * useQueueNotifications — there is no backend notification table, no push,
 * no persistence. History lives in React state for this session only and is
 * cleared on logout and page refresh. This is a deliberate, documented scope
 * boundary; persisted/cross-device notifications are Phase 2.
 */
export interface NotificationItem extends QueueNotificationEvent {
  id: string;
  createdAt: number;
  read: boolean;
}

interface NotificationContextType {
  events: NotificationItem[];
  unreadCount: number;
  /** Toasts currently visible (auto-dismissing). */
  toasts: NotificationItem[];
  markAllRead: () => void;
  markRead: (id: string) => void;
  clearAll: () => void;
  dismissToast: (id: string) => void;
}

const NotificationContext = createContext<NotificationContextType | null>(null);

const MAX_EVENTS = 50;
const TOAST_DURATION_MS = 6_000;

let idCounter = 0;
const nextId = () => `${Date.now()}-${idCounter++}`;

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [events, setEvents] = useState<NotificationItem[]>([]);
  const [toasts, setToasts] = useState<NotificationItem[]>([]);
  const toastTimers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());
  // Session key — events reset whenever the signed-in user changes.
  const sessionKeyRef = useRef<string>(`${user?.id ?? 'anon'}`);

  const addEvent = useCallback((event: QueueNotificationEvent) => {
    const item: NotificationItem = { ...event, id: nextId(), createdAt: Date.now(), read: false };
    setEvents((prev) => [item, ...prev].slice(0, MAX_EVENTS));
    // Fire a non-blocking, auto-dismissing toast.
    setToasts((prev) => [...prev, item]);
    const timer = setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== item.id));
      toastTimers.current.delete(item.id);
    }, TOAST_DURATION_MS);
    toastTimers.current.set(item.id, timer);
  }, []);

  useQueueNotifications(addEvent);

  const markAllRead = useCallback(() => {
    setEvents((prev) => prev.map((e) => ({ ...e, read: true })));
  }, []);

  const markRead = useCallback((id: string) => {
    setEvents((prev) => prev.map((e) => (e.id === id ? { ...e, read: true } : e)));
  }, []);

  const clearAll = useCallback(() => {
    setEvents([]);
    toastTimers.current.forEach((t) => clearTimeout(t));
    toastTimers.current.clear();
    setToasts([]);
  }, []);

  const dismissToast = useCallback((id: string) => {
    const timer = toastTimers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      toastTimers.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // Reset the session history when the signed-in user changes (or logs out),
  // so one session's notifications never leak into the next.
  const sessionKey = `${user?.id ?? 'anon'}`;
  if (sessionKey !== sessionKeyRef.current) {
    sessionKeyRef.current = sessionKey;
    if (events.length > 0 || toasts.length > 0) {
      toastTimers.current.forEach((t) => clearTimeout(t));
      toastTimers.current.clear();
      setEvents([]);
      setToasts([]);
    }
  }

  const unreadCount = useMemo(() => events.filter((e) => !e.read).length, [events]);

  const value = useMemo<NotificationContextType>(
    () => ({ events, unreadCount, toasts, markAllRead, markRead, clearAll, dismissToast }),
    [events, unreadCount, toasts, markAllRead, markRead, clearAll, dismissToast],
  );

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}

export function useNotifications(): NotificationContextType {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider');
  }
  return context;
}
