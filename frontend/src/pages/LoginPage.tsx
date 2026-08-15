import { useState, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, LogIn, MailCheck } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { useI18n } from '../i18n';
import AuthShell from '../components/AuthShell';
import Button from '../components/ui/Button';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const { t } = useI18n();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  // A 403 EMAIL_NOT_VERIFIED switches the card to a "verify your
  // email" prompt with a resend button instead of a generic error.
  const [verifyPrompt, setVerifyPrompt] = useState(false);
  const [resendMsg, setResendMsg] = useState('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setResendMsg('');
    setVerifyPrompt(false);
    setIsSubmitting(true);

    try {
      await login(email, password);
      const role = localStorage.getItem('careq_user')
        ? JSON.parse(localStorage.getItem('careq_user')!).role
        : null;
      navigate(`/${role?.toLowerCase() || 'patient'}/dashboard`, { replace: true });
    } catch (err: unknown) {
      const code = (err as { code?: string }).code;
      if (code === 'EMAIL_NOT_VERIFIED') {
        setVerifyPrompt(true);
        setError(err instanceof Error ? err.message : t('auth.verifyLoginBlocked'));
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(t('auth.loginFailed'));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResend = async () => {
    setResendMsg('');
    try {
      const response = await api.resendVerification(email);
      setResendMsg(response.message || t('auth.resendSent'));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t('auth.resendFailed'));
    }
  };

  return (
    <AuthShell>
      <div className="glass w-full rounded-3xl p-8 shadow-lift sm:p-10">
        <div className="text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-gradient-to-br from-brand-500 to-brand-800 shadow-lift">
            <Activity className="h-8 w-8 text-white" />
          </div>
          <h1 className="mt-5 font-display text-3xl font-extrabold tracking-tight text-slate-800 dark:text-slate-100">CareQ</h1>
          <p className="mt-1.5 text-sm text-slate-500 dark:text-slate-400">{t('auth.signInToContinue')}</p>
        </div>

        {error && (
          <div className="mt-6 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            ⚠ {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-7 space-y-5">
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

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('auth.password')}</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={t('auth.passwordPlaceholder')}
              required
              autoComplete="current-password"
              className="input-field"
            />
          </div>

          <Button type="submit" loading={isSubmitting} className="w-full py-3 text-base">
            {!isSubmitting && <LogIn className="h-4 w-4" />}
            {isSubmitting ? t('auth.signingIn') : t('auth.signIn')}
          </Button>
        </form>

        <div className="mt-4 flex items-center justify-between text-sm">
          <Link to="/forgot-password" className="font-semibold text-brand-600 hover:underline dark:text-brand-400">
            {t('auth.forgotPassword')}
          </Link>
        </div>

        <p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">
          {t('auth.noAccount')}{' '}
          <Link to="/signup" className="font-semibold text-brand-600 hover:text-brand-700 hover:underline dark:text-brand-400 dark:hover:text-brand-300">
            {t('auth.createOne')}
          </Link>
        </p>
      </div>

      {verifyPrompt && (
        <div className="glass mt-6 w-full rounded-3xl p-8 text-center shadow-lift sm:p-10">
          <MailCheck className="mx-auto h-12 w-12 text-emerald-500" />
          <h2 className="mt-4 font-display text-xl font-extrabold tracking-tight text-slate-800 dark:text-slate-100">
            {t('auth.verifyYourEmail')}
          </h2>
          <p className="mt-3 text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t('auth.checkYourEmailDesc', { email })}
          </p>
          {(resendMsg || error) && (
            <p className={`mt-3 text-sm font-medium ${error && !resendMsg ? 'text-red-600 dark:text-red-400' : 'text-emerald-600 dark:text-emerald-400'}`}>
              {resendMsg || error}
            </p>
          )}
          <Button variant="secondary" onClick={handleResend} className="mt-6 w-full py-3 text-base">
            {t('auth.resendEmail')}
          </Button>
          <button
            type="button"
            onClick={() => setVerifyPrompt(false)}
            className="mt-4 text-sm font-semibold text-brand-600 hover:underline dark:text-brand-400"
          >
            {t('auth.backToSignIn')}
          </button>
        </div>
      )}
    </AuthShell>
  );
}
