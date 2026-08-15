import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Activity, MailCheck, MailX } from 'lucide-react';
import { api } from '../services/api';
import { useI18n } from '../i18n';
import AuthShell from '../components/AuthShell';
import Button from '../components/ui/Button';

/**
 * VerifyEmailPage (Day 17) — the link target in the verification email
 * (?token=...). Calls GET /api/auth/verify once on mount and shows the
 * outcome: verified → sign in, invalid/expired → guidance.
 */
export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const { t } = useI18n();

  const [state, setState] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await api.verifyEmail(token);
        if (!cancelled) {
          setMessage(response.message || t('auth.verifySuccess'));
          setState('success');
        }
      } catch (err: unknown) {
        if (!cancelled) {
          setMessage(err instanceof Error ? err.message : t('auth.verifyFailed'));
          setState('error');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
    // Run exactly once with the token from the email link.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <AuthShell>
      <div className="glass w-full rounded-3xl p-8 text-center shadow-lift sm:p-10">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-gradient-to-br from-brand-500 to-brand-800 shadow-lift">
          <Activity className="h-8 w-8 text-white" />
        </div>
        <h1 className="mt-5 font-display text-2xl font-extrabold tracking-tight text-slate-800 dark:text-slate-100">
          {state === 'loading' ? t('auth.verifyTitle') : state === 'success' ? t('auth.verifySuccess') : t('auth.verifyFailedTitle')}
        </h1>

        <div className="mt-6">
          {state === 'loading' && (
            <div className="flex items-center justify-center gap-3 text-slate-500 dark:text-slate-400">
              <span className="h-5 w-5 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
              <span className="text-sm">{t('common.loading')}</span>
            </div>
          )}

          {state === 'success' && (
            <>
              <MailCheck className="mx-auto h-12 w-12 text-emerald-500" />
              <p className="mt-4 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{message}</p>
              <Link to="/login" className="mt-6 inline-block">
                <Button className="w-full px-8 py-3">{t('auth.signIn')}</Button>
              </Link>
            </>
          )}

          {state === 'error' && (
            <>
              <MailX className="mx-auto h-12 w-12 text-red-500" />
              <p className="mt-4 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{message}</p>
              <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">{t('auth.verifyFailedHint')}</p>
              <div className="mt-6 flex flex-col gap-3">
                <Link to="/login" className="inline-block">
                  <Button className="w-full px-8 py-3">{t('auth.signIn')}</Button>
                </Link>
                <Link to="/signup" className="text-sm font-semibold text-brand-600 hover:underline dark:text-brand-400">
                  {t('auth.createOne')}
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </AuthShell>
  );
}
