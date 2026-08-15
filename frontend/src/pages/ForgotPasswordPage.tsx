import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Activity, MailCheck, Send } from 'lucide-react';
import { api } from '../services/api';
import { useI18n } from '../i18n';
import AuthShell from '../components/AuthShell';
import Button from '../components/ui/Button';

/**
 * ForgotPasswordPage — enter the account email, get a one-time
 * reset link. Always shows the generic outcome message (the backend never
 * reveals whether the email exists).
 */
export default function ForgotPasswordPage() {
  const { t } = useI18n();
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setIsSubmitting(true);
    try {
      const response = await api.forgotPassword(email);
      setMessage(response.message || t('auth.forgotSent'));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t('auth.forgotFailed'));
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
            {t('auth.forgotTitle')}
          </h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{t('auth.forgotDesc')}</p>
        </div>

        {message ? (
          <div className="mt-8 text-center">
            <MailCheck className="mx-auto h-12 w-12 text-emerald-500" />
            <p className="mt-4 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{message}</p>
            <Link to="/login" className="mt-6 inline-block">
              <Button className="w-full px-8 py-3">{t('auth.backToSignIn')}</Button>
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
              <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('auth.email')}</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder={t('auth.emailPlaceholder')}
                required
                autoComplete="email"
                className="input-field"
              />
            </div>

            <Button type="submit" loading={isSubmitting} className="w-full py-3 text-base">
              {!isSubmitting && <Send className="h-4 w-4" />}
              {isSubmitting ? t('common.loading') : t('auth.sendResetLink')}
            </Button>

            <p className="text-center text-sm text-slate-500 dark:text-slate-400">
              <Link to="/login" className="font-semibold text-brand-600 hover:underline dark:text-brand-400">
                {t('auth.backToSignIn')}
              </Link>
            </p>
          </form>
        )}
      </div>
    </AuthShell>
  );
}
