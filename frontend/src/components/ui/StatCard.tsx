import type { ReactNode } from 'react';

/**
 * StatCard — the Healthray/OPDX-style summary card: white bg, rounded-xl,
 * subtle shadow, one big bold number, small muted label, optional trend.
 */
export default function StatCard({
  label,
  value,
  trend,
  icon,
  accent = 'bg-brand-50 text-brand-700',
}: {
  label: string;
  value: ReactNode;
  trend?: string;
  icon?: ReactNode;
  accent?: string;
}) {
  return (
    <div className="card flex items-center gap-4 p-5 !rounded-xl transition-all duration-200 hover:shadow-lift">
      {icon && (
        <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl text-xl ${accent}`}>
          {icon}
        </div>
      )}
      <div className="min-w-0">
        <div className="text-3xl font-extrabold leading-none tracking-tight text-slate-800">{value}</div>
        <div className="mt-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400">{label}</div>
        {trend && (
          <div className="mt-1 text-xs font-semibold text-emerald-600">{trend}</div>
        )}
      </div>
    </div>
  );
}
