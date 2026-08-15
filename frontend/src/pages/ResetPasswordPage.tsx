import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Activity, KeyRound, MailCheck } from 'lucide-react';
import { api } from '../services/api';
import { useI18n } from '../i18n';
import AuthShell from '../components/AuthShell';
import Button from '../components/ui/Button';

/**
 * ResetPasswordPage (Day 17) — the link target in the password-reset email
 * (?token=...). Sets a new password via POST /api/auth/reset-password.
 */
export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const { t } = useI18n();

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const missingToken = !token;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setMessage('');
    if (password !== confirm) {
      setError(t('auth.passwordMismatch'));
      return;
    }
    if (password.length < 6) {
      setError(t('auth.passwordTooShort'));
      return;
    }
    setIsSubmitting(true);
    try {
      const response = await api.resetPassword(token, password);
      setMessage(response.message || t('auth.resetSuccess'));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t('auth.resetFailed'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell>
      <div className="glass w-full rounded-3xl p-8 shadow-lift sm:p-10">
        <div className="text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-800 shadow-lift">
            <Activity className="h-7 w-7 text-white" />
          </div>
          <h1 className="mt-4 font-display text-2xl font-extrabold tracking-tight text-slate-800 dark:text-slate-100">
            {t('auth.resetTitle')}
          </h1>
        </div>

        {message ? (
          <div className="mt-8 text-center">
            <MailCheck className="mx-auto h-12 w-12 text-emerald-500" />
            <p className="mt-4 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{message}</p>
            <Link to="/login" className="mt-6 inline-block">
              <Button className="w-full px-8 py-3">{t('auth.signIn')}</Button>
            </Link>
          </div>
        ) : missingToken ? (
          <div className="mt-8 text-center">
            <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-300">{t('auth.resetMissingToken')}</p>
            <Link to="/forgot-password" className="mt-6 inline-block">
              <Button className="w-full px-8 py-3">{t('auth.requestNewLink')}</Button>
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="mt-7 space-y-5">
            {error && (
              <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
                ⚠ {error}
              </div>
            )}
            <div>
              <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('auth.newPassword')}</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={t('auth.passwordHint')}
                required
                minLength={6}
                autoComplete="new-password"
                className="input-field"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('auth.confirmPassword')}</label>
              <input
                type="password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                placeholder={t('auth.confirmPassword')}
                required
                autoComplete="new-password"
                className="input-field"
              />
            </div>

            <Button type="submit" loading={isSubmitting} className="w-full py-3 text-base">
              {!isSubmitting && <KeyRound className="h-4 w-4" />}
              {isSubmitting ? t('common.loading') : t('auth.resetPassword')}
            </Button>
          </form>
        )}
      </div>
    </AuthShell>
  );
}
