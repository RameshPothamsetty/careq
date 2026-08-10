import { useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { Link } from 'react-router-dom';
import {
  Clock,
  ArrowRight,
  PhoneCall,
  Users,
  UserCheck,
  Activity,
  CalendarDays,
  Building2,
  Timer,
  Stethoscope,
  Radio,
} from 'lucide-react';
import { skipToken } from '@reduxjs/toolkit/query/react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  LineChart,
  Line,
} from 'recharts';
import {
  useGetDoctorsQuery,
  useGetMyDoctorQuery,
  useToggleAvailabilityMutation,
} from '../services/rtk/doctorApi';
import {
  useGetDoctorQueueQuery,
  useCallNextMutation,
  useGetDoctorAnalyticsQuery,
} from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import type { QueueEntryResponse, TriageLevel } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatusTag, Button, StatCard, LiveBadge, AvatarInitials, CountUp } from '../components/ui';
import { LoadingState, EmptyState } from '../components/ui/States';

const POLL_INTERVAL_MS = 10_000;
const WAITING_PREVIEW_LIMIT = 5;
const TRIAGE_LEVELS: TriageLevel[] = ['EMERGENCY', 'HIGH', 'NORMAL', 'FOLLOW_UP'];
// Day 13: the doctor's own entry comes from /api/doctors/me (header-based
// identity), polled so the dashboard self-heals once an admin links the
// account. The big doctors list is still fetched for the department context.
const MY_ENTRY_POLL_MS = 15_000;

// Dark chart palette (night surfaces) — the dashboard is dark-first.
const CHART_GRID = '#1e2c35';
const CHART_TICK = '#7d9ba1';
const CHART_TOOLTIP = {
  borderRadius: 12,
  border: '1px solid #1e2c35',
  backgroundColor: '#101a20',
  color: '#e2e8f0',
  fontSize: 13,
};

function displayName(entry: QueueEntryResponse) {
  return entry.patientName || `#${entry.patientId.slice(0, 4).toUpperCase()}`;
}

const formatDay = (iso: string) => {
  const d = new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
};

const shortDay = (iso: string) => {
  const d = new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString(undefined, { day: 'numeric' });
};

