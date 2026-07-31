import { useCallback, useEffect, useState } from 'react';
import { Activity, AlertTriangle, Building2, Clock, Stethoscope, Users } from 'lucide-react';
import { api, type LiveQueueOverview } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatCard, LiveBadge, StatusTag } from '../components/ui';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

const POLL_INTERVAL_MS = 10_000;

export default function AdminQueueOverview() {
  const [overview, setOverview] = useState<LiveQueueOverview | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [lastUpdatedAt, setLastUpdatedAt] = useState(Date.now());
  const [refreshKey, setRefreshKey] = useState(0);

  const fetchOverview = useCallback(async () => {
    try {
      const data = await api.getLiveQueueOverview();
      setOverview(data);
      setLastUpdatedAt(Date.now());
      setError('');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load live overview');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchOverview();
  }, [fetchOverview, refreshKey]);

  useEffect(() => {
    const interval = setInterval(fetchOverview, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [fetchOverview]);

  const secondsAgo = Math.max(0, Math.round((Date.now() - lastUpdatedAt) / 1000));

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-6xl space-y-6">
        <QueuePageHeader
          icon="🏥"
          title="Live Queue Overview"
          subtitle="Hospital-wide patient flow, delays & doctor load"
          dashboardPath="/admin"
        />

        {!isLoading && !error && overview && (
          <>
            {/* Live indicator */}
            <div className="flex items-center justify-between px-1">
              <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
                <LiveBadge lastUpdatedSeconds={secondsAgo} />
                <span>Refreshing every 10s</span>
              </div>
            </div>

            {/* Summary cards — the screenshot-worthy part */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5 sm:gap-4">
              <StatCard
                label="Patients waiting"
                value={overview.totalWaiting}
                icon={<Users className="h-5 w-5" />}
                accent="bg-sky-50 text-sky-700"
              />
              <StatCard
                label="In consultation"
                value={overview.totalInProgress}
                icon={<Stethoscope className="h-5 w-5" />}
                accent="bg-violet-50 text-violet-700"
              />
              <StatCard
                label="Doctors online"
                value={overview.doctorsOnline}
                icon={<Activity className="h-5 w-5" />}
                accent="bg-emerald-50 text-emerald-700"
              />
              <StatCard
                label="Delayed consultations"
                value={overview.delayedConsultations}
                icon={<AlertTriangle className="h-5 w-5" />}
                accent={
                  overview.delayedConsultations > 0
                    ? 'bg-red-50 text-red-700'
                    : 'bg-emerald-50 text-emerald-700'
                }
              />
              <StatCard
                label="Avg wait (min)"
                value={overview.averageWaitMinutes}
                icon={<Clock className="h-5 w-5" />}
                accent="bg-amber-50 text-amber-700"
              />
            </div>
          </>
        )}

        {isLoading ? (
          <LoadingState label="Loading hospital-wide queue overview…" />
        ) : error ? (
          <ErrorState message={error} onRetry={() => setRefreshKey((k) => k + 1)} />
        ) : !overview || overview.doctors.length === 0 ? (
          <EmptyState
            icon={<Building2 className="h-8 w-8 text-brand-400" />}
            title="No doctors in the catalog yet"
            message="Add doctors from the Manage Doctors screen to start monitoring queues."
          />
        ) : (
          /* Per-doctor table */
          <div className="card overflow-hidden">
            <div className="border-b border-slate-100 px-5 py-4">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                All doctors
              </h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
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
                      className="border-b border-slate-50 transition-colors last:border-0 hover:bg-brand-50/40"
                    >
                      <td className="px-5 py-4">
                        <p className="font-semibold text-slate-800">{doc.specialization}</p>
                        <p className="text-xs text-slate-400">
                          {doc.departmentName} · ≈{doc.avgConsultationTimeMinutes} min/patient
                        </p>
                      </td>
                      <td className="px-5 py-4">
                        <StatusTag status={doc.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                      </td>
                      <td className="px-5 py-4 text-center font-bold text-slate-800">{doc.waitingCount}</td>
                      <td className="px-5 py-4 text-center font-semibold text-violet-600">
                        {doc.inProgressCount}
                      </td>
                      <td className="px-5 py-4 text-center">
                        {doc.delayedCount > 0 ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-700">
                            ⚠ {doc.delayedCount}
                          </span>
                        ) : (
                          <span className="text-xs text-slate-400">—</span>
                        )}
                      </td>
                      <td className="px-5 py-4 text-right">
                        {doc.longestWaitMinutes != null && doc.longestWaitMinutes > 0 ? (
                          <span
                            className={`font-bold ${
                              doc.longestWaitMinutes > 30 ? 'text-red-600' : 'text-slate-800'
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

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Admin live queue overview · delayed = predicted wait &gt; 30 min
        </footer>
      </div>
    </div>
  );
}
