import { Activity, Building2, CalendarDays, Clock, TrendingUp } from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import { useGetAnalyticsSummaryQuery } from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatCard } from '../components/ui';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

const CHART_COLORS = ['#0e7490', '#0284c7', '#7c3aed', '#059669', '#d97706', '#e11d48', '#64748b'];

const formatDay = (iso: string) => {
  const d = new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
};

/** Friendly short label for chart axes: "Mon 4" style. */
const shortDay = (iso: string) => {
  const d = new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString(undefined, { day: 'numeric' });
};

export default function AdminAnalytics() {
  const { data, isLoading, isError, error, refetch } = useGetAnalyticsSummaryQuery();

  // Loading / error states first — same discipline as every other screen.
  if (isLoading && !data) {
    return (
      <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
        <div className="mx-auto max-w-6xl space-y-6">
          <QueuePageHeader
            icon="📊"
            title="Analytics Dashboard"
            subtitle="7-day trends: patient flow, wait times & department load"
            dashboardPath="/admin"
          />
          <LoadingState label="Aggregating the last 7 days…" />
        </div>
      </div>
    );
  }

  if (isError && !data) {
    return (
      <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
        <div className="mx-auto max-w-6xl space-y-6">
          <QueuePageHeader
            icon="📊"
            title="Analytics Dashboard"
            subtitle="7-day trends: patient flow, wait times & department load"
            dashboardPath="/admin"
          />
          <ErrorState message={getErrorMessage(error)} onRetry={refetch} />
        </div>
      </div>
    );
  }

  const totalPatients = (data?.patientsPerDay ?? []).reduce((sum, d) => sum + d.count, 0);
  const waits = (data?.avgWaitTimeTrend ?? [])
    .map((d) => d.avgWaitMinutes)
    .filter((w): w is number => w !== null && w !== undefined);
  const avgWait = waits.length ? Math.round(waits.reduce((a, b) => a + b, 0) / waits.length) : null;
  const busiest = (data?.departmentDistribution ?? []).reduce<{ name: string; count: number } | null>(
    (best, d) => (!best || d.patientCount > best.count ? { name: d.departmentName, count: d.patientCount } : best),
    null,
  );

  // Empty state: no patient activity in the window at all.
  const hasAnyData = totalPatients > 0 || (data?.departmentDistribution.length ?? 0) > 0;

  // Keep null for days with no calls — Recharts draws a gap (connectNulls
  // defaults to false) rather than a misleading 0-minute dip.
  const waitData = (data?.avgWaitTimeTrend ?? []).map((d) => ({
    ...d,
    label: formatDay(d.date),
    day: shortDay(d.date),
    avgWaitMinutes: d.avgWaitMinutes,
  }));

  const patientData = (data?.patientsPerDay ?? []).map((d) => ({
    ...d,
    label: formatDay(d.date),
    day: shortDay(d.date),
  }));

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-6xl space-y-6">
        <QueuePageHeader
          icon="📊"
          title="Analytics Dashboard"
          subtitle="7-day trends: patient flow, wait times & department load"
          dashboardPath="/admin"
        />

        {isError && data && (
          <div className="flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-700">
            ⚠ Showing the last known summary — a refresh just failed.
          </div>
        )}

        {/* Summary numbers above the charts */}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
          <StatCard
            label="Patients handled (7d)"
            value={totalPatients}
            icon={<CalendarDays className="h-5 w-5" />}
            accent="bg-cyan-50 text-cyan-700"
          />
          <StatCard
            label="Avg wait time (7d)"
            value={avgWait === null ? '—' : `${avgWait} min`}
            icon={<Clock className="h-5 w-5" />}
            accent="bg-amber-50 text-amber-700"
          />
          <StatCard
            label="Busiest department"
            value={busiest?.name ?? '—'}
            icon={<Building2 className="h-5 w-5" />}
            accent="bg-violet-50 text-violet-700"
          />
        </div>

        {!hasAnyData ? (
          <EmptyState
            icon={<Activity className="h-8 w-8 text-brand-400" />}
            title="No patient activity yet"
            message="Completed consultations and calls from the last 7 days will appear here as charts once patients start flowing through queues."
          />
        ) : (
          <div className="grid gap-5 lg:grid-cols-2">
            {/* Patients handled per day */}
            <div className="card p-5">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                Patients handled per day
              </h2>
              <div className="mt-4 h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={patientData} margin={{ top: 4, right: 8, left: -18, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                    <XAxis
                      dataKey="day"
                      tick={{ fontSize: 12, fill: '#94a3b8' }}
                      axisLine={{ stroke: '#e2e8f0' }}
                      tickLine={false}
                    />
                    <YAxis
                      allowDecimals={false}
                      tick={{ fontSize: 12, fill: '#94a3b8' }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      cursor={{ fill: '#f1f5f9' }}
                      contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', fontSize: 13 }}
                      labelFormatter={(_, payload) => payload?.[0]?.payload?.label ?? ''}
                    />
                    <Bar dataKey="count" name="Patients" radius={[6, 6, 0, 0]} fill="#0e7490" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Average wait trend */}
            <div className="card p-5">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                Average wait time trend
              </h2>
              <div className="mt-4 h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={waitData} margin={{ top: 4, right: 8, left: -18, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                    <XAxis
                      dataKey="day"
                      tick={{ fontSize: 12, fill: '#94a3b8' }}
                      axisLine={{ stroke: '#e2e8f0' }}
                      tickLine={false}
                    />
                    <YAxis
                      tick={{ fontSize: 12, fill: '#94a3b8' }}
                      axisLine={false}
                      tickLine={false}
                      unit="m"
                    />
                    <Tooltip
                      contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', fontSize: 13 }}
                      formatter={(value) => [`${value} min`, 'Avg wait']}
                      labelFormatter={(_, payload) => payload?.[0]?.payload?.label ?? ''}
                    />
                    <Line
                      type="monotone"
                      dataKey="avgWaitMinutes"
                      name="Avg wait"
                      stroke="#7c3aed"
                      strokeWidth={2.5}
                      dot={{ r: 3.5, fill: '#7c3aed', strokeWidth: 0 }}
                      activeDot={{ r: 5 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Department distribution */}
            <div className="card p-5 lg:col-span-2">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-400">
                Queue distribution by department
              </h2>
              {data?.departmentDistribution.length === 0 ? (
                <div className="flex h-56 flex-col items-center justify-center text-center">
                  <TrendingUp className="h-8 w-8 text-slate-300" />
                  <p className="mt-3 text-sm text-slate-400">
                    No completed consultations to break down by department yet.
                  </p>
                </div>
              ) : (
                <div className="mt-4 flex flex-col items-center gap-6 sm:flex-row sm:justify-center">
                  <div className="h-64 w-full max-w-xs">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={data?.departmentDistribution ?? []}
                          dataKey="patientCount"
                          nameKey="departmentName"
                          innerRadius={55}
                          outerRadius={95}
                          paddingAngle={3}
                          stroke="#ffffff"
                        >
                          {data?.departmentDistribution.map((_, i) => (
                            <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip
                          contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', fontSize: 13 }}
                          formatter={(value, name) => [value, name]}
                        />
                        <Legend
                          iconType="circle"
                          formatter={(value: string) => (
                            <span className="text-xs font-medium text-slate-600">{value}</span>
                          )}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Analytics over the last 7 days · patients handled = completed consultations
        </footer>
      </div>
    </div>
  );
}
