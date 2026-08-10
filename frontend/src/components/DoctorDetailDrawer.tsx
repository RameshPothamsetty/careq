import { useEffect, useMemo, useRef } from 'react';
import { Activity, Clock, Stethoscope, X } from 'lucide-react';
import { useGetDoctorQueueQuery } from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import type { DoctorCatalogResponse, QueueEntryResponse } from '../services/api';
import { AvatarInitials, StatusTag, LiveBadge } from './ui';

const POLL_INTERVAL_MS = 10_000;

function shortId(patientId: string) {
  return `#${patientId.slice(0, 4).toUpperCase()}`;
}

function displayName(entry: QueueEntryResponse) {
  return entry.patientName || shortId(entry.patientId);
}

/** Compact inline state blocks sized for a drawer section (not a full page). */
function SectionLoading({ label }: { label: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <div className="h-6 w-6 animate-spin rounded-full border-2 border-brand-200 border-t-brand-600 dark:border-brand-500/25 dark:border-t-brand-400" />
      <p className="mt-3 text-xs text-slate-400 dark:text-slate-500">{label}</p>
    </div>
  );
}

function SectionEmpty({ title, message }: { title: string; message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <Activity className="h-6 w-6 text-slate-300 dark:text-slate-600" />
      <p className="mt-2 text-sm font-semibold text-slate-600 dark:text-slate-300">{title}</p>
      <p className="mt-1 max-w-[260px] text-xs text-slate-400 dark:text-slate-500">{message}</p>
    </div>
  );
}

function SectionError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <p className="text-sm font-semibold text-red-600 dark:text-red-400">Couldn't load the queue</p>
      <p className="mt-1 max-w-[260px] text-xs text-slate-400 dark:text-slate-500">{message}</p>
      <button
        onClick={onRetry}
        className="mt-3 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 transition-colors hover:border-brand-300 hover:text-brand-700 dark:border-slate-600 dark:text-slate-300 dark:hover:border-brand-400 dark:hover:text-brand-300"
      >
        Try again
      </button>
    </div>
  );
}

/**
 * DoctorDetailDrawer — a right-side slide-over opened from the admin's Manage
 * Doctors table. Shows the full doctor record (qualification, fee, experience,
 * department, availability) plus a LIVE snapshot of that doctor's queue:
 * waiting/in-progress counts, longest predicted wait, and the current patient
 * list with triage badges (polled every 10s, read-only).
 *
 * A11y: focus moves into the panel on open, is trapped while open, and is
 * restored to the trigger on close. Escape closes.
 */
