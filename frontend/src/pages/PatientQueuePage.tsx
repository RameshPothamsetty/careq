import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { HeartPulse, RefreshCw, UserRound } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useGetDoctorsQuery } from '../services/rtk/doctorApi';
import { useGetMyQueueStatusQuery, useJoinQueueMutation } from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import type { QueueEntryResponse } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import { LiveBadge, StatusTag, Button } from '../components/ui';
import { LoadingState, ErrorState } from '../components/ui/States';

const POLL_INTERVAL_MS = 10_000;

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
        <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
          <LiveBadge lastUpdatedSeconds={secondsAgo} />
          <span>Polling every 10s</span>
        </div>
        {isStale && (
          <Button variant="secondary" onClick={onRefresh} className="!py-1 text-xs">
            <RefreshCw className="h-3.5 w-3.5" />
            Retry
          </Button>
        )}
      </div>

      {isStale && (
        <div className="card flex items-center justify-between gap-3 border-amber-200 bg-amber-50 px-4 py-3">
          <p className="text-sm font-medium text-amber-700">
            ⚠ Showing the last known status — a refresh just failed.
          </p>
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
          <StatusTag status={entry.effectiveTriage} className="!bg-white/15 !border-white/25 !text-white" />
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
          <StatusTag status={entry.status} />
          <p className="text-xs text-slate-400">
            Joined at {new Date(entry.joinedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </p>
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Symptoms reported</p>
          <p className="mt-1.5 rounded-xl bg-gray-50 p-3.5 text-sm leading-relaxed text-slate-800">{entry.symptomText}</p>
        </div>
        <div className="grid gap-3 text-sm sm:grid-cols-2">
          <div className="rounded-xl bg-gray-50 p-3.5">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">AI triage</p>
            <p className="mt-1 font-semibold text-slate-800">{entry.aiSuggestedTriage}</p>
          </div>
          <div className="rounded-xl bg-gray-50 p-3.5">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
              Doctor's final decision
            </p>
            <p className="mt-1 font-semibold text-slate-800">{entry.doctorOverrideTriage ?? 'No override yet'}</p>
          </div>
        </div>
        <Button variant="secondary" onClick={onRefresh} className="w-full sm:w-auto" title="Fetch the latest status">
          <RefreshCw className="h-4 w-4" />
          Refresh now
        </Button>
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
  const { data: allDoctors, isLoading: isLoadingDoctors, error: doctorsError } = useGetDoctorsQuery();
  const [joinQueue, { isLoading: isJoining }] = useJoinQueueMutation();
  const [selectedDoctor, setSelectedDoctor] = useState<string>(initialDoctorId ? String(initialDoctorId) : '');
  const [symptomText, setSymptomText] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const doctors = allDoctors?.filter((d) => d.isAvailable) ?? [];
  const loadError = doctorsError ? getErrorMessage(doctorsError) : '';

  const handleJoin = async () => {
    if (!selectedDoctor || !symptomText.trim()) {
      setError('Please choose a doctor and describe your symptoms.');
      return;
    }
    setError('');
    setSuccess('');
    try {
      const entry = await joinQueue({
        doctorCatalogEntryId: Number(selectedDoctor),
        symptomText: symptomText.trim(),
      }).unwrap();
      setSuccess('Queue joined successfully — AI triage complete.');
      setTimeout(() => onJoined(entry), 900);
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <div className="card p-6">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-100">
            <HeartPulse className="h-5 w-5 text-brand-700" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-slate-800">Join a queue</h2>
            <p className="text-sm text-slate-500">
              Pick a doctor, describe your symptoms, and our AI will triage your priority instantly.
            </p>
          </div>
        </div>

        {success && (
          <div className="mt-5 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
            ✓ {success}
          </div>
        )}
        {(loadError || error) && (
          <div className="mt-5 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            ⚠ {loadError || error}
          </div>
        )}

        <div className="mt-6 space-y-5">
          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">Doctor</label>
            {isLoadingDoctors ? (
              <div className="h-11 animate-pulse rounded-xl bg-gray-100" />
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
              <p className="mt-2 text-xs text-slate-400">
                No doctors are currently accepting new patients.
              </p>
            )}
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">Symptoms</label>
            <textarea
              value={symptomText}
              onChange={(e) => setSymptomText(e.target.value)}
              rows={4}
              maxLength={2000}
              placeholder="Describe what you're experiencing, e.g. 'Severe chest pain radiating to my left arm for the past hour'"
              className="input-field resize-none"
            />
            <p className="mt-1 text-right text-xs text-slate-400">{symptomText.length}/2000</p>
          </div>

          <Button onClick={handleJoin} loading={isJoining} className="w-full py-3 text-base">
            {isJoining ? 'Running AI triage…' : 'Join Queue →'}
          </Button>
          <p className="text-center text-xs text-slate-400">
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

  const [joinedEntry, setJoinedEntry] = useState<QueueEntryResponse | null>(null);
  const joinedAtRef = useRef(0);

  const {
    data: status,
    isLoading,
    isError,
    error,
    refetch,
    fulfilledTimeStamp,
  } = useGetMyQueueStatusQuery(undefined, { pollingInterval: POLL_INTERVAL_MS });

  const handleJoined = (entry: QueueEntryResponse) => {
    joinedAtRef.current = Date.now();
    setJoinedEntry(entry);
  };

  // The polled status is authoritative once it reflects the join (its last
  // successful fetch is newer than when we joined). Until then — right after
  // joining, or if the initial status fetch failed — show the entry the join
  // response returned so the screen never flashes back to the join form. Once
  // the server confirms there is no active entry, the optimistic entry is
  // dropped (e.g. after the consultation completes).
  const serverConfirmed = (fulfilledTimeStamp ?? 0) >= joinedAtRef.current;
  const liveEntry =
    status?.active && status.entry
      ? status.entry
      : joinedEntry && !serverConfirmed
        ? joinedEntry
        : null;

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon={<UserRound className="h-6 w-6 text-white" />}
          title="My Queue"
          subtitle="Live position, estimated wait & AI triage"
          dashboardPath="/patient"
        />

        {isLoading && !liveEntry ? (
          <LoadingState label="Checking your queue status…" />
        ) : isError && !status ? (
          <ErrorState message={getErrorMessage(error)} onRetry={refetch} />
        ) : liveEntry ? (
          <LiveStatusView
            entry={liveEntry}
            lastUpdatedAt={fulfilledTimeStamp ?? Date.now()}
            isStale={isError && !!status}
            onRefresh={refetch}
          />
        ) : (
          <JoinFlow initialDoctorId={initialDoctorId} onJoined={handleJoined} />
        )}

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | AI wait-time prediction & symptom triage
        </footer>
      </div>
    </div>
  );
}
