import { CheckCircle2, Info, X, AlertTriangle } from 'lucide-react';
import { useNotifications } from '../context/NotificationContext';
import { useTheme } from '../context/ThemeContext';

const KIND_ICON = {
  success: <CheckCircle2 className="h-5 w-5 text-emerald-500" />,
  info: <Info className="h-5 w-5 text-sky-500" />,
  warning: <AlertTriangle className="h-5 w-5 text-amber-500" />,
} as const;

const KIND_BORDER = {
  success: 'border-emerald-200',
  info: 'border-sky-200',
  warning: 'border-amber-200',
} as const;

/**
 * Non-blocking, auto-dismissing toast stack (bottom-right). Toasts are
 * derived events pushed by NotificationProvider — no backend involved.
 */
export default function ToastHost() {
  const { toasts, dismissToast } = useNotifications();
  // ToastHost renders OUTSIDE the page root (sibling of <Routes> in App.tsx),
  // so the per-page `dark` class doesn't cover it. Re-apply it here from the
  // shared theme context so toast dark: variants always match the app theme.
  const { isDark } = useTheme();

  if (toasts.length === 0) return null;

  return (
    <div className={`pointer-events-none fixed bottom-4 right-4 z-50 flex w-80 flex-col gap-2 ${isDark ? 'dark' : ''}`}>
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`pointer-events-auto flex items-start gap-3 rounded-2xl border ${KIND_BORDER[toast.kind] ?? 'border-slate-200'} bg-white p-4 shadow-lift animate-toast-in dark:border-slate-700 dark:bg-night-800`}
          role="status"
        >
          {KIND_ICON[toast.kind] ?? KIND_ICON.info}
          <div className="min-w-0 flex-1">
            <p className="text-sm font-bold text-slate-800 dark:text-slate-100">{toast.title}</p>
            <p className="mt-0.5 text-xs leading-relaxed text-slate-500 dark:text-slate-400">{toast.message}</p>
          </div>
          <button
            onClick={() => dismissToast(toast.id)}
            className="shrink-0 rounded-lg p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-300"
            aria-label="Dismiss"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ))}
    </div>
  );
}