export default function DoctorDetailDrawer({
  doctor,
  onClose,
}: {
  doctor: DoctorCatalogResponse;
  onClose: () => void;
}) {
  const panelRef = useRef<HTMLDivElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  // Stable ref so the keydown effect never re-registers when onClose changes.
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  const {
    data: queue,
    isLoading,
    isError,
    error,
    refetch,
    fulfilledTimeStamp,
  } = useGetDoctorQueueQuery(
    { doctorCatalogEntryId: doctor.id },
    { pollingInterval: POLL_INTERVAL_MS },
  );

  const entries = queue ?? [];
  const waiting = useMemo(() => entries.filter((e) => e.status === 'WAITING'), [entries]);
  const inProgress = useMemo(() => entries.filter((e) => e.status === 'IN_PROGRESS'), [entries]);
  const longestWait = useMemo(
    () => waiting.reduce((max, e) => Math.max(max, e.predictedWaitMinutes ?? 0), 0),
    [waiting],
  );
  const secondsAgo = Math.max(
    0,
    Math.round((Date.now() - (fulfilledTimeStamp ?? Date.now())) / 1000),
  );

  const doctorLabel = doctor.name || doctor.specialization;

  // Focus management + Escape + body scroll lock. Runs once on mount (the
  // effect has no changing deps) — onClose is read via a ref.
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    closeButtonRef.current?.focus();
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCloseRef.current();
        return;
      }
      // Simple focus trap: keep Tab cycling within the panel.
      if (e.key === 'Tab' && panelRef.current) {
        const focusables = panelRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        );
        if (focusables.length === 0) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };

    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
      previouslyFocused?.focus();
    };
  }, []);

  const details: { label: string; value: string }[] = [
    { label: 'Department', value: doctor.departmentName },
    { label: 'Specialization', value: doctor.specialization },
    { label: 'Qualification', value: doctor.qualification },
    { label: 'Experience', value: `${doctor.experienceYears} years` },
    { label: 'Consultation fee', value: `₹${doctor.consultationFee}` },
    { label: 'Avg consult time', value: `${doctor.avgConsultationTimeMinutes} min` },
    { label: 'User ID', value: doctor.userId },
  ];

  return (
    <div className="fixed inset-0 z-50" role="dialog" aria-modal="true" aria-label={`${doctorLabel} details`}>
      {/* Backdrop — click to close */}
      <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-[2px]" onClick={onClose} />

      {/* Panel */}
      <div
        ref={panelRef}
        className="animate-drawer-in absolute right-0 top-0 flex h-full w-full max-w-md flex-col bg-white shadow-lift dark:bg-night-800"
      >
        {/* Header */}
        <div className="relative shrink-0 overflow-hidden bg-gradient-to-br from-brand-600 to-brand-800 p-6 text-white">
          <div className="pointer-events-none absolute -right-8 -top-10 h-36 w-36 rounded-full bg-white/10" />
          <button
            ref={closeButtonRef}
            onClick={onClose}
            className="absolute right-4 top-4 flex h-8 w-8 items-center justify-center rounded-lg text-brand-100 transition-colors hover:bg-white/15 hover:text-white"
            aria-label="Close doctor details"
          >
            <X className="h-5 w-5" />
          </button>
          <div className="relative flex items-center gap-4">
            <AvatarInitials name={doctorLabel} size="lg" />
            <div className="min-w-0">
              <h2 className="truncate text-xl font-bold tracking-tight">{doctorLabel}</h2>
              <p className="mt-0.5 truncate text-sm text-brand-100">
                {doctor.specialization} · {doctor.departmentName}
              </p>
              <div className="mt-2">
                <StatusTag
                  status={doctor.isAvailable ? 'ONLINE' : 'OFFLINE'}
                  className="!bg-white/15 !border-white/25 !text-white"
                />
              </div>
            </div>
          </div>
        </div>

        {/* Body */}
        <div className="flex-1 space-y-5 overflow-y-auto p-6">
          {/* Full record */}
          <div>
            <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-500">
              Doctor details
            </h3>
            <dl className="overflow-hidden rounded-2xl border border-slate-100 dark:border-slate-700/50">
              {details.map((row, i) => (
                <div
                  key={row.label}
                  className={`flex items-center justify-between gap-4 px-4 py-2.5 text-sm ${
                    i % 2 === 1 ? 'bg-slate-50/60 dark:bg-night-700/40' : 'bg-white dark:bg-night-800'
                  }`}
                >
                  <dt className="shrink-0 text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">
                    {row.label}
                  </dt>
                  <dd
                    className={`truncate text-right font-medium ${
                      row.label === 'User ID' ? 'font-mono text-xs text-slate-400 dark:text-slate-500' : 'text-slate-800 dark:text-slate-100'
                    }`}
                  >
                    {row.value}
                  </dd>
                </div>
              ))}
            </dl>
          </div>

          {/* Live queue */}
          <div>
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Live queue
              </h3>
              {!isLoading && !isError && (
                <div className="flex items-center gap-2 text-xs text-slate-400 dark:text-slate-500">
                  <LiveBadge lastUpdatedSeconds={secondsAgo} />
                  <Stethoscope className="h-3.5 w-3.5" />
                </div>
              )}
            </div>

            {isLoading ? (
              <SectionLoading label="Loading queue…" />
            ) : isError ? (
              <SectionError message={getErrorMessage(error)} onRetry={refetch} />
            ) : entries.length === 0 ? (
              <SectionEmpty
                title="No patients in queue"
                message="New patients will appear here as they join with AI triage."
              />
            ) : (
              <div className="space-y-3">
                {/* Quick stats */}
                <div className="grid grid-cols-3 gap-2">
                  <div className="rounded-xl bg-brand-50 p-3 text-center dark:bg-brand-500/15">
                    <p className="text-xl font-extrabold text-brand-700 tabular-nums dark:text-brand-300">{waiting.length}</p>
                    <p className="text-[10px] font-semibold uppercase tracking-wide text-brand-600 dark:text-brand-400">
                      Waiting
                    </p>
                  </div>
                  <div className="rounded-xl bg-violet-50 p-3 text-center dark:bg-violet-500/15">
                    <p className="text-xl font-extrabold text-violet-700 tabular-nums dark:text-violet-300">{inProgress.length}</p>
                    <p className="text-[10px] font-semibold uppercase tracking-wide text-violet-600 dark:text-violet-400">
                      In consultation
                    </p>
                  </div>
                  <div className="rounded-xl bg-amber-50 p-3 text-center dark:bg-amber-500/15">
                    <p className="text-xl font-extrabold text-amber-700 tabular-nums dark:text-amber-300">{longestWait} min</p>
                    <p className="text-[10px] font-semibold uppercase tracking-wide text-amber-600 dark:text-amber-400">
                      Longest wait
                    </p>
                  </div>
                </div>

                {/* Patient list */}
                <ul className="divide-y divide-slate-50 overflow-hidden rounded-2xl border border-slate-100 dark:divide-slate-700/40 dark:border-slate-700/50">
                  {entries.map((entry) => (
                    <li key={entry.id} className="flex items-center gap-3 px-4 py-3">
                      <div
                        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-xl text-sm font-extrabold tabular-nums ${
                          entry.status === 'IN_PROGRESS' ? 'bg-brand-600 text-white' : 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300'
                        }`}
                      >
                        {entry.position ?? '—'}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">
                            {displayName(entry)}
                          </p>
                          <StatusTag status={entry.effectiveTriage} />
                        </div>
                        <p className="mt-0.5 flex items-center gap-1 truncate text-xs text-slate-400 dark:text-slate-500">
                          <Clock className="h-3 w-3 shrink-0" />
                          {entry.status === 'IN_PROGRESS'
                            ? 'In consultation now'
                            : `≈ ${entry.predictedWaitMinutes ?? 0} min wait`}
                          {entry.symptomText && ` · ${entry.symptomText.slice(0, 40)}${entry.symptomText.length > 40 ? '…' : ''}`}
                        </p>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
