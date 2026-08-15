import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Clock, PhoneCall, Search, Users, UserCheck, CheckCircle2, Activity, Stethoscope } from 'lucide-react';
import { skipToken } from '@reduxjs/toolkit/query/react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useGetMyDoctorQuery } from '../services/rtk/doctorApi';
import {
  useGetDoctorQueueQuery,
  useCallNextMutation,
  useCompleteQueueEntryMutation,
  useOverrideTriageMutation,
} from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import type { QueueEntryResponse, TriageLevel } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatCard, LiveBadge, StatusTag, AvatarInitials, Button, CountUp } from '../components/ui';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

const POLL_INTERVAL_MS = 10_000;
const OVERRIDE_OPTIONS: TriageLevel[] = ['EMERGENCY', 'HIGH', 'NORMAL', 'FOLLOW_UP'];
// The doctor's own catalog entry comes from GET /api/doctors/me
// (header-based identity) instead of scanning the whole paginated catalog —
// which silently broke once the catalog outgrew one page. It is polled so the
// page self-heals the moment an admin links the account.
const MY_ENTRY_POLL_MS = 15_000;
// The queue query is server-side filtered by patient name; the search box
// debounces so typing fires at most one request per pause.
const SEARCH_DEBOUNCE_MS = 300;
// Session-completed column is capped — the durable record lives in analytics.
const SESSION_COMPLETED_LIMIT = 8;

function Toast({ message, tone }: { message: string; tone: 'success' | 'error' }) {
  if (!message) return null;
  return (
    <div
      className={`flex items-center gap-2 rounded-xl border px-4 py-3 text-sm font-medium ${
        tone === 'success'
          ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300'
          : 'border-red-200 bg-red-50 text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300'
      }`}
    >
      {tone === 'success' ? '✓' : '⚠'} {message}
    </div>
  );
}

function shortId(patientId: string) {
  return `#${patientId.slice(0, 4).toUpperCase()}`;
}

function displayName(entry: QueueEntryResponse) {
  return entry.patientName || shortId(entry.patientId);
}

function timeAgo(ts: number) {
  const s = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (s < 60) return 'just now';
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  return `${Math.floor(m / 60)}h ago`;
}

/** Kanban column — colored header, count chip, scrollable list, empty state. */
function Column({
  title,
  count,
  accent,
  dot,
  empty,
  children,
}: {
  title: string;
  count: number;
  accent: string;
  dot: string;
  empty: string;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-[420px] flex-col rounded-2xl border border-slate-200 bg-slate-50/60 dark:border-slate-700/50 dark:bg-night-900/60">
      <div className={`flex items-center justify-between rounded-t-2xl border-b border-slate-200 px-4 py-3 dark:border-slate-700/50 ${accent}`}>
        <div className="flex items-center gap-2">
          <span className={`h-2 w-2 rounded-full ${dot}`} />
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">{title}</h3>
        </div>
        <span className="rounded-full bg-slate-200 px-2 py-0.5 text-xs font-bold text-slate-700 tabular-nums dark:bg-slate-700/60 dark:text-slate-200">
          {count}
        </span>
      </div>
      <div className="flex-1 space-y-3 p-3">
        {count === 0 ? (
          <p className="px-2 py-10 text-center text-xs text-slate-500">{empty}</p>
        ) : (
          children
        )}
      </div>
    </div>
  );
}

