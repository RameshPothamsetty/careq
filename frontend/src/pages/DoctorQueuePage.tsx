import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import {
  api,
  type DoctorCatalogResponse,
  type QueueEntryResponse,
  type TriageLevel,
} from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import TriageBadge from '../components/TriageBadge';

const POLL_INTERVAL_MS = 10_000;
const OVERRIDE_OPTIONS: TriageLevel[] = ['EMERGENCY', 'HIGH', 'NORMAL', 'FOLLOW_UP'];

function Spinner() {
  return (
    <div className="flex flex-col items-center justify-center py-24">
      <div className="h-10 w-10 animate-spin rounded-full border-4 border-brand-200 border-t-brand-600" />
      <p className="mt-4 text-sm text-ink-muted">Loading your live queue…</p>
    </div>
  );
}

function EmptyQueue() {
  return (
    <div className="card mx-auto max-w-lg p-12 text-center">
      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-brand-50 text-3xl">
        🍃
      </div>
      <h2 className="mt-4 text-lg font-bold text-ink">No patients in queue right now</h2>
      <p className="mt-1.5 text-sm text-ink-muted">
        New patients will appear here automatically as they join with AI triage.
      </p>
    </div>
  );
}

function Toast({ message, tone }: { message: string; tone: 'success' | 'error' }) {
  if (!message) return null;
  return (
    <div
      className={`flex items-center gap-2 rounded-xl border px-4 py-3 text-sm font-medium ${
        tone === 'success'
          ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
          : 'border-red-200 bg-red-50 text-red-700'
      }`}
    >
      {tone === 'success' ? '✓' : '⚠'} {message}
    </div>
  );
}

function shortId(patientId: string) {
  return `#${patientId.slice(0, 4).toUpperCase()}`;
}

