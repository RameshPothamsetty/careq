import type { ReactNode } from 'react';
import { AlertTriangle, Inbox, Loader2 } from 'lucide-react';
import { useI18n } from '../../i18n';
import Button from './Button';

export function LoadingState({ label }: { label?: string }) {
  const { t } = useI18n();
  return (
    <div className="flex flex-col items-center justify-center py-24">
      <Loader2 className="h-8 w-8 animate-spin text-brand-600 dark:text-brand-400" />
      <p className="mt-4 text-sm text-slate-400 dark:text-slate-500">{label ?? t('common.loading')}</p>
    </div>
  );
}

export function EmptyState({
  message,
  icon,
  title,
  action,
}: {
  message: string;
  icon?: ReactNode;
  title?: string;
  action?: ReactNode;
}) {
  const { t } = useI18n();
  return (
    <div className="card mx-auto max-w-lg p-12 text-center">
      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-brand-50 text-3xl dark:bg-brand-500/15">
        {icon ?? <Inbox className="h-8 w-8 text-brand-400 dark:text-brand-300" />}
      </div>
      <h2 className="mt-4 text-lg font-bold text-slate-800 dark:text-slate-100">{title ?? t('common.emptyDefault')}</h2>
      <p className="mt-1.5 text-sm text-slate-500 dark:text-slate-400">{message}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="card mx-auto max-w-lg p-8 text-center">
      <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-red-100 dark:bg-red-500/15">
        <AlertTriangle className="h-7 w-7 text-red-600 dark:text-red-400" />
      </div>
      <h2 className="mt-4 text-lg font-bold text-slate-800 dark:text-slate-100">{t('common.somethingWentWrong')}</h2>
      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{message}</p>
      {onRetry && (
        <Button variant="primary" className="mt-6" onClick={onRetry}>
          {t('common.tryAgain')}
        </Button>
      )}
    </div>
  );
}