export default function DoctorQueuePage() {
  const { user } = useAuth();
  const { isDark } = useTheme();
  const pageClass = isDark
    ? 'dark min-h-screen bg-mesh-dark px-4 py-6 sm:px-6'
    : 'min-h-screen bg-mesh-light px-4 py-6 sm:px-6';
  // Resolve the doctor's own catalog entry via /api/doctors/me.
  // isError (404) means the account isn't linked to a catalog entry yet —
  // shown as a friendly setup state below, not a dead-end error.
  const {
    data: myEntry,
    isLoading: myEntryLoading,
    isError: myEntryError,
    error: myEntryQueryError,
    refetch: refetchMyEntry,
  } = useGetMyDoctorQuery(undefined, {
    skip: user?.role !== 'DOCTOR',
    pollingInterval: MY_ENTRY_POLL_MS,
  });

  // Patient-name search box (debounced).
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setSearchQuery(searchInput.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // The queue query only fires once we know our catalog entry id, then polls.
  // Changing the search string refetches immediately; polling keeps the queue
  // live while the search is applied server-side.
  const {
    data: queue,
    isLoading: queueLoading,
    error,
    refetch,
    fulfilledTimeStamp,
  } = useGetDoctorQueueQuery(
    myEntry ? { doctorCatalogEntryId: myEntry.id, search: searchQuery || undefined } : skipToken,
    {
      pollingInterval: POLL_INTERVAL_MS,
    },
  );

  const [callNext] = useCallNextMutation();
  const [completeQueueEntry] = useCompleteQueueEntryMutation();
  const [overrideTriage] = useOverrideTriageMutation();

  const [toast, setToast] = useState<{ message: string; tone: 'success' | 'error' } | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  // Completions from THIS session — the queue endpoint returns active entries
  // only, so the third column is fed client-side (the durable record is the
  // doctor's analytics).
  const [completedLocal, setCompletedLocal] = useState<
    { entry: QueueEntryResponse; doneAt: number }[]
  >([]);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = (message: string, tone: 'success' | 'error' = 'success') => {
    setToast({ message, tone });
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 4000);
  };

  // ONLY a 404 means "not linked yet" — a doctor-service outage (5xx/network)
  // must surface as a real error, never as a misleading "ask an admin" card.
  const myEntryIs404 =
    myEntryError && (myEntryQueryError as { status?: unknown } | undefined)?.status === 404;
  const noCatalogEntry = myEntryIs404 && !myEntryLoading && !myEntry;
  const errorMessage = useMemo(() => {
    if (error) return getErrorMessage(error);
    return '';
  }, [error]);

  // The queue query may be skipped (no catalog entry yet) — retry both the
  // entry resolution and the queue so the button always does something.
  const handleRetry = () => {
    refetchMyEntry();
    refetch();
  };

  const isLoading = myEntryLoading || (queueLoading && !!myEntry);

  const entries = queue ?? [];
  const firstWaiting = entries.find((e) => e.status === 'WAITING');
  const inProgress = entries.filter((e) => e.status === 'IN_PROGRESS');
  const waitingEntries = entries.filter((e) => e.status === 'WAITING');
  const waitingCount = waitingEntries.length;
  // Derived stat — the queue endpoint returns active entries only.
  const longestWait = waitingEntries.reduce(
    (max, e) => Math.max(max, e.predictedWaitMinutes ?? 0),
    0,
  );
  const secondsAgo = Math.max(
    0,
    Math.round((Date.now() - (fulfilledTimeStamp ?? Date.now())) / 1000),
  );

  // Mutations invalidate the DoctorQueue tag, so the queue refetches
  // automatically after every action — no manual refetch needed.
  const runAction = async (
    action: () => Promise<unknown>,
    successMessage: string,
    id: number,
  ) => {
    setBusyId(id);
    try {
      await action();
      showToast(successMessage);
    } catch (err: unknown) {
      showToast(getErrorMessage(err), 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleCallNext = (entry: QueueEntryResponse) =>
    runAction(() => callNext(entry.id).unwrap(), `Called ${displayName(entry)} — consultation started`, entry.id);

  const handleComplete = (entry: QueueEntryResponse) =>
    // Move the patient into the session-completed column ONLY on success — a
    // failed mutation must not leave a phantom entry in the Completed column.
    runAction(
      async () => {
        await completeQueueEntry(entry.id).unwrap();
        setCompletedLocal((prev) =>
          [{ entry, doneAt: Date.now() }, ...prev.filter((c) => c.entry.id !== entry.id)].slice(0, SESSION_COMPLETED_LIMIT),
        );
      },
      `Completed ${displayName(entry)} — queue advanced`,
      entry.id,
    );

  const handleOverride = (entry: QueueEntryResponse, level: TriageLevel) =>
    runAction(() => overrideTriage({ id: entry.id, triageLevel: level }).unwrap(), `Triage updated to ${level}`, entry.id);

  return (
    <div className={pageClass}>
      <div className="mx-auto max-w-7xl space-y-6">
        <QueuePageHeader
          icon="🩺"
          title="Live Patient Queue"
          subtitle={myEntry ? `${myEntry.specialization} · ${myEntry.departmentName}` : 'Your queue'}
          dashboardPath="/doctor"
        />

        {/* Stats row */}
        {!isLoading && !errorMessage && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
            <StatCard
              label="Waiting"
              value={<CountUp value={waitingCount} />}
              icon={<Users className="h-5 w-5" />}
            />
            <StatCard
              label="In consultation"
              value={<CountUp value={inProgress.length} />}
              icon={<UserCheck className="h-5 w-5" />}
              accent="bg-violet-50 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300"
            />
            <StatCard
              label="Longest wait"
              value={<CountUp value={longestWait} />}
              icon={<Clock className="h-5 w-5" />}
              accent="bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300"
            />
          </div>
        )}

        {/* Live indicator */}
        {!isLoading && !errorMessage && (
          <div className="flex items-center justify-between px-1">
            <div className="flex items-center gap-2 text-xs font-medium text-slate-400">
              <LiveBadge lastUpdatedSeconds={secondsAgo} />
              <span>Polling every 10s</span>
            </div>
            {myEntry && (
              <span className="text-xs text-slate-500 tabular-nums">
                ≈{myEntry.avgConsultationTimeMinutes} min/patient avg
              </span>
            )}
          </div>
        )}

        <Toast message={toast?.message ?? ''} tone={toast?.tone ?? 'success'} />

        {/* No catalog entry for this account — a friendly setup state
            (polled every 15s, so it disappears by itself once an admin links
            the account), not the old dead-end error. */}
        {noCatalogEntry && !isLoading && (
          <EmptyState
            icon={<Stethoscope className="h-8 w-8 text-brand-400" />}
            title="No doctor profile linked yet"
            message="Your account isn't linked to a doctor catalog entry yet. Ask an admin to create one in Admin → Manage Doctors (they'll need this account's user ID, visible in the Admin user directory). This page checks automatically every 15 seconds."
            action={
              <Button variant="secondary" onClick={handleRetry}>
                Re-check now
              </Button>
            }
          />
        )}

        {errorMessage && !isLoading && !noCatalogEntry && (
          <ErrorState message={errorMessage} onRetry={handleRetry} />
        )}

        {/* Patient-name search (server-side filter) */}
        {!isLoading && !errorMessage && myEntry && (
          <div className="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Search patients by name…"
                className="input-field !pl-10"
              />
            </div>
            {searchQuery && (
              <span className="text-xs text-slate-500 tabular-nums">
                {waitingCount + inProgress.length} match(es) for “{searchQuery}”
              </span>
            )}
          </div>
        )}

        {/* Call next action — hidden while a patient-name search is active, so
            the hero never targets a search match instead of the actual next patient. */}
        {!isLoading && !errorMessage && !searchQuery && firstWaiting && (
          <div className="card relative flex flex-col items-center justify-between gap-3 overflow-hidden border-brand-200 bg-gradient-to-r from-brand-50 via-white to-white p-5 dark:border-brand-500/30 dark:from-brand-500/15 dark:via-night-800/60 dark:to-night-800/40 sm:flex-row">
            <div className="pointer-events-none absolute -right-10 -top-10 h-32 w-32 rounded-full bg-brand-500/15 blur-2xl" />
            <div className="relative flex items-center gap-3">
              <AvatarInitials name={displayName(firstWaiting)} />
              <div>
                <p className="text-sm font-semibold text-brand-700 dark:text-brand-300">Next patient ready</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {displayName(firstWaiting)} · {firstWaiting.symptomText.slice(0, 60)}
                  {firstWaiting.symptomText.length > 60 ? '…' : ''}
                </p>
              </div>
            </div>
            <Button
              onClick={() => handleCallNext(firstWaiting)}
              loading={busyId === firstWaiting.id}
              className="shrink-0 px-6 animate-glow-pulse"
            >
              {!busyId && <PhoneCall className="h-4 w-4" />}
              Call Next →
            </Button>
          </div>
        )}

        {isLoading ? (
          <LoadingState label="Loading your live queue…" />
        ) : !errorMessage && entries.length === 0 && completedLocal.length === 0 ? (
          <EmptyState
            icon={<Activity className="h-8 w-8 text-brand-400" />}
            title={searchQuery ? 'No patients match your search' : 'No patients in queue right now'}
            message={
              searchQuery
                ? `Nothing found for “${searchQuery}” — try a different name.`
                : 'New patients will appear here automatically as they join with AI triage.'
            }
          />
        ) : (
          !errorMessage && (
            <div className="grid items-start gap-5 lg:grid-cols-3">
              {/* ── Waiting ─────────────────────────────────────── */}
              <Column
                title="Waiting"
                count={waitingEntries.length}
                accent="bg-sky-500/10"
                dot="bg-sky-400"
                empty="No patients waiting — new joins appear here with AI triage."
              >
                {waitingEntries.map((entry, idx) => (
                  <div
                    key={entry.id}
                    className="animate-fade-in-up rounded-xl border border-slate-200 bg-white p-4 transition-all duration-200 hover:border-slate-300 hover:bg-slate-50 dark:border-slate-700/50 dark:bg-night-800/60 dark:hover:border-slate-600 dark:hover:bg-night-700/60"
                    style={{ animationDelay: `${idx * 50}ms` }}
                  >
                    <div className="flex items-start gap-3">
                      <div
                        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-base font-extrabold tabular-nums ${
                          entry.position === 1
                            ? 'bg-brand-500 text-white'
                            : 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300'
                        }`}
                      >
                        {entry.position ?? '—'}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="truncate text-sm font-bold text-slate-800 dark:text-slate-100">{displayName(entry)}</p>
                          <StatusTag status={entry.effectiveTriage} />
                        </div>
                        <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                          Wait ≈ {entry.predictedWaitMinutes ?? 0} min · AI: {entry.aiSuggestedTriage}
                          {entry.doctorOverrideTriage ? ` → overridden to ${entry.doctorOverrideTriage}` : ''}
                        </p>
                        <p className="mt-1.5 line-clamp-2 text-xs text-slate-400">{entry.symptomText}</p>
                      </div>
                    </div>
                    <div className="mt-3 flex flex-wrap items-center gap-2">
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
                      <Button
                        onClick={() => handleCallNext(entry)}
                        loading={busyId === entry.id}
                        className="!py-1.5 text-xs"
                      >
                        Call Next
                      </Button>
                    </div>
                  </div>
                ))}
              </Column>

              {/* ── In consultation ─────────────────────────────── */}
              <Column
                title="In consultation"
                count={inProgress.length}
                accent="bg-violet-500/10"
                dot="bg-violet-400"
                empty="Call a patient to move them here."
              >
                {inProgress.map((entry) => (
                  <div
                    key={entry.id}
                    className="rounded-xl border border-violet-200 bg-violet-50 p-4 transition-all duration-200 hover:bg-violet-100 dark:border-violet-500/30 dark:bg-violet-500/10 dark:hover:bg-violet-500/15"
                  >
                    <div className="flex items-start gap-3">
                      <AvatarInitials name={displayName(entry)} />
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="truncate text-sm font-bold text-slate-800 dark:text-slate-100">{displayName(entry)}</p>
                          <StatusTag status={entry.effectiveTriage} />
                        </div>
                        <p className="mt-1.5 line-clamp-2 text-xs text-slate-500 dark:text-slate-400">{entry.symptomText}</p>
                      </div>
                    </div>
                    <Button
                      onClick={() => handleComplete(entry)}
                      loading={busyId === entry.id}
                      className="mt-3 w-full !bg-emerald-600 !py-2 text-xs hover:!bg-emerald-500"
                    >
                      Complete ✓
                    </Button>
                  </div>
                ))}
              </Column>

              {/* ── Completed (this session) ────────────────────── */}
              <Column
                title="Completed"
                count={completedLocal.length}
                accent="bg-emerald-500/10"
                dot="bg-emerald-400"
                empty="Completed consultations collect here for this session."
              >
                {completedLocal.map(({ entry, doneAt }) => (
                  <div
                    key={entry.id}
                    className="animate-scale-in rounded-xl border border-emerald-200 bg-emerald-50 dark:border-emerald-500/25 dark:bg-emerald-500/5"
                  >
                    <div className="flex items-start gap-3">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-100 dark:bg-emerald-500/15">
                        <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="truncate text-sm font-bold text-slate-800 dark:text-slate-100">{displayName(entry)}</p>
                          <StatusTag status="COMPLETED" />
                        </div>
                        <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                          Done {timeAgo(doneAt)} · {entry.specialization || entry.departmentName}
                        </p>
                      </div>
                    </div>
                  </div>
                ))}
                <p className="px-1 pt-1 text-[10px] text-slate-400 dark:text-slate-600">
                  This session only — your full history lives in Analytics.
                </p>
              </Column>
            </div>
          )
        )}

        <footer className="pt-4 text-center text-xs text-slate-400 dark:text-slate-600">
          CareQ — SmartOPD AI | Doctor live queue · color-coded AI triage
        </footer>
      </div>
    </div>
  );
}
