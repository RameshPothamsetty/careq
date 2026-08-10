import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import {
  Building2,
  Stethoscope,
  BarChart3,
  TrendingUp,
  Users,
  ArrowRight,
  Activity,
  AlertTriangle,
  Clock,
  UserCheck,
} from 'lucide-react';
import { useGetLiveQueueOverviewQuery } from '../services/rtk/queueApi';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatCard, CountUp } from '../components/ui';

const POLL_INTERVAL_MS = 10_000;

const NAV_CARDS = [
  {
    to: '/admin/departments',
    icon: <Building2 className="h-6 w-6 text-white" />,
    title: 'Manage Departments',
    desc: 'Create, edit, and delete hospital departments',
    tint: 'from-brand-500 to-brand-700',
    wide: false,
  },
  {
    to: '/admin/doctors',
    icon: <Stethoscope className="h-6 w-6 text-white" />,
    title: 'Manage Doctors',
    desc: 'Add, edit, and manage doctor catalog entries',
    tint: 'from-sky-500 to-sky-700',
    wide: false,
  },
  {
    to: '/admin/queue',
    icon: <BarChart3 className="h-6 w-6 text-white" />,
    title: 'Live Queue Overview',
    desc: 'Hospital-wide waiting counts, delays & doctor load in real time',
    tint: 'from-teal-500 to-teal-700',
    wide: false,
  },
  {
    to: '/admin/analytics',
    icon: <TrendingUp className="h-6 w-6 text-white" />,
    title: 'Analytics Dashboard',
    desc: '7-day patient flow, wait-time trends & department distribution',
    tint: 'from-indigo-500 to-indigo-700',
    wide: false,
  },
  {
    to: '/admin/users',
    icon: <Users className="h-6 w-6 text-white" />,
    title: 'Manage Users',
    desc: 'Search the user directory by name or email',
    tint: 'from-violet-500 to-violet-700',
    wide: true,
  },
] as const;

export default function AdminDashboard() {
  const { user } = useAuth();
  // Live hospital snapshot right on the landing page — the admin's most
  // asked question ("what's happening right now?") answered before they
  // navigate anywhere.
  const { data: overview, isLoading } = useGetLiveQueueOverviewQuery(undefined, {
    pollingInterval: POLL_INTERVAL_MS,
  });

  return (
    <div className="dark min-h-screen bg-mesh-dark px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="⚙️"
          title="Admin Dashboard"
          subtitle={`Welcome, ${user?.fullName || 'Admin'}`}
          dashboardPath="/admin"
          showDashboard={false}
        />

        {/* Live command-center strip */}
        <div className="space-y-3">
          <div className="flex items-center justify-between px-1">
            <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
              Hospital right now
            </h2>
            {overview && (
              <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/25 bg-emerald-500/10 px-2.5 py-1 text-xs font-semibold text-emerald-400">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-500" />
                LIVE · 10s
              </span>
            )}
          </div>
          {isLoading && !overview ? (
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-5 lg:gap-4">
              {[0, 1, 2, 3, 4].map((i) => (
                <div key={i} className="skeleton h-24 rounded-xl" />
              ))}
            </div>
          ) : (
            overview && (
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-5 lg:gap-4">
                <StatCard
                  label="Patients waiting"
                  value={<CountUp value={overview.totalWaiting} />}
                  icon={<Users className="h-5 w-5" />}
                  accent="bg-sky-50 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300"
                />
                <StatCard
                  label="In consultation"
                  value={<CountUp value={overview.totalInProgress} />}
                  icon={<UserCheck className="h-5 w-5" />}
                  accent="bg-violet-50 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300"
                />
                <StatCard
                  label="Doctors online"
                  value={<CountUp value={overview.doctorsOnline} />}
                  icon={<Activity className="h-5 w-5" />}
                  accent="bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300"
                />
                <StatCard
                  label="Delayed"
                  value={<CountUp value={overview.delayedConsultations} />}
                  icon={<AlertTriangle className="h-5 w-5" />}
                  accent={
                    overview.delayedConsultations > 0
                      ? 'bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-300'
                      : 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300'
                  }
                />
                <StatCard
                  label="Avg wait (min)"
                  value={<CountUp value={overview.averageWaitMinutes} />}
                  icon={<Clock className="h-5 w-5" />}
                  accent="bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300"
                />
              </div>
            )
          )}
        </div>

        <div className="grid gap-5 sm:grid-cols-2">
          {NAV_CARDS.map((card) => (
            <Link
              key={card.to}
              to={card.to}
              className={`card group p-6 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lift ${
                card.wide ? 'sm:col-span-2' : ''
              }`}
            >
              <div className="flex items-start justify-between">
                <div className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${card.tint} shadow-lift transition-transform duration-200 group-hover:scale-105`}>
                  {card.icon}
                </div>
                <ArrowRight className="h-5 w-5 text-slate-600 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-brand-400" />
              </div>
              <h2 className="mt-4 font-display text-lg font-bold text-slate-100">{card.title}</h2>
              <p className="mt-1 text-sm text-slate-500">{card.desc}</p>
            </Link>
          ))}
        </div>

        <div className="card p-6">
          <h2 className="font-display text-base font-bold text-slate-100">Welcome, {user?.fullName}!</h2>
          <p className="mt-1 text-sm leading-relaxed text-slate-400">
            You are logged in as an <strong className="text-brand-300">Admin</strong>. Manage departments,
            doctors, and monitor queue operations across the hospital.
          </p>
        </div>

        <div className="card p-6">
          <h2 className="mb-4 font-display text-base font-bold text-slate-100">Account Info</h2>
          <div className="grid gap-3 text-sm sm:grid-cols-3">
            <div className="rounded-xl bg-night-700/50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Name</p>
              <p className="mt-1 font-semibold text-slate-100">{user?.fullName}</p>
            </div>
            <div className="rounded-xl bg-night-700/50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Email</p>
              <p className="mt-1 break-all font-semibold text-slate-100">{user?.email}</p>
            </div>
            <div className="rounded-xl bg-night-700/50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Role</p>
              <p className="mt-1 font-semibold text-slate-100">Admin</p>
            </div>
          </div>
        </div>

        <footer className="pt-4 text-center text-xs text-slate-600">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>
    </div>
  );
}
