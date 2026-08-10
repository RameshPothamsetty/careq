import { useEffect, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import { useI18n, type I18nT } from '../i18n';
import { useGetMyQueueStatusQuery } from '../services/rtk/queueApi';

/** An event derived client-side from a queue status transition (Day 7b). */
export interface QueueNotificationEvent {
  title: string;
  message: string;
  kind: 'success' | 'info' | 'warning';
}

const POLL_INTERVAL_MS = 10_000;

/**
 * Watches the patient's my-status polling (the exact same RTK Query cache
 * entry the My Queue screen subscribes to — one poll, many subscribers) and
 * emits a derived event whenever a tracked transition happens:
 *
 *   - inactive → active           "You joined Dr. X's queue"
 *   - WAITING → IN_PROGRESS       "It's your turn! Dr. X has called you"
 *   - WAITING, position decreased "You moved up to position #N"
 *   - active → gone / COMPLETED   "Consultation complete"
 *
 * The FIRST successful poll is treated as a baseline (no events fired) so a
 * page refresh mid-queue never replays "you joined" spam. Everything here is
 * client-side and session-only — the durable record lives in
 * notification-service (Day 13).
 */
export function useQueueNotifications(
  onEvent: (event: QueueNotificationEvent) => void,
): void {
  const { isAuthenticated, user } = useAuth();
  const { t } = useI18n();
  const isPatient = isAuthenticated && user?.role === 'PATIENT';

  const { data } = useGetMyQueueStatusQuery(undefined, {
    // Only patients have a my-status; skip entirely for other roles/sessions.
    skip: !isPatient,
    pollingInterval: isPatient ? POLL_INTERVAL_MS : 0,
  });

  const baselineRef = useRef(false);
  const prevRef = useRef<{
    id: number;
    status: string;
    position: number | null;
  } | null>(null);
  const onEventRef = useRef(onEvent);
  const tRef = useRef<I18nT>(t);

  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    tRef.current = t;
  }, [t]);

  useEffect(() => {
    if (!data) return;

    const entry = data.active ? data.entry : null;
    const current = entry
      ? { id: entry.id, status: entry.status, position: entry.position }
      : null;
    const prev = prevRef.current;

    // First successful poll — baseline only, never emit.
    if (!baselineRef.current) {
      baselineRef.current = true;
      prevRef.current = current;
      return;
    }
    prevRef.current = current;

    // No meaningful change → no event.
    if (current === null && prev === null) return;
    if (
      prev &&
      current &&
      prev.id === current.id &&
      prev.status === current.status &&
      prev.position === current.position
    ) {
      return;
    }

    const doctorName = entry?.doctorName?.trim() || tRef.current('queue.yourDoctor');

    // 1. Joined the queue (was inactive, now active).
    if (prev === null && current) {
      onEventRef.current({
        title: tRef.current('toast.queueJoined'),
        message: tRef.current('toast.joinedMessage', {
          doctor: doctorName,
          position: current.position ?? '—',
        }),
        kind: 'success',
      });
      return;
    }

    // 2. Consultation finished: entry left the active queue, or was explicitly completed.
    if (current === null || (prev?.status === 'IN_PROGRESS' && current.status === 'COMPLETED')) {
      onEventRef.current({
        title: tRef.current('toast.consultationComplete'),
        message: tRef.current('toast.completeMessage', {
          prefix:
            prev?.status === 'IN_PROGRESS'
              ? tRef.current('toast.completePrefix', { doctor: doctorName })
              : '',
        }),
        kind: 'success',
      });
      return;
    }

    // 3. Called: WAITING → IN_PROGRESS. The flagship transition.
    if (prev?.status === 'WAITING' && current.status === 'IN_PROGRESS') {
      onEventRef.current({
        title: tRef.current('toast.yourTurn'),
        message: tRef.current('toast.calledMessage', { doctor: doctorName }),
        kind: 'warning',
      });
      return;
    }

    // 4. Moved up while still waiting (position strictly decreased).
    if (
      current.status === 'WAITING' &&
      prev?.status === 'WAITING' &&
      prev.position !== null &&
      current.position !== null &&
      current.position < prev.position
    ) {
      onEventRef.current({
        title: tRef.current('toast.positionMovedUp'),
        message: tRef.current('toast.movedUpMessage', {
          position: current.position,
          doctor: doctorName,
        }),
        kind: 'info',
      });
    }
  }, [data]);
}