export default function DoctorDashboard() {
  const { user } = useAuth();
  const { isDark } = useTheme();
  const pageClass = isDark
    ? 'dark min-h-screen bg-mesh-dark px-4 py-6 sm:px-6'
    : 'min-h-screen bg-mesh-light px-4 py-6 sm:px-6';
  // A single large page guarantees the doctor's own catalog entry is in the
  // cache even for a big catalog (listing is server-side paginated since Day 7a).
  const { data: doctors, isLoading, error: queryError } = useGetDoctorsQuery({ size: 1000 });
  const [toggleAvailability, { isLoading: isToggling }] = useToggleAvailabilityMutation();
  const [callNext] = useCallNextMutation();
  const [actionError, setActionError] = useState('');
  const [success, setSuccess] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);

  // Day 13: authoritative resolution of the doctor's own catalog entry.
  // isError (404) means the account isn't linked to a catalog entry yet.
  const {
    data: doctorEntry,
    isLoading: myEntryLoading,
    isError: myEntryError,
    error: myEntryQueryError,
    refetch: refetchMyEntry,
  } = useGetMyDoctorQuery(undefined, {
    skip: user?.role !== 'DOCTOR',
    pollingInterval: MY_ENTRY_POLL_MS,
  });

  // Live queue snapshot — same endpoint the queue page polls; the dashboard
  // just subscribes to its own copy (RTK dedupes cache keys, so this is one
  // request, not two).
  const {
    data: queue,
    isLoading: queueLoading,
    error: queueError,
    fulfilledTimeStamp,
  } = useGetDoctorQueueQuery(
    doctorEntry ? { doctorCatalogEntryId: doctorEntry.id } : skipToken,
    { pollingInterval: POLL_INTERVAL_MS },
  );

  // Personal analytics — refreshed automatically whenever a queue mutation
  // invalidates the Analytics tag, so today's numbers stay current without
  // an extra polling loop.
  const {
    data: analytics,
    isLoading: analyticsLoading,
    error: analyticsError,
  } = useGetDoctorAnalyticsQuery(doctorEntry ? doctorEntry.id : skipToken);

  const error = queryError ? getErrorMessage(queryError) : actionError;
  const queueErrorMessage = queueError ? getErrorMessage(queueError) : '';
  // ONLY a 404 means "not linked yet" — a doctor-service outage (5xx/network)
  // must surface as a real error, never as a misleading "ask an admin" card.
  const myEntryIs404 =
    myEntryError && (myEntryQueryError as { status?: unknown } | undefined)?.status === 404;
  const noCatalogEntry = myEntryIs404 && !myEntryLoading && !doctorEntry;

  const entries = queue ?? [];
  const waitingEntries = entries.filter((e) => e.status === 'WAITING');
  const waitingCount = waitingEntries.length;
  const inProgressCount = entries.filter((e) => e.status === 'IN_PROGRESS').length;
  const longestWait = waitingEntries.reduce(
    (max, e) => Math.max(max, e.predictedWaitMinutes ?? 0),
    0,
  );
  const firstWaiting = waitingEntries[0] ?? null;
  const secondsAgo = Math.max(
    0,
    Math.round((Date.now() - (fulfilledTimeStamp ?? Date.now())) / 1000),
  );
  const showQueue = !!doctorEntry && !queryError;

  // Triage mix of the patients still waiting (in-consultation is already its
  // own StatCard, so this answers "who still needs urgent attention").
  const triageMix = useMemo(() => {
    const counts: Record<TriageLevel, number> = { EMERGENCY: 0, HIGH: 0, NORMAL: 0, FOLLOW_UP: 0 };
    for (const e of waitingEntries) counts[e.effectiveTriage] += 1;
    return counts;
  }, [waitingEntries]);

  // Department context — colleagues in the same department from the catalog
  // list already loaded for this dashboard (no extra request). Self is
  // excluded so the figure reads "colleagues online", not a self-inclusive count.
  const deptDoctors = useMemo(
    () =>
      (doctors?.content ?? []).filter(
        (d) => d.departmentName === doctorEntry?.departmentName && d.userId !== user?.id,
      ),
    [doctors, doctorEntry?.departmentName, user?.id],
  );
  const onlineInDept = deptDoctors.filter((d) => d.isAvailable).length;

  // Charts: keep null for days with no calls so the line chart draws a gap.
  const patientData = (analytics?.patientsPerDay ?? []).map((d) => ({
    ...d,
    label: formatDay(d.date),
    day: shortDay(d.date),
  }));
  const waitData = (analytics?.avgWaitTimeTrend ?? []).map((d) => ({
    ...d,
    label: formatDay(d.date),
    day: shortDay(d.date),
    avgWaitMinutes: d.avgWaitMinutes,
  }));
  const hasActivity =
    (analytics?.patientsPerDay ?? []).some((d) => d.count > 0) ||
    (analytics?.avgWaitTimeTrend ?? []).some((d) => d.avgWaitMinutes !== null);

  const handleToggleAvailability = async () => {
    if (!doctorEntry) return;
    setActionError('');
    setSuccess('');
    try {
      const updated = await toggleAvailability({
        isAvailable: !doctorEntry.isAvailable,
      }).unwrap();
      setSuccess(`You are now ${updated.isAvailable ? 'online' : 'offline'}`);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: unknown) {
      setActionError(getErrorMessage(err));
    }
  };

  const handleCallNext = async () => {
    if (!firstWaiting) return;
    setActionError('');
    setSuccess('');
    setBusyId(firstWaiting.id);
    try {
      await callNext(firstWaiting.id).unwrap();
      setSuccess(`Called ${displayName(firstWaiting)} — consultation started`);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: unknown) {
      setActionError(getErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  };

  const loadingRoot = (
    <div className={pageClass}>
      <div className="mx-auto max-w-5xl">
        <LoadingState label="Loading your profile…" />
      </div>
    </div>
  );

  if (isLoading || (myEntryLoading && !doctorEntry && !myEntryError)) {
    return loadingRoot;
  }

  return (
    <div className={pageClass}>
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="🩺"
          title="Doctor Dashboard"
          subtitle={`Welcome, Dr. ${user?.fullName || 'Doctor'}`}
          dashboardPath="/doctor"
          showDashboard={false}
        />

        {error && (
          <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            ⚠ {error}
          </div>
        )}
        {success && (
          <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300">
            ✓ {success}
          </div>
        )}

        {/* Day 13: account not linked to a doctor catalog entry yet — a clear,
            actionable setup state (auto-refreshes) instead of a blank page. */}
        {noCatalogEntry && (
          <EmptyState
            icon={<Stethoscope className="h-8 w-8 text-brand-400" />}
            title="No doctor profile linked yet"
            message="Your account isn't linked to a doctor catalog entry yet, so your queue and analytics aren't available. Ask an admin to create one in Admin → Manage Doctors (they'll need this account's user ID, visible in the Admin user directory). This dashboard checks automatically every 15 seconds."
            action={
              <Button variant="secondary" onClick={refetchMyEntry}>
                Re-check now
              </Button>
            }
          />
        )}

        {/* Availability toggle card */}
        {doctorEntry && (
          <div className="card p-6">
            <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
              <div>
                <div className="flex items-center gap-2.5">
                  <h2 className="font-display text-lg font-bold text-slate-800 dark:text-slate-100">{doctorEntry.name || doctorEntry.specialization}</h2>
                  <StatusTag status={doctorEntry.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                </div>
                <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                  {doctorEntry.specialization} · {doctorEntry.departmentName} · {doctorEntry.qualification} · {doctorEntry.experienceYears} years exp
                </p>
                <div className="mt-3 flex flex-wrap gap-5 text-sm text-slate-500 dark:text-slate-400">
                  <span>💰 Fee: ₹{doctorEntry.consultationFee}</span>
                  <span>⏱ Avg: {doctorEntry.avgConsultationTimeMinutes} min/patient</span>
                </div>
              </div>
              <Button
                onClick={handleToggleAvailability}
                loading={isToggling}
                className={`shrink-0 ${
                  doctorEntry.isAvailable
                    ? '!border-red-200 !bg-red-50 !text-red-600 hover:!bg-red-100 dark:!border-red-500/30 dark:!bg-red-500/10 dark:!text-red-300 dark:hover:!bg-red-500/20'
                    : '!border-transparent !bg-emerald-600 !text-white hover:!bg-emerald-500'
                }`}
              >
                {doctorEntry.isAvailable ? 'Go Offline' : 'Go Online'}
              </Button>
            </div>
          </div>
        )}

        {/* Live queue snapshot — stats + next patient, right on the dashboard */}
        {showQueue && (
          <div className="space-y-3">
            <div className="flex items-center justify-between px-1">
              <LiveBadge lastUpdatedSeconds={secondsAgo} />
            </div>

            {queueLoading && !queue ? (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="skeleton h-24 rounded-xl" />
                ))}
              </div>
            ) : queueErrorMessage ? (
              <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm font-medium text-amber-300">
                ⚠ Couldn't load live queue: {queueErrorMessage}
              </div>
            ) : (
              <>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
                  <StatCard
                    label="Waiting"
                    value={<CountUp value={waitingCount} />}
                    icon={<Users className="h-5 w-5" />}
                  />
                  <StatCard
                    label="In consultation"
                    value={<CountUp value={inProgressCount} />}
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

                {/* Triage mix — at a glance, who still needs urgent attention */}
                {waitingCount > 0 && (
                  <div className="card flex flex-wrap items-center gap-2 p-4">
                    <span className="mr-1 text-xs font-semibold uppercase tracking-wide text-slate-400">
                      Triage mix
                    </span>
                    {TRIAGE_LEVELS.map((level) =>
                      triageMix[level] > 0 ? (
                        <span key={level} className="inline-flex items-center gap-1.5">
                          <StatusTag status={level} />
                          <span className="text-sm font-bold text-slate-700 tabular-nums dark:text-slate-200">{triageMix[level]}</span>
                        </span>
                      ) : null,
                    )}
                  </div>
                )}

                {firstWaiting ? (
                  <div className="card relative flex flex-col items-center justify-between gap-4 overflow-hidden border-brand-200 bg-gradient-to-r from-brand-50 via-white to-white p-5 dark:border-brand-500/30 dark:from-brand-500/15 dark:via-night-800/60 dark:to-night-800/40 sm:flex-row">
                    <div className="pointer-events-none absolute -right-10 -top-10 h-32 w-32 rounded-full bg-brand-500/15 blur-2xl" />
                    <div className="relative flex items-center gap-3.5">
                      <AvatarInitials name={displayName(firstWaiting)} size="lg" />
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="text-base font-bold text-slate-800 dark:text-slate-100">{displayName(firstWaiting)}</p>
                          <StatusTag status={firstWaiting.effectiveTriage} />
                        </div>
                        <p className="mt-1 text-sm text-brand-700 dark:text-brand-300">Next patient ready</p>
                        <p className="mt-0.5 max-w-md truncate text-xs text-slate-500 dark:text-slate-400">{firstWaiting.symptomText}</p>
                      </div>
                    </div>
                    <div className="relative flex items-center gap-3">
                      <span className="text-xs text-slate-500 dark:text-slate-400 tabular-nums">
                        Wait ≈ <CountUp value={firstWaiting.predictedWaitMinutes ?? 0} /> min
                      </span>
                      <Button
                        onClick={handleCallNext}
                        loading={busyId === firstWaiting.id}
                        className="shrink-0 px-6 animate-glow-pulse"
                      >
                        {!busyId && <PhoneCall className="h-4 w-4" />}
                        Call Next
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="card flex items-center gap-4 p-5">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-emerald-50 dark:bg-emerald-500/10">
                      <Activity className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-bold text-slate-800 dark:text-slate-100">Queue is clear</p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        No patients waiting right now — new joins appear here automatically.
                      </p>
                    </div>
                  </div>
                )}

                {/* Waiting-list preview — the actual patients, capped */}
                {waitingEntries.length > 0 && (
                  <div className="card p-5">
                    <div className="flex items-center justify-between">
                      <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                        Waiting list
                      </h2>
                      <Link
                        to="/doctor/queue"
                        className="text-xs font-semibold text-brand-400 transition-colors hover:text-brand-300"
                      >
                        View all {waitingCount} →
                      </Link>
                    </div>
                    <div className="mt-3 divide-y divide-slate-100 dark:divide-slate-700/50">
                      {waitingEntries.slice(0, WAITING_PREVIEW_LIMIT).map((entry) => (
                        <div key={entry.id} className="flex items-center gap-3 py-3">
                          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-sm font-extrabold text-brand-700 tabular-nums dark:bg-brand-500/15 dark:text-brand-300">
                            {entry.position ?? '—'}
                          </div>
                          <AvatarInitials name={displayName(entry)} size="sm" />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">
                              {displayName(entry)}
                            </p>
                            <p className="truncate text-xs text-slate-500 dark:text-slate-400">{entry.symptomText}</p>
                          </div>
                          <StatusTag status={entry.effectiveTriage} />
                          <span className="w-20 text-right text-xs font-semibold text-slate-500 dark:text-slate-400 tabular-nums">
                            ≈{entry.predictedWaitMinutes ?? 0} min
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* Personal analytics — today's numbers + 7-day trends */}
        {showQueue && (
          <div className="space-y-3">
            <div className="flex items-center justify-between px-1">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                Your performance
              </h2>
              <span className="text-xs text-slate-500">Last 7 days</span>
            </div>

            {analyticsLoading && !analytics ? (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="skeleton h-24 rounded-xl" />
                ))}
              </div>
            ) : analyticsError ? (
              <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm font-medium text-amber-300">
                ⚠ Couldn't load your analytics: {getErrorMessage(analyticsError)}
              </div>
            ) : analytics ? (
              <>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
                  <StatCard
                    label="Completed today"
                    value={<CountUp value={analytics.patientsCompletedToday} />}
                    icon={<CalendarDays className="h-5 w-5" />}
                    accent="bg-cyan-50 text-cyan-700 dark:bg-cyan-500/15 dark:text-cyan-300"
                  />
                  <StatCard
                    label="Avg wait today"
                    value={
                      analytics.avgWaitTodayMinutes === null
                        ? '—'
                        : <CountUp value={Math.round(analytics.avgWaitTodayMinutes)} />
                    }
                    icon={<Timer className="h-5 w-5" />}
                    accent="bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300"
                  />
                  <StatCard
                    label="Avg consult today"
                    value={
                      analytics.avgConsultTimeTodayMinutes === null
                        ? '—'
                        : <CountUp value={Math.round(analytics.avgConsultTimeTodayMinutes)} />
                    }
                    icon={<Clock className="h-5 w-5" />}
                    accent="bg-violet-50 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300"
                  />
                </div>

                {hasActivity ? (
                  <div className="grid gap-5 lg:grid-cols-2">
                    <div className="card p-5">
                      <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                        Patients handled per day
                      </h2>
                      <div className="mt-3 h-48">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={patientData} margin={{ top: 4, right: 8, left: -18, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} vertical={false} />
                            <XAxis
                              dataKey="day"
                              tick={{ fontSize: 12, fill: CHART_TICK }}
                              axisLine={{ stroke: CHART_GRID }}
                              tickLine={false}
                            />
                            <YAxis
                              allowDecimals={false}
                              tick={{ fontSize: 12, fill: CHART_TICK }}
                              axisLine={false}
                              tickLine={false}
                            />
                            <Tooltip
                              cursor={{ fill: '#16222a' }}
                              contentStyle={CHART_TOOLTIP}
                              labelFormatter={(_, payload) => payload?.[0]?.payload?.label ?? ''}
                            />
                            <Bar dataKey="count" name="Patients" radius={[6, 6, 0, 0]} fill="#3eb8b8" />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>

                    <div className="card p-5">
                      <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                        Average wait time trend
                      </h2>
                      <div className="mt-3 h-48">
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={waitData} margin={{ top: 4, right: 8, left: -18, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} vertical={false} />
                            <XAxis
                              dataKey="day"
                              tick={{ fontSize: 12, fill: CHART_TICK }}
                              axisLine={{ stroke: CHART_GRID }}
                              tickLine={false}
                            />
                            <YAxis
                              tick={{ fontSize: 12, fill: CHART_TICK }}
                              axisLine={false}
                              tickLine={false}
                              unit="m"
                            />
                            <Tooltip
                              contentStyle={CHART_TOOLTIP}
                              formatter={(value) => [`${value} min`, 'Avg wait']}
                              labelFormatter={(_, payload) => payload?.[0]?.payload?.label ?? ''}
                            />
                            <Line
                              type="monotone"
                              dataKey="avgWaitMinutes"
                              name="Avg wait"
                              stroke="#a78bfa"
                              strokeWidth={2.5}
                              dot={{ r: 3.5, fill: '#a78bfa', strokeWidth: 0 }}
                              activeDot={{ r: 5 }}
                            />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="card flex items-center gap-4 p-5">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-100 dark:bg-slate-700/50">
                      <Activity className="h-5 w-5 text-slate-400 dark:text-slate-500" />
                    </div>
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      No completed consultations in the last 7 days yet — your daily trend will appear here
                      as patients are seen.
                    </p>
                  </div>
                )}
              </>
            ) : null}
          </div>
        )}

        {/* Department context */}
        {showQueue && doctorEntry && (
          <div className="card flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-sky-50 dark:bg-sky-500/10">
              <Building2 className="h-5 w-5 text-sky-600 dark:text-sky-400" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-bold text-slate-800 dark:text-slate-100">
                  {doctorEntry.departmentName} department
                </p>
                <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 tabular-nums">
                  {onlineInDept} of {deptDoctors.length} colleagues online
                </p>
              </div>
              <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700/60">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-sky-500 to-emerald-500 transition-all duration-500"
                  style={{
                    width: deptDoctors.length ? `${Math.round((onlineInDept / deptDoctors.length) * 100)}%` : '0%',
                  }}
                />
              </div>
            </div>
          </div>
        )}

        {/* Queue management card — hidden while the account isn't linked, so it
            never looks like the queue exists when it can't. */}
        {showQueue && (
          <Link
            to="/doctor/queue"
            className="card group flex items-center gap-4 p-6 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lift"
          >
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 shadow-lift">
              <Radio className="h-6 w-6 text-white" />
            </div>
            <div className="flex-1">
              <h2 className="font-display text-lg font-bold text-slate-800 dark:text-slate-100">Live Patient Queue</h2>
              <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                Color-coded AI triage, override controls, call-next & complete
              </p>
            </div>
            <ArrowRight className="h-5 w-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-brand-600 dark:text-slate-600 dark:group-hover:text-brand-400" />
          </Link>
        )}

        {/* Account info */}
        <div className="card p-6">
          <h2 className="mb-4 font-display text-base font-bold text-slate-800 dark:text-slate-100">Account Info</h2>
          <div className="grid gap-3 text-sm sm:grid-cols-3">
            <div className="rounded-xl bg-slate-100 p-3.5 dark:bg-night-700/50">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">Name</p>
              <p className="mt-1 font-semibold text-slate-800 dark:text-slate-100">Dr. {user?.fullName}</p>
            </div>
            <div className="rounded-xl bg-slate-100 p-3.5 dark:bg-night-700/50">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">Email</p>
              <p className="mt-1 break-all font-semibold text-slate-800 dark:text-slate-100">{user?.email}</p>
            </div>
            <div className="rounded-xl bg-slate-100 p-3.5 dark:bg-night-700/50">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">Role</p>
              <p className="mt-1 font-semibold text-slate-800 dark:text-slate-100">Doctor</p>
            </div>
          </div>
        </div>

        <footer className="pt-4 text-center text-xs text-slate-400 dark:text-slate-600">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>
    </div>
  );
}
