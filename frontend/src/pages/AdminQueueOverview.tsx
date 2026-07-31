import { useCallback, useEffect, useState } from 'react';
import { api, type LiveQueueOverview } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';

const POLL_INTERVAL_MS = 10_000;

function Spinner() {
  return (
    <div className="flex flex-col items-center justify-center py-24">
      <div className="h-10 w-10 animate-spin rounded-full border-4 border-brand-200 border-t-brand-600" />
      <p className="mt-4 text-sm text-ink-muted">Loading hospital-wide queue overview…</p>
    </div>
  );
}

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

  const card = (label: string, value: number | string, icon: string, accent: string) => (
    <div className="card flex items-center gap-4 p-5 transition-all duration-200 hover:shadow-lift">
      <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl text-xl ${accent}`}>
        {icon}
      </div>
      <div className="min-w-0">
        <div className="text-3xl font-extrabold leading-none tracking-tight text-ink">{value}</div>
        <div className="mt-1.5 text-[11px] font-semibold uppercase tracking-wider text-ink-muted">{label}</div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-surface px-4 py-6 sm:px-6">
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
              <div className="flex items-center gap-2 text-xs font-medium text-ink-muted">
                <span className="h-2.5 w-2.5 animate-pulse-dot rounded-full bg-brand-500" />
                <span>Live — refreshing every 10s</span>
              </div>
              <span className="text-xs font-medium text-ink-muted">Last updated {secondsAgo}s ago</span>
            </div>

            {/* Summary cards — the screenshot-worthy part */}
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5 sm:gap-4">
              {card('Patients waiting', overview.totalWaiting, '🕐', 'bg-sky-100')}
              {card('In consultation', overview.totalInProgress, '🩺', 'bg-violet-100')}
              {card('Doctors online', overview.doctorsOnline, '🟢', 'bg-emerald-100')}
              {card('Delayed consultations', overview.delayedConsultations, '⚠️', overview.delayedConsultations > 0 ? 'bg-red-100' : 'bg-emerald-100')}
              {card('Avg wait (min)', overview.averageWaitMinutes, '📊', 'bg-amber-100')}
            </div>
          </>
        )}

        {isLoading ? (
          <Spinner />
        ) : error ? (
          <div className="card mx-auto max-w-lg p-8 text-center">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-red-100 text-2xl">
              ⚠️
            </div>
            <h2 className="mt-4 text-lg font-bold text-ink">Could not load overview</h2>
            <p className="mt-1 text-sm text-ink-muted">{error}</p>
            <button onClick={() => setRefreshKey((k) => k + 1)} className="btn-primary mt-6">
              Try again
            </button>
          </div>
        ) : !overview || overview.doctors.length === 0 ? (
          <div className="card mx-auto max-w-lg p-12 text-center">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-brand-50 text-3xl">
              🏥
            </div>
            <h2 className="mt-4 text-lg font-bold text-ink">No doctors in the catalog yet</h2>
            <p className="mt-1.5 text-sm text-ink-muted">
              Add doctors from the Manage Doctors screen to start monitoring queues.
            </p>
          </div>
        ) : (
          /* Per-doctor table */
          <div className="card overflow-hidden">
            <div className="border-b border-slate-100 px-5 py-4">
              <h2 className="text-sm font-bold uppercase tracking-wide text-ink-muted">
                All doctors
              </h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-[11px] font-semibold uppercase tracking-wider text-ink-muted">
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
                        <p className="font-semibold text-ink">{doc.specialization}</p>
                        <p className="text-xs text-ink-muted">
                          {doc.departmentName} · ≈{doc.avgConsultationTimeMinutes} min/patient
                        </p>
                      </td>
                      <td className="px-5 py-4">
                        {doc.isAvailable ? (
                          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">
                            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" /> Online
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-500">
                            <span className="h-1.5 w-1.5 rounded-full bg-slate-400" /> Offline
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-4 text-center font-bold text-ink">{doc.waitingCount}</td>
                      <td className="px-5 py-4 text-center font-semibold text-violet-600">
                        {doc.inProgressCount}
                      </td>
                      <td className="px-5 py-4 text-center">
                        {doc.delayedCount > 0 ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-700">
                            ⚠ {doc.delayedCount}
                          </span>
                        ) : (
                          <span className="text-xs text-ink-muted">—</span>
                        )}
                      </td>
                      <td className="px-5 py-4 text-right">
                        {doc.longestWaitMinutes != null && doc.longestWaitMinutes > 0 ? (
                          <span
                            className={`font-bold ${
                              doc.longestWaitMinutes > 30 ? 'text-red-600' : 'text-ink'
                            }`}
                          >
                            {doc.longestWaitMinutes} min
                          </span>
                        ) : (
                          <span className="text-xs text-ink-muted">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-ink-muted">
          CareQ — SmartOPD AI | Admin live queue overview · delayed = predicted wait &gt; 30 min
        </footer>
      </div>
    </div>
  );
}
