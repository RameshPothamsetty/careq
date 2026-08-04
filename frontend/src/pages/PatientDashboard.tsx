import { useEffect, useMemo, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import {
  Search,
  Clock,
  ArrowRight,
  HeartPulse,
  UserRound,
  RefreshCw,
  History,
  Sparkles,
  Award,
  Wallet,
} from 'lucide-react';
import QueuePageHeader from '../components/QueuePageHeader';
import { useGetMyQueueStatusQuery, useGetMyQueueHistoryQuery } from '../services/rtk/queueApi';
import { useGetDoctorsQuery } from '../services/rtk/doctorApi';
import { LiveBadge, StatusTag, Button, AvatarInitials } from '../components/ui';
import type { DoctorCatalogResponse, QueueEntryResponse } from '../services/api';

const POLL_INTERVAL_MS = 10_000;
const HISTORY_LIMIT = 10;
const RECOMMENDATION_LIMIT = 3;

const NAV_CARDS = [
  {
    to: '/patient/doctors',
    icon: <Search className="h-6 w-6 text-white" />,
    title: 'Browse Doctors',
    desc: 'Find and filter doctors by department and specialization',
    tint: 'from-brand-500 to-brand-700',
  },
  {
    to: '/patient/queue',
    icon: <Clock className="h-6 w-6 text-white" />,
    title: 'My Queue',
    desc: 'Join a queue, see your live position and AI-estimated wait time',
    tint: 'from-sky-500 to-sky-700',
  },
] as const;

/** "Today · 2:10 PM" for today's visits, "Mon, Jul 30 · 9:05 AM" otherwise. */
function visitLabel(iso: string) {
  const d = new Date(iso);
  const today = new Date();
  const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  if (d.toDateString() === today.toDateString()) return `Today · ${time}`;
  return `${d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })} · ${time}`;
}

// ─── Live "My visit" widget ──────────────────────────────────────────
// Polls the same my-status endpoint the queue page uses; while the patient
// has an active entry the dashboard shows their live position/ETA instead of
// a dead card. When the entry flips to IN_PROGRESS the notifications hook
// (which watches the same cache entry) fires its toast from here too.

// Note: the entry is only ever WAITING or IN_PROGRESS here — once a visit
// completes, the backend reports it as no longer active (active: false), so
// this card unmounts and the "no active visit" state takes over.
function MyVisitCard({
  entry,
  lastUpdatedSeconds,
}: {
  entry: QueueEntryResponse;
  lastUpdatedSeconds: number;
}) {
  const isInProgress = entry.status === 'IN_PROGRESS';
  const stage = isInProgress ? 1 : 0;

  return (
    <div className="relative overflow-hidden rounded-3xl border border-brand-100 bg-gradient-to-br from-brand-600 to-brand-800 p-6 text-white shadow-lift">
      <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-white/10" />
      <div className="pointer-events-none absolute -bottom-16 -left-10 h-44 w-44 rounded-full bg-white/5" />

      <div className="relative flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-100">
            {entry.specialization} · {entry.departmentName}
          </p>
          <h2 className="mt-1 text-lg font-bold">
            {isInProgress ? "It's your turn!" : 'Your live queue position'}
          </h2>
        </div>
        <div className="flex flex-col items-end gap-2">
          <StatusTag status={entry.effectiveTriage} className="!bg-white/15 !border-white/25 !text-white" />
          <LiveBadge lastUpdatedSeconds={lastUpdatedSeconds} />
        </div>
      </div>

      <div className="relative mt-5 flex items-end gap-10">
        <div>
          <div className="text-5xl font-extrabold leading-none tracking-tight">
            {isInProgress ? '→' : entry.position ?? '—'}
          </div>
          <p className="mt-2 text-sm font-medium text-brand-100">
            {isInProgress ? 'Called — head to the doctor' : 'Position in queue'}
          </p>
        </div>
        <div>
          <div className="text-5xl font-extrabold leading-none tracking-tight">
            {isInProgress ? 'Now' : `${entry.predictedWaitMinutes ?? 0} min`}
          </div>
          <p className="mt-2 text-sm font-medium text-brand-100">Estimated wait</p>
        </div>
      </div>

      {/* Joined → Called → Completed progress */}
      <div className="relative mt-6">
        <div className="flex items-center justify-between text-[11px] font-semibold uppercase tracking-wide text-brand-100">
          <span className={stage >= 0 ? 'text-white' : ''}>Joined</span>
          <span className={stage >= 1 ? 'text-white' : ''}>Called</span>
          <span>Completed</span>
        </div>
        <div className="mt-2 flex items-center">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex-1">
              <div className={`h-1.5 rounded-full ${i < stage ? 'bg-white' : 'bg-white/25'}`} />
            </div>
          ))}
        </div>
      </div>

      {/* Doctor + deep link */}
      <div className="relative mt-5 flex items-center gap-3 rounded-2xl bg-white/10 px-4 py-3">
        <UserRound className="h-5 w-5 shrink-0 text-brand-100" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold">{entry.doctorName}</p>
          <p className="text-xs text-brand-100/80">
            Joined at {new Date(entry.joinedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </p>
        </div>
        <Link
          to="/patient/queue"
          className="shrink-0 text-xs font-bold text-white underline-offset-2 hover:underline"
        >
          Open My Queue →
        </Link>
      </div>
    </div>
  );
}

