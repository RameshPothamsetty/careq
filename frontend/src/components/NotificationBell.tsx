import { useEffect, useRef, useState } from 'react';
import { Bell, CheckCheck } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useI18n } from '../i18n';
import type { TranslationKey } from '../i18n/translations';
import { useWebPush } from '../hooks/useWebPush';
import {
  useGetMyNotificationsQuery,
  useMarkNotificationReadMutation,
} from '../services/rtk/notificationApi';
const TYPE_META: Record<string, { titleKey: TranslationKey; kind: 'success' | 'info' | 'warning' }> = {
  'queue.joined': { titleKey: 'header.queueJoined', kind: 'success' },
  'queue.triaged': { titleKey: 'header.urgencyUpdated', kind: 'info' },
  'queue.called': { titleKey: 'header.yourTurn', kind: 'warning' },
  'queue.completed': { titleKey: 'header.consultationComplete', kind: 'success' },
};

const KIND_DOT: Record<string, string> = {
  success: 'bg-emerald-500',
  info: 'bg-sky-500',
  warning: 'bg-amber-500',
};

const POLL_INTERVAL_MS = 15_000;

/**
 * Notification bell — the dropdown history is now backed by
 * REAL persisted notifications from notification-service (GET /api/notifications/me,
 * polled every 15s), so it survives page refreshes and shows on any device.
 * The real-time toast-on-status-change layer is untouched and still
 * lives in NotificationContext (immediate feedback while the bell is the
 * durable record). Only PATIENTs see the bell — every queue event type is
 * patient-centric.
 */
export default function NotificationBell() {
  const { user, isAuthenticated } = useAuth();
  const { t } = useI18n();
  const isPatient = isAuthenticated && user?.role === 'PATIENT';

  const { data, isLoading, refetch } = useGetMyNotificationsQuery(undefined, {
    skip: !isPatient,
    pollingInterval: isPatient ? POLL_INTERVAL_MS : 0,
  });
  const [markNotificationRead] = useMarkNotificationReadMutation();

  // Web Push toggle — hooks must stay above the early return.
  const webPush = useWebPush(!!isPatient);

  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);

  const timeAgo = (iso: string): string => {
    const ts = new Date(iso).getTime();
    if (Number.isNaN(ts)) return '';
    const s = Math.max(0, Math.floor((Date.now() - ts) / 1000));
    if (s < 60) return t('header.justNow');
    const m = Math.floor(s / 60);
    if (m < 60) return t('header.mAgo', { m });
    const h = Math.floor(m / 60);
    if (h < 24) return t('header.hAgo', { h });
    return t('header.dAgo', { d: Math.floor(h / 24) });
  };

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
        className="relative flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900 dark:border-slate-600 dark:bg-night-800/70 dark:text-slate-300 dark:hover:bg-slate-700/60 dark:hover:text-slate-100"
        aria-label={`${t('header.notifications')}${unreadCount ? ` (${unreadCount} ${t('header.unread')})` : ''}`}
      >
        <Bell className="h-5 w-5" />
        {unreadCount > 0 && (
          <span className="absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white shadow-sm">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lift dark:border-slate-700 dark:bg-night-800">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-slate-700/50">
            <div>
              <p className="text-sm font-bold text-slate-800 dark:text-slate-100">{t('header.notifications')}</p>
              <p className="text-[11px] text-slate-400 dark:text-slate-500">{t('header.persisted')}</p>
            </div>
            <button
              onClick={handleMarkAllRead}
              disabled={unreadItems.length === 0 || busy}
              className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-50 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-40 dark:text-slate-500 dark:hover:bg-slate-700/60 dark:hover:text-slate-300"
              title={
                unreadItems.length
                  ? t('header.markNRead', { n: unreadItems.length })
                  : t('header.markAllRead')
              }
            >
              <CheckCheck className={`h-4 w-4 ${busy ? 'animate-pulse' : ''}`} />
            </button>
          </div>

          {webPush.pushSupported && (
            <div className="border-b border-slate-100 px-4 py-3 dark:border-slate-700/50">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                    {t('header.pushNotifications')}
                  </p>
                  <p className="mt-0.5 text-[11px] leading-snug text-slate-400 dark:text-slate-500">
                    {webPush.denied ? t('header.pushDenied') : t('header.pushHint')}
                  </p>
                  {webPush.error && (
                    <p className="mt-0.5 text-[11px] text-red-500">{webPush.error}</p>
                  )}
                </div>
                <button
                  role="switch"
                  aria-checked={webPush.pushEnabled}
                  aria-label={t('header.pushNotifications')}
                  onClick={webPush.togglePush}
                  disabled={webPush.busy}
                  className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${
                    webPush.pushEnabled
                      ? 'bg-emerald-500'
                      : 'bg-slate-300 dark:bg-slate-600'
                  } ${webPush.busy ? 'cursor-wait opacity-60' : ''}`}
                >
                  <span
                    className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all ${
                      webPush.pushEnabled ? 'left-[22px]' : 'left-0.5'
                    }`}
                  />
                </button>
              </div>
            </div>
          )}

          <div className="max-h-72 overflow-y-auto">
            {isLoading && items.length === 0 ? (
              <div className="space-y-2 px-4 py-4">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="h-12 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-700/50" />
                ))}
              </div>
            ) : items.length === 0 ? (
              <div className="px-4 py-10 text-center">
                <Bell className="mx-auto h-7 w-7 text-slate-300 dark:text-slate-600" />
                <p className="mt-3 text-sm font-medium text-slate-500 dark:text-slate-400">{t('header.noNotifications')}</p>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
                  {t('header.noNotificationsHint')}
                </p>
              </div>
            ) : (
              <ul className="divide-y divide-slate-50 dark:divide-slate-700/40">
                {items.map((item) => {
                  const meta = TYPE_META[item.type] ?? { titleKey: 'header.update' as TranslationKey, kind: 'info' as const };
                  return (
                    <li key={item.id} className="flex gap-3 px-4 py-3 transition-colors hover:bg-slate-50 dark:hover:bg-slate-700/40">
                      <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${KIND_DOT[meta.kind] ?? 'bg-slate-400'}`} />
                      <div className="min-w-0">
                        <div className="flex items-baseline justify-between gap-2">
                          <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">{t(meta.titleKey)}</p>
                          <span className="shrink-0 text-[10px] text-slate-400 dark:text-slate-500">{timeAgo(item.createdAt)}</span>
                        </div>
                        <p className="mt-0.5 text-xs leading-relaxed text-slate-500 dark:text-slate-400">{item.message}</p>
                        {!item.read && (
                          <span className="mt-1.5 inline-block rounded-full bg-brand-50 px-2 py-0.5 text-[10px] font-semibold text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
                            {t('header.new')}
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
