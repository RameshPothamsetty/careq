import type { TriageLevel } from '../services/api';

// Urgency color coding — EMERGENCY = red, HIGH = orange, NORMAL = blue/green, FOLLOW_UP = gray.
const TRIAGE_STYLES: Record<TriageLevel, { label: string; classes: string; dot: string }> = {
  EMERGENCY: {
    label: 'Emergency',
    classes: 'bg-red-100 text-red-700 border-red-200',
    dot: 'bg-red-500',
  },
  HIGH: {
    label: 'High',
    classes: 'bg-orange-100 text-orange-700 border-orange-200',
    dot: 'bg-orange-500',
  },
  NORMAL: {
    label: 'Normal',
    classes: 'bg-emerald-100 text-emerald-700 border-emerald-200',
    dot: 'bg-emerald-500',
  },
  FOLLOW_UP: {
    label: 'Follow-up',
    classes: 'bg-slate-100 text-slate-600 border-slate-200',
    dot: 'bg-slate-400',
  },
};

export default function TriageBadge({
  level,
  showDot = true,
  className = '',
}: {
  level: TriageLevel;
  showDot?: boolean;
  className?: string;
}) {
  const style = TRIAGE_STYLES[level];
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold ${style.classes} ${className}`}
    >
      {showDot && <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} />}
      {style.label}
    </span>
  );
}
