import type { ReactNode } from 'react';

/**
 * StatCard — the summary card: layered surface, one big bold number
 * (tabular numerals), small muted label, optional trend + icon tile.
 * Dark-aware so the same component works on the staff (dark) dashboards.
 */
export default function StatCard({
  label,
  value,
  trend,
  icon,
  accent = 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300',
}: {
  label: string;
  value: ReactNode;
  trend?: string;
  icon?: ReactNode;
  accent?: string;
}) {
  return (
    <div className="card flex items-center gap-4 p-5 !rounded-xl transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lift">
      {icon && (
        <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl text-xl ${accent}`}>
          {icon}
        </div>
      )}
      <div className="min-w-0">
        <div className="text-3xl font-extrabold leading-none tracking-tight text-slate-800 tabular-nums dark:text-slate-100">
          {value}
        </div>
        <div className="mt-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">
          {label}
        </div>
        {trend && (
          <div className="mt-1 text-xs font-semibold text-emerald-600 dark:text-emerald-400">{trend}</div>
        )}
      </div>
    </div>
  );
}