export default function DoctorQueuePage() {
  const { user } = useAuth();
  const [doctorEntry, setDoctorEntry] = useState<DoctorCatalogResponse | null>(null);
  const [queue, setQueue] = useState<QueueEntryResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [toast, setToast] = useState<{ message: string; tone: 'success' | 'error' } | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(Date.now());
  const [refreshKey, setRefreshKey] = useState(0);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = (message: string, tone: 'success' | 'error' = 'success') => {
    setToast({ message, tone });
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 4000);
  };

  const fetchQueue = useCallback(async () => {
    try {
      // Find this doctor's catalog entry by userId (same pattern as DoctorDashboard).
      let myEntry = doctorEntry;
      if (!myEntry) {
        const doctors = await api.getDoctors();
        myEntry = doctors.find((d) => d.userId === user?.id) ?? null;
        if (myEntry) setDoctorEntry(myEntry);
        else {
          setError('No doctor catalog entry found for your account. Ask an admin to create one.');
          setIsLoading(false);
          return;
        }
      }
      const data = await api.getDoctorQueue(myEntry.id);
      setQueue(data);
      setLastUpdatedAt(Date.now());
      setError('');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load your queue');
    } finally {
      setIsLoading(false);
    }
  }, [user?.id, doctorEntry]);

  useEffect(() => {
    fetchQueue();
  }, [fetchQueue, refreshKey]);

  useEffect(() => {
    const interval = setInterval(fetchQueue, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [fetchQueue]);

  const firstWaiting = queue.find((e) => e.status === 'WAITING');
  const inProgress = queue.filter((e) => e.status === 'IN_PROGRESS');
  const secondsAgo = Math.max(0, Math.round((Date.now() - lastUpdatedAt) / 1000));

  const runAction = async (
    action: () => Promise<unknown>,
    successMessage: string,
    id: number,
  ) => {
    setBusyId(id);
    setError('');
    try {
      await action();
      await fetchQueue();
      showToast(successMessage);
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : 'Action failed', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleCallNext = (entry: QueueEntryResponse) =>
    runAction(() => api.callNext(entry.id), `Called ${shortId(entry.patientId)} — consultation started`, entry.id);

  const handleComplete = (entry: QueueEntryResponse) =>
    runAction(() => api.completeQueueEntry(entry.id), `Completed ${shortId(entry.patientId)} — queue advanced`, entry.id);

  const handleOverride = (entry: QueueEntryResponse, level: TriageLevel) =>
    runAction(() => api.overrideTriage(entry.id, { triageLevel: level }), `Triage updated to ${level}`, entry.id);

  const stat = (label: string, value: number | string, accent = 'text-ink') => (
    <div className="card px-5 py-4 text-center">
      <div className={`text-2xl font-extrabold tracking-tight ${accent}`}>{value}</div>
      <div className="mt-0.5 text-[11px] font-semibold uppercase tracking-wider text-ink-muted">{label}</div>
    </div>
  );

  return (
    <div className="min-h-screen bg-surface px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="🩺"
          title="Live Patient Queue"
          subtitle={doctorEntry ? `${doctorEntry.specialization} · ${doctorEntry.departmentName}` : 'Your queue'}
          dashboardPath="/doctor"
        />

        {/* Stats row */}
        {!isLoading && !error && (
          <div className="grid grid-cols-3 gap-3 sm:gap-4">
            {stat('Waiting', queue.filter((e) => e.status === 'WAITING').length, 'text-brand-700')}
            {stat('In consultation', inProgress.length, 'text-violet-600')}
            {stat('Total in queue', queue.length, 'text-emerald-600')}
          </div>
        )}

        {/* Live indicator */}
        {!isLoading && !error && (
          <div className="flex items-center justify-between px-1">
            <div className="flex items-center gap-2 text-xs font-medium text-ink-muted">
              <span className="h-2.5 w-2.5 animate-pulse-dot rounded-full bg-brand-500" />
              <span>Live queue — polling every 10s</span>
            </div>
            <span className="text-xs font-medium text-ink-muted">Last updated {secondsAgo}s ago</span>
          </div>
        )}

        <Toast message={toast?.message ?? ''} tone={toast?.tone ?? 'success'} />

        {error && (
          <div className="card p-6">
            <div className="flex items-center gap-2 text-sm font-medium text-red-700">
              ⚠ {error}
            </div>
            <button onClick={() => setRefreshKey((k) => k + 1)} className="btn-primary mt-4">
              Retry
            </button>
          </div>
        )}

        {/* Call next action */}
        {!isLoading && !error && firstWaiting && (
          <div className="card flex flex-col items-center justify-between gap-3 border-brand-100 bg-gradient-to-r from-brand-50 to-white p-5 sm:flex-row">
            <div>
              <p className="text-sm font-semibold text-ink">Next patient ready</p>
              <p className="text-xs text-ink-muted">
                {shortId(firstWaiting.patientId)} · {firstWaiting.symptomText.slice(0, 60)}
                {firstWaiting.symptomText.length > 60 ? '…' : ''}
              </p>
            </div>
            <button
              onClick={() => handleCallNext(firstWaiting)}
              disabled={busyId === firstWaiting.id}
              className="btn-primary shrink-0 px-6"
            >
              {busyId === firstWaiting.id ? 'Calling…' : 'Call Next →'}
            </button>
          </div>
        )}

        {isLoading ? (
          <Spinner />
        ) : !error && queue.filter((e) => e.status !== 'COMPLETED').length === 0 ? (
          <EmptyQueue />
        ) : (
          !error && (
            <div className="space-y-3">
              {queue
                .filter((e) => e.status !== 'COMPLETED')
                .map((entry, idx) => {
                  const isTop = entry.status === 'IN_PROGRESS' || idx === 0;
                  return (
                    <div
                      key={entry.id}
                      className={`card animate-fade-in-up p-5 transition-all duration-200 hover:shadow-lift ${
                        entry.status === 'IN_PROGRESS' ? 'border-violet-200 bg-violet-50/40' : ''
                      }`}
                      style={{ animationDelay: `${idx * 60}ms` }}
                    >
                      <div className="flex flex-col gap-4 lg:flex-row lg:items-center">
                        {/* Position + identity */}
                        <div className="flex min-w-[190px] items-center gap-4">
                          <div
                            className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl text-lg font-extrabold ${
                              isTop ? 'bg-brand-600 text-white' : 'bg-brand-50 text-brand-700'
                            }`}
                          >
                            {entry.position ?? '—'}
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <p className="font-bold text-ink">{shortId(entry.patientId)}</p>
                              <TriageBadge level={entry.effectiveTriage} />
                            </div>
                            <p className="mt-0.5 text-xs text-ink-muted">
                              Wait ≈ {entry.predictedWaitMinutes ?? 0} min · AI: {entry.aiSuggestedTriage}
                              {entry.doctorOverrideTriage ? ` → overridden to ${entry.doctorOverrideTriage}` : ''}
                            </p>
                          </div>
                        </div>

                        {/* Symptoms */}
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Symptoms</p>
                          <p className="mt-1 truncate text-sm text-ink">{entry.symptomText}</p>
                        </div>

                        {/* Actions */}
                        <div className="flex flex-wrap items-center gap-2 lg:justify-end">
                          {entry.status === 'WAITING' ? (
                            <>
                              <select
                                value={entry.effectiveTriage}
                                onChange={(e) => handleOverride(entry, e.target.value as TriageLevel)}
                                disabled={busyId === entry.id}
                                className="select-field !w-auto !py-1.5 text-xs font-semibold"
                                title="Override AI triage (doctor's decision is final)"
                              >
                                {OVERRIDE_OPTIONS.map((lvl) => (
                                  <option key={lvl} value={lvl}>
                                    Set: {lvl}
                                  </option>
                                ))}
                              </select>
                              <button
                                onClick={() => handleCallNext(entry)}
                                disabled={busyId === entry.id}
                                className="btn-primary !py-1.5 text-xs"
                              >
                                {busyId === entry.id ? '…' : 'Call Next'}
                              </button>
                            </>
                          ) : (
                            <button
                              onClick={() => handleComplete(entry)}
                              disabled={busyId === entry.id}
                              className="btn-primary !bg-emerald-600 !py-1.5 text-xs hover:!bg-emerald-700"
                            >
                              {busyId === entry.id ? '…' : 'Complete ✓'}
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
            </div>
          )
        )}

        <footer className="pt-4 text-center text-xs text-ink-muted">
          CareQ — SmartOPD AI | Doctor live queue · color-coded AI triage
        </footer>
      </div>
    </div>
  );
}
