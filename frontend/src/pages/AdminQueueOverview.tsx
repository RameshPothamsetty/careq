import { Activity, AlertTriangle, Building2, Clock, Users, UserCheck } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';
import { useGetLiveQueueOverviewQuery } from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatCard, LiveBadge, StatusTag, CountUp } from '../components/ui';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

const POLL_INTERVAL_MS = 10_000;

export default function AdminQueueOverview() {
  const { isDark } = useTheme();
  const pageClass = isDark
    ? 'dark min-h-screen bg-mesh-dark px-4 py-6 sm:px-6'
    : 'min-h-screen bg-mesh-light px-4 py-6 sm:px-6';

  const {
    data: overview,
    isLoading,
    isError,
    error,
    refetch,
    fulfilledTimeStamp,
  } = useGetLiveQueueOverviewQuery(undefined, { pollingInterval: POLL_INTERVAL_MS });

  const secondsAgo = Math.max(
    0,
    Math.round((Date.now() - (fulfilledTimeStamp ?? Date.now())) / 1000),
  );

  return (
    <div className={pageClass}>
      <div className="mx-auto max-w-6xl space-y-6">
        <QueuePageHeader
          icon="🏥"
          title="Live Queue Overview"
          subtitle="Hospital-wide patient flow, delays & doctor load"
          dashboardPath="/admin"
        />

        {overview && (
          <>
            {/* Live indicator */}
            <div className="flex items-center justify-between px-1">
              <div className="flex items-center gap-2 text-xs font-medium text-slate-400">
                <LiveBadge lastUpdatedSeconds={secondsAgo} />
                <span>Refreshing every 10s</span>
              </div>
            </div>

            {isError && (
              <div className="flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300">
                ⚠ Showing the last known overview — a refresh just failed.
              </div>
            )}

            {/* Summary cards — the screenshot-worthy part */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5 sm:gap-4">
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
                label="Delayed consultations"
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
          </>
        )}

        {isLoading && !overview ? (
          <LoadingState label="Loading hospital-wide queue overview…" />
        ) : isError && !overview ? (
          <ErrorState message={getErrorMessage(error)} onRetry={refetch} />
        ) : !overview || overview.doctors.length === 0 ? (
          <EmptyState
            icon={<Building2 className="h-8 w-8 text-brand-400" />}
            title="No doctors in the catalog yet"
            message="Add doctors from the Manage Doctors screen to start monitoring queues."
          />
        ) : (
          /* Per-doctor table */
          <div className="card overflow-hidden">
            <div className="border-b border-slate-700/50 px-5 py-4">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                All doctors
              </h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:border-slate-700/50 dark:bg-night-700/40 dark:text-slate-500">
                    <th className="px-5 py-3">Doctor</th>
                    <th className="px-5 py-3">Status</th>
                    <th className="px-5 py-3 text-center">Waiting</th>
                    <th className="px-5 py-3 text-center">In consultation</th>
                    <th className="px-5 py-3 text-center">Delayed</th>
                    <th className="px-5 py-3 text-right">Longest wait</th>
                  </tr>
                </thead>
                <tbody>
                  {overview.doctors.map((doc) => (
                    <tr
                      key={doc.doctorCatalogEntryId}
                      className="border-b border-slate-100 transition-colors last:border-0 hover:bg-brand-50/40 dark:border-slate-700/40 dark:hover:bg-brand-500/5"
                    >
                      <td className="px-5 py-4">
                        <p className="font-semibold text-slate-800 dark:text-slate-100">{doc.doctorName || doc.specialization}</p>
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {doc.specialization} · {doc.departmentName} · ≈{doc.avgConsultationTimeMinutes} min/patient
                        </p>
                      </td>
                      <td className="px-5 py-4">
                        <StatusTag status={doc.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                      </td>
                      <td className="px-5 py-4 text-center font-bold text-slate-800 tabular-nums dark:text-slate-100">{doc.waitingCount}</td>
                      <td className="px-5 py-4 text-center font-semibold text-violet-700 tabular-nums dark:text-violet-300">
                        {doc.inProgressCount}
                      </td>
                      <td className="px-5 py-4 text-center">
                        {doc.delayedCount > 0 ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-500/15 dark:text-red-300">
                            ⚠ {doc.delayedCount}
                          </span>
                        ) : (
                          <span className="text-xs text-slate-400">—</span>
                        )}
                      </td>
                      <td className="px-5 py-4 text-right">
                        {doc.longestWaitMinutes != null && doc.longestWaitMinutes > 0 ? (
                          <span
                            className={`font-bold tabular-nums ${
                              doc.longestWaitMinutes > 30 ? 'text-red-600 dark:text-red-300' : 'text-slate-800 dark:text-slate-100'
                            }`}
                          >
                            {doc.longestWaitMinutes} min
                          </span>
                        ) : (
                          <span className="text-xs text-slate-400">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-slate-400 dark:text-slate-600">
          CareQ — SmartOPD AI | Admin live queue overview · delayed = predicted wait &gt; 30 min
        </footer>
      </div>
    </div>
  );
}
