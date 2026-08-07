import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { HeartPulse, RefreshCw, Sparkles, Wallet, Award, Clock, UserRound } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useGetDoctorsQuery } from '../services/rtk/doctorApi';
import {
  useGetMyQueueStatusQuery,
  useJoinQueueMutation,
  useCancelQueueEntryMutation,
  useAutoAssignMutation,
} from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import type { QueueEntryResponse, AutoAssignResponse } from '../services/api';
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
  onCancel,
  isCancelling,
  cancelError,
}: {
  entry: QueueEntryResponse;
  lastUpdatedAt: number;
  isStale: boolean;
  onRefresh: () => void;
  onCancel: () => void;
  isCancelling: boolean;
  cancelError: string;
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
        {cancelError && (
          <p className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm font-medium text-red-700">
            ⚠ {cancelError}
          </p>
        )}
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={onRefresh} className="w-full sm:w-auto" title="Fetch the latest status">
            <RefreshCw className="h-4 w-4" />
            Refresh now
          </Button>
          {entry.status === 'WAITING' && (
            <Button
              variant="danger"
              onClick={onCancel}
              loading={isCancelling}
              className="w-full sm:w-auto"
              title="Leave the queue before being seen — your spot will be released"
            >
              Leave queue
            </Button>
          )}
        </div>
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
  // A single generous page keeps every available doctor in the dropdown.
  const { data: allDoctors, isLoading: isLoadingDoctors, error: doctorsError } = useGetDoctorsQuery({ size: 100 });
  const [joinQueue, { isLoading: isJoining }] = useJoinQueueMutation();
  const [autoAssign, { isLoading: isAutoAssigning }] = useAutoAssignMutation();
  const [selectedDoctor, setSelectedDoctor] = useState<string>(initialDoctorId ? String(initialDoctorId) : '');
  const [symptomText, setSymptomText] = useState('');
  const [suggestionResponse, setSuggestionResponse] = useState<AutoAssignResponse | null>(null);
  // Browsing already chose a doctor (?doctor=id) → open the manual picker.
  const [showManualPicker, setShowManualPicker] = useState<boolean>(initialDoctorId !== null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const doctors = (allDoctors?.content ?? []).filter((d) => d.isAvailable);
  const loadError = doctorsError ? getErrorMessage(doctorsError) : '';

  // Phase 2 — "describe and done": the AI picks the single best doctor and
  // joins that queue automatically. When the symptoms are ambiguous (or no
  // doctor is available) nothing is joined and the top candidates are shown
  // for the patient to confirm.
  const handleAutoJoin = async () => {
    if (!symptomText.trim()) {
      setError('Please describe your symptoms first — the AI matches you with the right doctor.');
      return;
    }
    setError('');
    setSuccess('');
    setSuggestionResponse(null);
    setSelectedDoctor('');
    try {
      const result = await autoAssign({
        symptomText: symptomText.trim(),
        // Captured so the doctor's live queue can show real patient names (Day 7a).
        patientName: user?.fullName || undefined,
      }).unwrap();
      const entry = result.entry;
      if (result.assigned && entry) {
        setSuccess(result.message ?? 'You\'ve been matched — joining the queue…');
        setTimeout(() => onJoined(entry), 1200);
      } else {
        // Ambiguous / no available doctors → offer the candidates to confirm.
        setSuggestionResponse(result);
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    }
  };

  const handleJoin = async (doctorId?: string) => {
    const targetDoctor = doctorId ?? selectedDoctor;
    if (!targetDoctor || !symptomText.trim()) {
      setError('Please choose a doctor and describe your symptoms.');
      return;
    }
    setError('');
    setSuccess('');
    try {
      const entry = await joinQueue({
        doctorCatalogEntryId: Number(targetDoctor),
        symptomText: symptomText.trim(),
        // Captured so the doctor's live queue can show real patient names (Day 7a).
        patientName: user?.fullName || undefined,
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
              Describe your symptoms — the AI matches you with the right doctor, triages your priority and joins
              instantly.
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
          {/* Symptoms first — the AI matches the right doctor (Phase 1) */}
          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">Symptoms</label>
            <textarea
              value={symptomText}
              onChange={(e) => {
                setSymptomText(e.target.value);
                if (suggestionResponse) setSuggestionResponse(null);
              }}
              rows={4}
              maxLength={2000}
              placeholder="Describe what you're experiencing, e.g. 'Persistent headache with blurred vision for two days'"
              className="input-field resize-none"
            />
            <p className="mt-1 text-right text-xs text-slate-400">{symptomText.length}/2000</p>
          </div>

          {/* The primary action: describe and done. The AI joins the single
              best doctor; ambiguous symptoms fall back to a manual pick. */}
          <Button onClick={handleAutoJoin} loading={isAutoAssigning} className="w-full py-3 text-base">
            <Sparkles className="h-4 w-4" />
            {isAutoAssigning ? 'Matching you with the best doctor…' : 'Auto-join — the AI picks the best doctor'}
          </Button>

          {/* Always reachable manual path — no AI step required (e.g. the
              patient already knows their doctor) */}
          {!showManualPicker && (
            <button
              type="button"
              onClick={() => setShowManualPicker(true)}
              className="block w-full text-center text-xs font-semibold text-brand-600 hover:text-brand-700"
            >
              Prefer to pick yourself? Choose a doctor manually →
            </button>
          )}

          {/* AI result: either the patient must confirm (ambiguous / none),
              or the assigned confirmation is shown by the success banner */}
          {suggestionResponse && (
            <div className="space-y-3">
              {suggestionResponse.reason === 'AMBIGUOUS_SYMPTOMS' && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-700">
                  ⚠ {suggestionResponse.message ??
                    "Your symptoms don't clearly point to one specialty — please pick a doctor below."}
                </div>
              )}
              {suggestionResponse.reason === 'NO_AVAILABLE_DOCTORS' && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-700">
                  ⚠ {suggestionResponse.message ??
                    'No doctors are currently accepting new patients — please try again shortly.'}
                </div>
              )}

              <div className="flex flex-wrap items-center gap-2 rounded-xl bg-brand-50 px-4 py-3">
                <Sparkles className="h-4 w-4 text-brand-700" />
                <span className="text-sm font-semibold text-brand-800">
                  {suggestionResponse.suggestedDepartment
                    ? `AI suggests ${suggestionResponse.suggestedDepartment}`
                    : 'AI analysis'}
                </span>
                <StatusTag status={suggestionResponse.triageLevel} />
              </div>

              {suggestionResponse.emergency && suggestionResponse.urgencyNote && (
                <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
                  ⚠ {suggestionResponse.urgencyNote}
                </div>
              )}

              {suggestionResponse.suggestions.length === 0 ? (
                suggestionResponse.reason !== 'NO_AVAILABLE_DOCTORS' && (
                  <p className="rounded-xl bg-gray-50 px-4 py-3 text-sm text-slate-500">
                    No doctors are currently accepting new patients. Please try again shortly.
                  </p>
                )
              ) : (
                suggestionResponse.suggestions.map((s) => (
                  <div
                    key={s.doctorCatalogEntryId}
                    className="card flex flex-col gap-3 p-4 transition-all duration-200 hover:shadow-lift sm:flex-row sm:items-center"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-sm font-bold text-slate-800">{s.name}</h3>
                        <span className="inline-flex items-center rounded-md bg-brand-50 px-1.5 py-0.5 text-xs font-semibold text-brand-700">
                          {s.specialization}
                        </span>
                      </div>
                      <p className="mt-0.5 text-xs text-slate-500">
                        {s.departmentName} · {s.matchReason}
                      </p>
                      <div className="mt-1.5 flex flex-wrap gap-3 text-xs text-slate-500">
                        <span className="inline-flex items-center gap-1">
                          <Award className="h-3.5 w-3.5 text-slate-400" />
                          {s.experienceYears} yrs
                        </span>
                        <span className="inline-flex items-center gap-1">
                          <Wallet className="h-3.5 w-3.5 text-slate-400" />
                          ₹{s.consultationFee}
                        </span>
                        <span className="inline-flex items-center gap-1">
                          <Clock className="h-3.5 w-3.5 text-slate-400" />
                          ≈{s.predictedWaitMinutes} min wait · position {s.position}
                        </span>
                      </div>
                    </div>
                    <Button
                      onClick={() => handleJoin(String(s.doctorCatalogEntryId))}
                      loading={isJoining}
                      className="w-full shrink-0 sm:w-auto"
                    >
                      Join this doctor →
                    </Button>
                  </div>
                ))
              )}

            </div>
          )}

          {/* Manual picker (default when arriving from Browse, or via the link) */}
          {showManualPicker && (
            <>
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
                        {doc.name || doc.specialization} — {doc.specialization} ({doc.departmentName}, ≈{doc.avgConsultationTimeMinutes} min)
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

              <Button onClick={() => handleJoin()} loading={isJoining} className="w-full py-3 text-base">
                {isJoining ? 'Running AI triage…' : 'Join Queue →'}
              </Button>
            </>
          )}

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

  const [cancelQueueEntry, { isLoading: isCancelling }] = useCancelQueueEntryMutation();
  const [cancelError, setCancelError] = useState('');

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

  const handleCancel = async () => {
    const target = liveEntry ?? joinedEntry;
    if (!target) return;
    if (!window.confirm('Leave the queue? Your position will be released.')) return;
    setCancelError('');
    try {
      await cancelQueueEntry(target.id).unwrap();
      // The status poll refetches (tag invalidated) and reports no active
      // entry, which returns the page to the join flow automatically.
    } catch (err: unknown) {
      setCancelError(getErrorMessage(err));
    }
  };

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
            onCancel={handleCancel}
            isCancelling={isCancelling}
            cancelError={cancelError}
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
