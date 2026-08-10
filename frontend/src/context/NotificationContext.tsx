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
 * Immediate-feedback toast layer (Day 7b), kept after the Day 13 upgrade.
 *
 * The BELL now shows real persisted notifications from notification-service;
 * this context's only remaining job is the real-time, auto-dismissing TOAST
 * that fires the instant a queue status transition is observed by the
 * my-status polling (called → "It's your turn!", moved up, completed, etc.).
 * Toasts are session-only by design — the durable record lives in
 * notification-service.
 */
export interface ToastItem {
  id: string;
  title: string;
  message: string;
  kind: 'success' | 'info' | 'warning';
}

interface NotificationContextType {
  /** Toasts currently visible (auto-dismissing). */
  toasts: ToastItem[];
  dismissToast: (id: string) => void;
}

const NotificationContext = createContext<NotificationContextType | null>(null);

const TOAST_DURATION_MS = 6_000;

let idCounter = 0;
const nextId = () => `${Date.now()}-${idCounter++}`;

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const toastTimers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());
  // Session key — toasts reset whenever the signed-in user changes.
  const sessionKeyRef = useRef<string>(`${user?.id ?? 'anon'}`);

  const addEvent = useCallback((event: QueueNotificationEvent) => {
    const item: ToastItem = { ...event, id: nextId() };
    setToasts((prev) => [...prev, item]);
    const timer = setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== item.id));
      toastTimers.current.delete(item.id);
    }, TOAST_DURATION_MS);
    toastTimers.current.set(item.id, timer);
  }, []);

  useQueueNotifications(addEvent);

  const dismissToast = useCallback((id: string) => {
    const timer = toastTimers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      toastTimers.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // Reset the toast queue when the signed-in user changes (or logs out), so
  // one session's toasts never leak into the next.
  const sessionKey = `${user?.id ?? 'anon'}`;
  if (sessionKey !== sessionKeyRef.current) {
    sessionKeyRef.current = sessionKey;
    if (toasts.length > 0) {
      toastTimers.current.forEach((t) => clearTimeout(t));
      toastTimers.current.clear();
      setToasts([]);
    }
  }

  const value = useMemo<NotificationContextType>(
    () => ({ toasts, dismissToast }),
    [toasts, dismissToast],
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