// ─── Recent visits ───────────────────────────────────────────────────

function RecentVisitsCard({ history }: { history: QueueEntryResponse[] }) {
  return (
    <div className="card p-5">
      <div className="flex items-center gap-2">
        <History className="h-4 w-4 text-slate-400" />
        <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">Recent visits</h2>
      </div>
      <div className="mt-2 divide-y divide-slate-100">
        {history.map((entry) => (
          <div key={entry.id} className="flex flex-wrap items-center gap-3 py-3">
            <AvatarInitials name={entry.doctorName || 'Dr'} size="sm" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-slate-800">
                {entry.doctorName ?? 'Visit'}
              </p>
              <p className="truncate text-xs text-slate-400">
                {entry.specialization} · {entry.departmentName}
              </p>
            </div>
            <StatusTag status={entry.status} />
            <span className="w-36 text-right text-xs text-slate-500">{visitLabel(entry.joinedAt)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Recommended doctor ──────────────────────────────────────────────

function RecommendedDoctorCard({ doctor }: { doctor: DoctorCatalogResponse }) {
  return (
    <div className="card flex flex-col gap-3 p-5">
      <div className="flex items-center gap-3">
        <AvatarInitials name={doctor.name || doctor.specialization} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold text-slate-800">
            {doctor.name || doctor.specialization}
          </p>
          <p className="mt-0.5 truncate text-xs text-slate-500">
            <span className="rounded-md bg-brand-50 px-1.5 py-0.5 text-xs font-semibold text-brand-700">
              {doctor.specialization}
            </span>{' '}
            {doctor.departmentName}
          </p>
          <div className="mt-1.5 flex flex-wrap gap-3 text-xs text-slate-500">
            <span className="inline-flex items-center gap-1">
              <Award className="h-3.5 w-3.5 text-slate-400" />
              {doctor.experienceYears}y exp
            </span>
            <span className="inline-flex items-center gap-1">
              <Wallet className="h-3.5 w-3.5 text-slate-400" />
              ₹{doctor.consultationFee}
            </span>
            <span className="inline-flex items-center gap-1 font-semibold text-emerald-600">
              ● Available now
            </span>
          </div>
        </div>
      </div>
      <Link to={`/patient/queue?doctor=${doctor.id}`} className="mt-auto">
        <Button className="w-full">Join Queue →</Button>
      </Link>
    </div>
  );
}

export default function PatientDashboard() {
  const { user } = useAuth();
  const {
    data: status,
    isLoading,
    isError,
    refetch,
    fulfilledTimeStamp,
  } = useGetMyQueueStatusQuery(undefined, { pollingInterval: POLL_INTERVAL_MS });

  const {
    data: historyData,
    isLoading: historyLoading,
    refetch: refetchHistory,
  } = useGetMyQueueHistoryQuery(HISTORY_LIMIT);

  // When the active visit completes while the patient sits on the dashboard
  // (status flips active -> inactive), refetch history so the finished visit
  // appears under "Recent visits" immediately.
  const wasActiveRef = useRef(false);
  useEffect(() => {
    if (wasActiveRef.current && !status?.active) {
      refetchHistory();
    }
    wasActiveRef.current = !!status?.active;
  }, [status?.active, refetchHistory]);

  const entry = status?.active ? status.entry : null;
  const secondsAgo = Math.max(
    0,
    Math.round((Date.now() - (fulfilledTimeStamp ?? Date.now())) / 1000),
  );
  const history = historyData ?? [];

  // Doctor recommendations — prefer available doctors in the department of
  // the most recent visit (continuity of care); fall back to the most
  // experienced doctors currently available. A generous page keeps the whole
  // catalog in cache (same pattern as the doctor dashboard), so the
  // "most experienced" fallback can never miss a doctor on page 2.
  const { data: doctorsData } = useGetDoctorsQuery({ size: 1000 });
  const availableDoctors = useMemo(
    () => (doctorsData?.content ?? []).filter((d) => d.isAvailable),
    [doctorsData],
  );
  const lastVisitDept = history[0]?.departmentName ?? null;
  const recommended = useMemo(() => {
    if (lastVisitDept) {
      const sameDept = availableDoctors.filter((d) => d.departmentName === lastVisitDept);
      if (sameDept.length) return sameDept.slice(0, RECOMMENDATION_LIMIT);
    }
    return [...availableDoctors]
      .sort((a, b) => b.experienceYears - a.experienceYears)
      .slice(0, RECOMMENDATION_LIMIT);
  }, [availableDoctors, lastVisitDept]);
  const recBasedOnVisit =
    !!lastVisitDept && recommended.some((d) => d.departmentName === lastVisitDept);

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="👤"
          title="Patient Dashboard"
          subtitle={`Welcome, ${user?.fullName || 'Patient'}`}
          dashboardPath="/patient"
          showDashboard={false}
        />

        {/* Live visit widget — replaces the static welcome blurb when active */}
        {isLoading && !status ? (
          <div className="h-44 animate-pulse rounded-3xl bg-gray-100" />
        ) : isError && !status ? (
          <div className="card flex flex-col items-center justify-between gap-3 border-amber-200 bg-amber-50 p-5 sm:flex-row">
            <p className="text-sm font-medium text-amber-700">
              ⚠ Couldn't check your queue status right now.
            </p>
            <Button variant="secondary" onClick={refetch} className="shrink-0 !py-1.5 text-xs">
              <RefreshCw className="h-3.5 w-3.5" />
              Retry
            </Button>
          </div>
        ) : entry ? (
          <MyVisitCard entry={entry} lastUpdatedSeconds={secondsAgo} />
        ) : (
          <div className="card flex flex-col items-start justify-between gap-4 p-6 sm:flex-row sm:items-center">
            <div className="flex items-center gap-3.5">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-brand-100">
                <HeartPulse className="h-6 w-6 text-brand-700" />
              </div>
              <div>
                <h2 className="text-base font-bold text-slate-800">No active visit</h2>
                <p className="mt-0.5 text-sm text-slate-500">
                  When you join a queue, your live position &amp; estimated wait will appear right here.
                </p>
              </div>
            </div>
            <Link to="/patient/queue" className="shrink-0">
              <Button>Join a queue →</Button>
            </Link>
          </div>
        )}

        {/* Recent visits — today's history + past visits */}
        <div className="space-y-3">
          {historyLoading && !history.length ? (
            <div className="h-32 animate-pulse rounded-2xl bg-gray-100" />
          ) : history.length > 0 ? (
            <RecentVisitsCard history={history} />
          ) : (
            <div className="card flex items-center gap-4 p-5">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-100">
                <History className="h-5 w-5 text-slate-400" />
              </div>
              <p className="text-sm text-slate-500">
                No visits yet — your completed consultations will appear here, with today's visits
                marked.
              </p>
            </div>
          )}
        </div>

        {/* Recommended doctors */}
        {recommended.length > 0 && (
          <div className="space-y-3">
            <div className="flex items-center justify-between px-1">
              <h2 className="flex items-center gap-1.5 text-sm font-bold uppercase tracking-wide text-slate-400">
                <Sparkles className="h-4 w-4 text-brand-500" />
                {recBasedOnVisit ? `Recommended for you · ${lastVisitDept}` : 'Doctors available now'}
              </h2>
              <Link
                to="/patient/doctors"
                className="text-xs font-semibold text-brand-600 transition-colors hover:text-brand-700"
              >
                Browse all →
              </Link>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {recommended.map((doc) => (
                <RecommendedDoctorCard key={doc.id} doctor={doc} />
              ))}
            </div>
          </div>
        )}

        <div className="grid gap-5 sm:grid-cols-2">
          {NAV_CARDS.map((card) => (
            <Link
              key={card.to}
              to={card.to}
              className="card group p-6 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lift"
            >
              <div className="flex items-start justify-between">
                <div className={`flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br ${card.tint} shadow-lift`}>
                  {card.icon}
                </div>
                <ArrowRight className="h-5 w-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-brand-600" />
              </div>
              <h2 className="mt-4 text-lg font-bold text-slate-800">{card.title}</h2>
              <p className="mt-1 text-sm text-slate-500">{card.desc}</p>
            </Link>
          ))}
        </div>

        <div className="card p-6">
          <h2 className="mb-4 text-base font-bold text-slate-800">Account Info</h2>
          <div className="grid gap-3 text-sm sm:grid-cols-3">
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Name</p>
              <p className="mt-1 font-semibold text-slate-800">{user?.fullName}</p>
            </div>
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Email</p>
              <p className="mt-1 break-all font-semibold text-slate-800">{user?.email}</p>
            </div>
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Role</p>
              <p className="mt-1 font-semibold text-slate-800">Patient</p>
            </div>
          </div>
        </div>

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>
    </div>
  );
}
