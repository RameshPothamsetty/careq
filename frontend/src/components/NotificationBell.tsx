import { useEffect, useRef, useState } from 'react';
import { Bell, CheckCheck } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import {
  useGetMyNotificationsQuery,
  useMarkNotificationReadMutation,
} from '../services/rtk/notificationApi';
import type { NotificationItem } from '../services/api';

const TYPE_META: Record<string, { title: string; kind: 'success' | 'info' | 'warning' }> = {
  'queue.joined': { title: 'Queue joined', kind: 'success' },
  'queue.triaged': { title: 'Urgency updated', kind: 'info' },
  'queue.called': { title: "It's your turn!", kind: 'warning' },
  'queue.completed': { title: 'Consultation complete', kind: 'success' },
};

const KIND_DOT: Record<string, string> = {
  success: 'bg-emerald-500',
  info: 'bg-sky-500',
  warning: 'bg-amber-500',
};

const POLL_INTERVAL_MS = 15_000;

function metaFor(item: NotificationItem) {
  return TYPE_META[item.type] ?? { title: 'Update', kind: 'info' as const };
}

function timeAgo(iso: string): string {
  const ts = new Date(iso).getTime();
  if (Number.isNaN(ts)) return '';
  const s = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (s < 60) return 'just now';
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

/**
 * Notification bell — Day 13 upgrade: the dropdown history is now backed by
 * REAL persisted notifications from notification-service (GET /api/notifications/me,
 * polled every 15s), so it survives page refreshes and shows on any device.
 * The Day 7b real-time toast-on-status-change layer is untouched and still
 * lives in NotificationContext (immediate feedback while the bell is the
 * durable record). Only PATIENTs see the bell — every queue event type is
 * patient-centric.
 */
export default function NotificationBell() {
  const { user, isAuthenticated } = useAuth();
  const isPatient = isAuthenticated && user?.role === 'PATIENT';

  const { data, isLoading, refetch } = useGetMyNotificationsQuery(undefined, {
    skip: !isPatient,
    pollingInterval: isPatient ? POLL_INTERVAL_MS : 0,
  });
  const [markNotificationRead] = useMarkNotificationReadMutation();

  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);

  // Close the dropdown on outside click. NOTE: this effect must stay ABOVE the
  // early return — hooks must run unconditionally or React will throw a
  // "rendered more hooks than during the previous render" error when the role
  // flips (e.g. a saved session is restored and the user becomes a patient).
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [open]);

  // Events are patient-centric — other roles never receive any, so don't show
  // them a bell that promises notifications.
  if (!isPatient) {
    return null;
  }

  const items = data?.content ?? [];
  const unreadCount = data?.unreadCount ?? 0;
  const unreadItems = items.filter((item) => !item.read);

  // No bulk endpoint exists (deliberately): mark each unread row on the
  // current page — bounded by the page size (20), so this is at most 20 PUTs.
  const handleMarkAllRead = async () => {
    if (unreadItems.length === 0 || busy) return;
    setBusy(true);
    try {
      await Promise.all(unreadItems.map((item) => markNotificationRead(item.id).unwrap()));
      // The Notifications tag invalidation refetches the list automatically.
      refetch();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="relative" ref={panelRef}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
        aria-label={`Notifications${unreadCount ? ` (${unreadCount} unread)` : ''}`}
      >
        <Bell className="h-5 w-5" />
        {unreadCount > 0 && (
          <span className="absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white shadow-sm">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lift">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div>
              <p className="text-sm font-bold text-slate-800">Notifications</p>
              <p className="text-[11px] text-slate-400">Persisted — stays after refresh</p>
            </div>
            <button
              onClick={handleMarkAllRead}
              disabled={unreadItems.length === 0 || busy}
              className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-50 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
              title={unreadItems.length ? `Mark ${unreadItems.length} unread as read` : 'Mark all read'}
            >
              <CheckCheck className={`h-4 w-4 ${busy ? 'animate-pulse' : ''}`} />
            </button>
          </div>

          <div className="max-h-72 overflow-y-auto">
            {isLoading && items.length === 0 ? (
              <div className="space-y-2 px-4 py-4">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="h-12 animate-pulse rounded-xl bg-slate-100" />
                ))}
              </div>
            ) : items.length === 0 ? (
              <div className="px-4 py-10 text-center">
                <Bell className="mx-auto h-7 w-7 text-slate-300" />
                <p className="mt-3 text-sm font-medium text-slate-500">No notifications yet</p>
                <p className="mt-1 text-xs text-slate-400">
                  Queue updates (joined, called, complete) will be saved here.
                </p>
              </div>
            ) : (
              <ul className="divide-y divide-slate-50">
                {items.map((item) => {
                  const meta = metaFor(item);
                  return (
                    <li key={item.id} className="flex gap-3 px-4 py-3 transition-colors hover:bg-slate-50">
                      <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${KIND_DOT[meta.kind] ?? 'bg-slate-400'}`} />
                      <div className="min-w-0">
                        <div className="flex items-baseline justify-between gap-2">
                          <p className="truncate text-sm font-semibold text-slate-800">{meta.title}</p>
                          <span className="shrink-0 text-[10px] text-slate-400">{timeAgo(item.createdAt)}</span>
                        </div>
                        <p className="mt-0.5 text-xs leading-relaxed text-slate-500">{item.message}</p>
                        {!item.read && (
                          <span className="mt-1.5 inline-block rounded-full bg-brand-50 px-2 py-0.5 text-[10px] font-semibold text-brand-700">
                            New
                          </span>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
