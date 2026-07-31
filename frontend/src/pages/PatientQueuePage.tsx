import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  api,
  type DoctorCatalogResponse,
  type QueueEntryResponse,
} from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import TriageBadge from '../components/TriageBadge';

const POLL_INTERVAL_MS = 10_000;

// ─── Small building blocks ────────────────────────────────────────────

function Spinner() {
  return (
    <div className="flex flex-col items-center justify-center py-24">
      <div className="h-10 w-10 animate-spin rounded-full border-4 border-brand-200 border-t-brand-600" />
      <p className="mt-4 text-sm text-ink-muted">Checking your queue status…</p>
    </div>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="card mx-auto max-w-lg p-8 text-center">
      <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-red-100 text-2xl">
        ⚠️
      </div>
      <h2 className="mt-4 text-lg font-bold text-ink">Something went wrong</h2>
      <p className="mt-1 text-sm text-ink-muted">{message}</p>
      <button onClick={onRetry} className="btn-primary mt-6">
        Try again
      </button>
    </div>
  );
}

function StatusPill({ status }: { status: QueueEntryResponse['status'] }) {
  const config = {
    WAITING: 'bg-sky-100 text-sky-700 border-sky-200',
    IN_PROGRESS: 'bg-violet-100 text-violet-700 border-violet-200',
    COMPLETED: 'bg-emerald-100 text-emerald-700 border-emerald-200',
    CANCELLED: 'bg-slate-100 text-slate-500 border-slate-200',
  } as const;
  return (
    <span className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold ${config[status]}`}>
      {status === 'WAITING' && 'In queue'}
      {status === 'IN_PROGRESS' && 'Consultation in progress'}
      {status === 'COMPLETED' && 'Completed'}
      {status === 'CANCELLED' && 'Cancelled'}
    </span>
  );
}

// ─── Live status view (the flagship screen) ───────────────────────────

function LiveStatusView({
  entry,
  lastUpdatedAt,
  isStale,
  onRefresh,
}: {
  entry: QueueEntryResponse;
  lastUpdatedAt: number;
  isStale: boolean;
  onRefresh: () => void;
}) {
  const isInProgress = entry.status === 'IN_PROGRESS';
  const isCompleted = entry.status === 'COMPLETED';
  const secondsAgo = Math.max(0, Math.round((Date.now() - lastUpdatedAt) / 1000));

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      {/* Live indicator */}
      <div className="flex items-center justify-between px-1">
        <div className="flex items-center gap-2 text-xs font-medium text-ink-muted">
          <span className="h-2.5 w-2.5 animate-pulse-dot rounded-full bg-brand-500" />
          <span>Live — updating automatically every 10s</span>
        </div>
        <span className="text-xs font-medium text-ink-muted">Last updated {secondsAgo}s ago</span>
      </div>

      {isStale && (
        <div className="card flex items-center justify-between gap-3 border-amber-200 bg-amber-50 px-4 py-3">
          <p className="text-sm font-medium text-amber-700">
            ⚠ Showing the last known status — a refresh just failed.
          </p>
          <button onClick={onRefresh} className="btn-secondary !py-1.5 text-xs">
            Retry
          </button>
        </div>
      )}

      {/* Hero card */}
      <div
        className={`relative overflow-hidden rounded-3xl border border-brand-100 bg-gradient-to-br from-brand-600 to-brand-800 p-7 text-white shadow-lift ${
          isInProgress ? 'animate-soft-pulse' : ''
        }`}
      >
        <div className="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rounded-full bg-white/10" />
        <div className="pointer-events-none absolute -bottom-16 -left-10 h-48 w-48 rounded-full bg-white/5" />

        <div className="relative flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-brand-100">
              {entry.specialization} · {entry.departmentName}
            </p>
            <h2 className="mt-1 text-lg font-bold">
              {isCompleted
                ? 'Consultation complete'
                : isInProgress
                  ? "It's your turn!"
                  : 'Your position in queue'}
            </h2>
          </div>
          <TriageBadge level={entry.effectiveTriage} className="!bg-white/15 !border-white/25 !text-white" />
        </div>

        <div className="relative mt-6 flex items-end gap-8">
          {!isCompleted && (
            <div>
              <div className="text-6xl font-extrabold leading-none tracking-tight">
                {isInProgress ? '→' : entry.position ?? '—'}
              </div>
              <p className="mt-2 text-sm font-medium text-brand-100">
                {isInProgress ? 'Called — please head to the doctor' : 'Position in queue'}
              </p>
            </div>
          )}
          <div>
            <div className="text-5xl font-extrabold leading-none tracking-tight">
              {isCompleted ? '✓' : isInProgress ? 'Now' : `${entry.predictedWaitMinutes ?? 0} min`}
            </div>
            <p className="mt-2 text-sm font-medium text-brand-100">
              {isCompleted ? 'All done — take care!' : isInProgress ? 'Being attended to' : 'Estimated wait'}
            </p>
          </div>
        </div>

        {!isCompleted && (
          <div className="relative mt-7">
            <div className="flex items-center justify-between text-[11px] font-semibold uppercase tracking-wide text-brand-100">
              <span className={entry.status === 'WAITING' ? 'text-white' : ''}>Joined</span>
              <span className={entry.status === 'IN_PROGRESS' ? 'text-white' : ''}>Called</span>
              <span>Completed</span>
            </div>
            <div className="mt-2 flex items-center">
              <div className="flex-1">
                <div className={`h-1.5 rounded-full ${entry.status === 'WAITING' ? 'bg-white' : 'bg-white/25'}`} />
              </div>
              <div className="flex-1">
                <div className={`h-1.5 rounded-full ${entry.status === 'IN_PROGRESS' ? 'bg-white' : 'bg-white/25'}`} />
              </div>
              <div className="flex-1">
                <div className="h-1.5 rounded-full bg-white/25" />
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Detail card */}
      <div className="card space-y-4 p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <StatusPill status={entry.status} />
          <p className="text-xs text-ink-muted">
            Joined at {new Date(entry.joinedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </p>
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Symptoms reported</p>
          <p className="mt-1.5 rounded-xl bg-surface p-3.5 text-sm leading-relaxed text-ink">{entry.symptomText}</p>
        </div>
        <div className="grid gap-3 text-sm sm:grid-cols-2">
          <div className="rounded-xl bg-surface p-3.5">
            <p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">AI triage</p>
            <p className="mt-1 font-semibold text-ink">{entry.aiSuggestedTriage}</p>
          </div>
          <div className="rounded-xl bg-surface p-3.5">
            <p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">
              Doctor's final decision
            </p>
            <p className="mt-1 font-semibold text-ink">{entry.doctorOverrideTriage ?? 'No override yet'}</p>
          </div>
        </div>
        <button
          onClick={onRefresh}
          className="btn-secondary w-full sm:w-auto"
          title="Fetch the latest status"
        >
          ↻ Refresh now
        </button>
      </div>
    </div>
  );
}

// ─── Join flow ─────────────────────────────────────────────────────────

function JoinFlow({
  initialDoctorId,
  onJoined,
}: {
  initialDoctorId: number | null;
  onJoined: (entry: QueueEntryResponse) => void;
}) {
  const { user } = useAuth();
  const [doctors, setDoctors] = useState<DoctorCatalogResponse[]>([]);
  const [selectedDoctor, setSelectedDoctor] = useState<string>(initialDoctorId ? String(initialDoctorId) : '');
  const [symptomText, setSymptomText] = useState('');
  const [isLoadingDoctors, setIsLoadingDoctors] = useState(true);
  const [isJoining, setIsJoining] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const data = await api.getDoctors();
        setDoctors(data.filter((d) => d.isAvailable));
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to load doctors');
      } finally {
        setIsLoadingDoctors(false);
      }
    })();
  }, []);

  const handleJoin = async () => {
    if (!selectedDoctor || !symptomText.trim()) {
      setError('Please choose a doctor and describe your symptoms.');
      return;
    }
    setIsJoining(true);
    setError('');
    setSuccess('');
    try {
      const entry = await api.joinQueue({
        doctorCatalogEntryId: Number(selectedDoctor),
        symptomText: symptomText.trim(),
      });
      setSuccess('Queue joined successfully — AI triage complete.');
      setTimeout(() => onJoined(entry), 900);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to join the queue');
      setIsJoining(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <div className="card p-6">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-100 text-xl">
            🩺
          </div>
          <div>
            <h2 className="text-lg font-bold text-ink">Join a queue</h2>
            <p className="text-sm text-ink-muted">
              Pick a doctor, describe your symptoms, and our AI will triage your priority instantly.
            </p>
          </div>
        </div>

        {success && (
          <div className="mt-5 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
            ✓ {success}
          </div>
        )}
        {error && (
          <div className="mt-5 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            ⚠ {error}
          </div>
        )}

        <div className="mt-6 space-y-5">
          <div>
            <label className="mb-1.5 block text-sm font-semibold text-ink">Doctor</label>
            {isLoadingDoctors ? (
              <div className="h-11 animate-pulse rounded-xl bg-slate-100" />
            ) : (
              <select
                value={selectedDoctor}
                onChange={(e) => setSelectedDoctor(e.target.value)}
                className="select-field"
              >
                <option value="">Select a doctor…</option>
                {doctors.map((doc) => (
                  <option key={doc.id} value={doc.id}>
                    {doc.specialization} — {doc.departmentName} (≈{doc.avgConsultationTimeMinutes} min)
                  </option>
                ))}
              </select>
            )}
            {!isLoadingDoctors && doctors.length === 0 && (
              <p className="mt-2 text-xs text-ink-muted">
                No doctors are currently accepting new patients.
              </p>
            )}
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-ink">Symptoms</label>
            <textarea
              value={symptomText}
              onChange={(e) => setSymptomText(e.target.value)}
              rows={4}
              maxLength={2000}
              placeholder="Describe what you're experiencing, e.g. 'Severe chest pain radiating to my left arm for the past hour'"
              className="input-field resize-none"
            />
            <p className="mt-1 text-right text-xs text-ink-muted">{symptomText.length}/2000</p>
          </div>

          <button onClick={handleJoin} disabled={isJoining} className="btn-primary w-full py-3 text-base">
            {isJoining ? 'Running AI triage…' : 'Join Queue →'}
          </button>
          <p className="text-center text-xs text-ink-muted">
            {user?.fullName} · Your position & estimated wait will update live once you're in.
          </p>
        </div>
      </div>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────

export default function PatientQueuePage() {
  const [searchParams] = useSearchParams();
  const initialDoctorId = searchParams.get('doctor') ? Number(searchParams.get('doctor')) : null;

  const [status, setStatus] = useState<{ active: boolean; entry: QueueEntryResponse | null } | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [lastUpdatedAt, setLastUpdatedAt] = useState(Date.now());
  const [refreshKey, setRefreshKey] = useState(0);
  const lastUpdatedRef = useRef(lastUpdatedAt);

  const fetchStatus = useCallback(async () => {
    try {
      const data = await api.getMyQueueStatus();
      setStatus(data);
      lastUpdatedRef.current = Date.now();
      setLastUpdatedAt(lastUpdatedRef.current);
      setError('');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to fetch queue status');
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Initial load + manual refresh
  useEffect(() => {
    fetchStatus();
  }, [fetchStatus, refreshKey]);

  // Poll every 10s — polling only, no WebSockets (decided Phase 2).
  useEffect(() => {
    const interval = setInterval(fetchStatus, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [fetchStatus]);

  const handleJoined = (entry: QueueEntryResponse) => {
    setStatus({ active: true, entry });
    lastUpdatedRef.current = Date.now();
    setLastUpdatedAt(lastUpdatedRef.current);
  };

  return (
    <div className="min-h-screen bg-surface px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="👤"
          title="My Queue"
          subtitle="Live position, estimated wait & AI triage"
          dashboardPath="/patient"
        />

        {isLoading ? (
          <Spinner />
        ) : error && !status ? (
          <ErrorState message={error} onRetry={() => setRefreshKey((k) => k + 1)} />
        ) : status && status.active && status.entry ? (
          <LiveStatusView
            entry={status.entry}
            lastUpdatedAt={lastUpdatedAt}
            isStale={!!error}
            onRefresh={() => setRefreshKey((k) => k + 1)}
          />
        ) : (
          <JoinFlow initialDoctorId={initialDoctorId} onJoined={handleJoined} />
        )}

        <footer className="pt-4 text-center text-xs text-ink-muted">
          CareQ — SmartOPD AI | AI wait-time prediction & symptom triage
        </footer>
      </div>
    </div>
  );
}
