import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { Search, Clock, ArrowRight, HeartPulse, UserRound, RefreshCw } from 'lucide-react';
import QueuePageHeader from '../components/QueuePageHeader';
import { useGetMyQueueStatusQuery } from '../services/rtk/queueApi';
import { LiveBadge, StatusTag, Button } from '../components/ui';
import type { QueueEntryResponse } from '../services/api';

const POLL_INTERVAL_MS = 10_000;

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

export default function PatientDashboard() {
  const { user } = useAuth();
  const {
    data: status,
    isLoading,
    isError,
    refetch,
    fulfilledTimeStamp,
  } = useGetMyQueueStatusQuery(undefined, { pollingInterval: POLL_INTERVAL_MS });

  const entry = status?.active ? status.entry : null;
  const secondsAgo = Math.max(
    0,
    Math.round((Date.now() - (fulfilledTimeStamp ?? Date.now())) / 1000),
  );

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
