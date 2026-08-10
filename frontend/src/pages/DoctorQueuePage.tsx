import { useEffect, useMemo, useRef, useState } from 'react';
import { Clock, PhoneCall, Search, Users, UserCheck, Activity, Stethoscope } from 'lucide-react';
import { skipToken } from '@reduxjs/toolkit/query/react';
import { useAuth } from '../context/AuthContext';
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
import { StatCard, LiveBadge, StatusTag, AvatarInitials, Button } from '../components/ui';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

const POLL_INTERVAL_MS = 10_000;
const OVERRIDE_OPTIONS: TriageLevel[] = ['EMERGENCY', 'HIGH', 'NORMAL', 'FOLLOW_UP'];
// Day 13: the doctor's own catalog entry comes from GET /api/doctors/me
// (header-based identity) instead of scanning the whole paginated catalog —
// which silently broke once the catalog outgrew one page. It is polled so the
// page self-heals the moment an admin links the account.
const MY_ENTRY_POLL_MS = 15_000;
// The queue query is server-side filtered by patient name; the search box
// debounces so typing fires at most one request per pause.
const SEARCH_DEBOUNCE_MS = 300;

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

function displayName(entry: QueueEntryResponse) {
  return entry.patientName || shortId(entry.patientId);
}

export default function DoctorQueuePage() {
  const { user } = useAuth();
  // Day 13: resolve the doctor's own catalog entry via /api/doctors/me.
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
  // Derived stat — the queue endpoint returns active entries only, so a
  // "completed today" count could never populate (it was always 0). The
  // longest current predicted wait is derivable and clinically useful.
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
    runAction(() => completeQueueEntry(entry.id).unwrap(), `Completed ${displayName(entry)} — queue advanced`, entry.id);

  const handleOverride = (entry: QueueEntryResponse, level: TriageLevel) =>
    runAction(() => overrideTriage({ id: entry.id, triageLevel: level }).unwrap(), `Triage updated to ${level}`, entry.id);

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
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
              value={waitingCount}
              icon={<Users className="h-5 w-5" />}
              accent="bg-brand-50 text-brand-700"
            />
            <StatCard
              label="In consultation"
              value={inProgress.length}
              icon={<UserCheck className="h-5 w-5" />}
              accent="bg-violet-50 text-violet-700"
            />
            <StatCard
              label="Longest wait"
              value={`${longestWait} min`}
              icon={<Clock className="h-5 w-5" />}
              accent="bg-amber-50 text-amber-700"
            />
          </div>
        )}

        {/* Live indicator */}
        {!isLoading && !errorMessage && (
          <div className="flex items-center justify-between px-1">
            <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
              <LiveBadge lastUpdatedSeconds={secondsAgo} />
              <span>Polling every 10s</span>
            </div>
            {myEntry && (
              <span className="text-xs text-slate-400">
                ≈{myEntry.avgConsultationTimeMinutes} min/patient avg
              </span>
            )}
          </div>
        )}

        <Toast message={toast?.message ?? ''} tone={toast?.tone ?? 'success'} />

        {/* Day 13: no catalog entry for this account — a friendly setup state
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
              <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Search patients by name…"
                className="input-field !pl-10"
              />
            </div>
            {searchQuery && (
              <span className="text-xs text-slate-400">
                {waitingCount + inProgress.length} match(es) for “{searchQuery}”
              </span>
            )}
          </div>
        )}

        {/* Call next action — hidden while a patient-name search is active, so
            the hero never targets a search match instead of the actual next patient. */}
        {!isLoading && !errorMessage && !searchQuery && firstWaiting && (
          <div className="card flex flex-col items-center justify-between gap-3 border-brand-100 bg-gradient-to-r from-brand-50 to-white p-5 sm:flex-row">
            <div className="flex items-center gap-3">
              <AvatarInitials name={displayName(firstWaiting)} />
              <div>
                <p className="text-sm font-semibold text-slate-800">Next patient ready</p>
                <p className="text-xs text-slate-500">
                  {displayName(firstWaiting)} · {firstWaiting.symptomText.slice(0, 60)}
                  {firstWaiting.symptomText.length > 60 ? '…' : ''}
                </p>
              </div>
            </div>
            <Button
              onClick={() => handleCallNext(firstWaiting)}
              loading={busyId === firstWaiting.id}
              className="shrink-0 px-6"
            >
              {!busyId && <PhoneCall className="h-4 w-4" />}
              Call Next →
            </Button>
          </div>
        )}

        {isLoading ? (
          <LoadingState label="Loading your live queue…" />
        ) : !errorMessage && entries.length === 0 ? (
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
            <div className="space-y-3">
              {entries
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
                              <AvatarInitials name={displayName(entry)} size="sm" />
                              <p className="font-bold text-slate-800">{displayName(entry)}</p>
                              <StatusTag status={entry.effectiveTriage} />
                            </div>
                            <p className="mt-0.5 text-xs text-slate-400">
                              Wait ≈ {entry.predictedWaitMinutes ?? 0} min · AI: {entry.aiSuggestedTriage}
                              {entry.doctorOverrideTriage ? ` → overridden to ${entry.doctorOverrideTriage}` : ''}
                            </p>
                          </div>
                        </div>

                        {/* Symptoms */}
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Symptoms</p>
                          <p className="mt-1 truncate text-sm text-slate-800">{entry.symptomText}</p>
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
                              <Button
                                onClick={() => handleCallNext(entry)}
                                loading={busyId === entry.id}
                                className="!py-1.5 text-xs"
                              >
                                Call Next
                              </Button>
                            </>
                          ) : (
                            <Button
                              onClick={() => handleComplete(entry)}
                              loading={busyId === entry.id}
                              className="!bg-emerald-600 !py-1.5 text-xs hover:!bg-emerald-700"
                            >
                              Complete ✓
                            </Button>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
            </div>
          )
        )}

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Doctor live queue · color-coded AI triage
        </footer>
      </div>
    </div>
  );
}
