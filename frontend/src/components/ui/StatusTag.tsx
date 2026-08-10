/**
 * StatusTag — the single colored pill used across the app for:
 * - triage levels:  EMERGENCY / HIGH / NORMAL / FOLLOW_UP
 * - queue statuses: WAITING / IN_PROGRESS / COMPLETED / CANCELLED
 * - availability:   ONLINE / OFFLINE / ACTIVE / INACTIVE
 *
 * Colors follow the rule: green = good, amber = warning, red = urgent (rare).
 */
const STATUS_STYLES: Record<string, { label: string; classes: string; dot: string }> = {
  // Triage
  EMERGENCY: { label: 'Emergency', classes: 'bg-red-100 text-red-700 border-red-200 dark:bg-red-500/10 dark:text-red-400 dark:border-red-500/25', dot: 'bg-red-500' },
  HIGH: { label: 'High', classes: 'bg-orange-100 text-orange-700 border-orange-200 dark:bg-orange-500/10 dark:text-orange-400 dark:border-orange-500/25', dot: 'bg-orange-500' },
  NORMAL: { label: 'Normal', classes: 'bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-400 dark:border-emerald-500/25', dot: 'bg-emerald-500' },
  FOLLOW_UP: { label: 'Follow-up', classes: 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-500/10 dark:text-slate-400 dark:border-slate-500/25', dot: 'bg-slate-400' },
  // Queue statuses
  WAITING: { label: 'In queue', classes: 'bg-sky-100 text-sky-700 border-sky-200 dark:bg-sky-500/10 dark:text-sky-400 dark:border-sky-500/25', dot: 'bg-sky-500' },
  IN_PROGRESS: { label: 'In consultation', classes: 'bg-violet-100 text-violet-700 border-violet-200 dark:bg-violet-500/10 dark:text-violet-400 dark:border-violet-500/25', dot: 'bg-violet-500' },
  COMPLETED: { label: 'Completed', classes: 'bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-400 dark:border-emerald-500/25', dot: 'bg-emerald-500' },
  CANCELLED: { label: 'Cancelled', classes: 'bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-500/10 dark:text-slate-400 dark:border-slate-500/25', dot: 'bg-slate-400' },
  // Availability / active flags
  ONLINE: { label: 'Online', classes: 'bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-400 dark:border-emerald-500/25', dot: 'bg-emerald-500' },
  OFFLINE: { label: 'Offline', classes: 'bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-500/10 dark:text-slate-400 dark:border-slate-500/25', dot: 'bg-slate-400' },
  ACTIVE: { label: 'Active', classes: 'bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-400 dark:border-emerald-500/25', dot: 'bg-emerald-500' },
  INACTIVE: { label: 'Inactive', classes: 'bg-red-100 text-red-700 border-red-200 dark:bg-red-500/10 dark:text-red-400 dark:border-red-500/25', dot: 'bg-red-500' },
};

export default function StatusTag({
  status,
  showDot = true,
  className = '',
}: {
  status: string;
  showDot?: boolean;
  className?: string;
}) {
  const style = STATUS_STYLES[status] ?? {
    label: status,
    classes: 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-500/10 dark:text-slate-400 dark:border-slate-500/25',
    dot: 'bg-slate-400',
  };
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold ${style.classes} ${className}`}
    >
      {showDot && <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} />}
      {style.label}
    </span>
  );
}
