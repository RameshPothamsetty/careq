import type { ReactNode } from 'react';
import { Monitor, Moon, Sun } from 'lucide-react';
import { useTheme, type ThemeMode } from '../context/ThemeContext';

const OPTIONS: { mode: ThemeMode; icon: ReactNode; label: string }[] = [
  { mode: 'auto', icon: <Monitor className="h-4 w-4" />, label: 'Auto (role default)' },
  { mode: 'light', icon: <Sun className="h-4 w-4" />, label: 'Light theme' },
  { mode: 'dark', icon: <Moon className="h-4 w-4" />, label: 'Dark theme' },
];

/**
 * ThemeToggle — compact segmented control (Auto / Light / Dark) for the
 * shared header. Auto follows the role default (dark for staff, light for
 * patients); the other two force the theme everywhere.
 */
export default function ThemeToggle() {
  const { mode, setMode } = useTheme();

  return (
    <div
      className="flex h-10 items-center gap-0.5 rounded-xl border border-slate-200 bg-white p-1 dark:border-slate-600 dark:bg-night-800/70"
      role="group"
      aria-label="Theme"
    >
      {OPTIONS.map((opt) => (
        <button
          key={opt.mode}
          type="button"
          onClick={() => setMode(opt.mode)}
          aria-label={opt.label}
          aria-pressed={mode === opt.mode}
          title={opt.label}
          className={`flex h-8 w-8 items-center justify-center rounded-lg transition-all duration-200 ${
            mode === opt.mode
              ? 'bg-brand-600 text-white shadow-sm'
              : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-700/60 dark:hover:text-slate-100'
          }`}
        >
          {opt.icon}
        </button>
      ))}
    </div>
  );
}
