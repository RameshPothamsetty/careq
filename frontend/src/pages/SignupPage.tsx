import { useState, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, MailCheck, UserPlus } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { useI18n } from '../i18n';
import AuthShell from '../components/AuthShell';
import Button from '../components/ui/Button';

const ROLES = [
  { value: 'PATIENT', icon: '👤', labelKey: 'auth.rolePatient', descKey: 'auth.rolePatientDesc' },
  { value: 'DOCTOR', icon: '🩺', labelKey: 'auth.roleDoctor', descKey: 'auth.roleDoctorDesc' },
  { value: 'ADMIN', icon: '⚙️', labelKey: 'auth.roleAdmin', descKey: 'auth.roleAdminDesc' },
] as const;

export default function SignupPage() {
  const navigate = useNavigate();
  const { signup } = useAuth();
  const { t } = useI18n();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('PATIENT');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  // Day 17 — after signup, production requires the verification email
  // before any session exists, so the page switches to a "check your
  // inbox" screen instead of auto-navigating to the dashboard.
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);
  const [resendMsg, setResendMsg] = useState('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      const response = await signup(fullName, email, password, role);
      if (response.verificationRequired) {
        setRegisteredEmail(email);
      } else {
        navigate(`/${role.toLowerCase()}/dashboard`, { replace: true });
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(t('auth.signupFailed'));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResend = async () => {
    if (!registeredEmail) return;
    setResendMsg('');
    try {
      const response = await api.resendVerification(registeredEmail);
      setResendMsg(response.message || t('auth.resendSent'));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t('auth.resendFailed'));
    }
  };

  const selectedRole = ROLES.find((r) => r.value === role);

  return (
    <AuthShell>
      <div className="glass w-full rounded-3xl p-8 shadow-lift sm:p-10">
        <div className="text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-800 shadow-lift">
            <Activity className="h-7 w-7 text-white" />
          </div>
          <h1 className="mt-4 font-display text-2xl font-extrabold tracking-tight text-slate-800 dark:text-slate-100">{t('auth.createAccount')}</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{t('auth.joinCareQ')}</p>
        </div>

        {error && (
          <div className="mt-6 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            ⚠ {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-5">
          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('auth.fullName')}</label>
            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder={t('auth.fullNamePlaceholder')}
              required
              className="input-field"
            />
          </div>

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
              placeholder={t('auth.passwordHint')}
              required
              minLength={6}
              autoComplete="new-password"
              className="input-field"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('auth.iamA')}</label>
            <div className="grid grid-cols-3 gap-2">
              {ROLES.map((r) => (
                <button
                  key={r.value}
                  type="button"
                  onClick={() => setRole(r.value)}
                  className={`flex flex-col items-center gap-1 rounded-xl border-2 px-2 py-3 text-center transition-all duration-200 ${
                    role === r.value
                      ? 'border-brand-500 bg-brand-50 dark:bg-brand-500/15'
                      : 'border-slate-200 bg-white hover:border-brand-300 dark:border-slate-600 dark:bg-night-800 dark:hover:border-brand-400'
                  }`}
                >
                  <span className="text-xl">{r.icon}</span>
                  <span className={`text-xs font-semibold ${role === r.value ? 'text-brand-700 dark:text-brand-300' : 'text-slate-600 dark:text-slate-300'}`}>
                    {t(r.labelKey)}
                  </span>
                </button>
              ))}
            </div>
            <p className="mt-1.5 text-xs text-slate-400 dark:text-slate-500">
              {selectedRole ? t(selectedRole.descKey) : ''}
            </p>
          </div>

          <Button type="submit" loading={isSubmitting} className="w-full py-3 text-base">
            {!isSubmitting && <UserPlus className="h-4 w-4" />}
            {isSubmitting ? t('auth.creatingAccount') : t('auth.createAccount')}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">
          {t('auth.alreadyHaveAccount')}{' '}
          <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700 hover:underline dark:text-brand-400 dark:hover:text-brand-300">
            {t('auth.signIn')}
          </Link>
        </p>
      </div>

      {registeredEmail && (
        <div className="glass mt-6 w-full rounded-3xl p-8 text-center shadow-lift sm:p-10">
          <MailCheck className="mx-auto h-12 w-12 text-emerald-500" />
          <h2 className="mt-4 font-display text-xl font-extrabold tracking-tight text-slate-800 dark:text-slate-100">
            {t('auth.checkYourEmail')}
          </h2>
          <p className="mt-3 text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t('auth.checkYourEmailDesc', { email: registeredEmail })}
          </p>
          {(resendMsg || error) && (
            <p className={`mt-3 text-sm font-medium ${error ? 'text-red-600 dark:text-red-400' : 'text-emerald-600 dark:text-emerald-400'}`}>
              {error || resendMsg}
            </p>
          )}
          <Button variant="secondary" onClick={handleResend} className="mt-6 w-full py-3 text-base">
            {t('auth.resendEmail')}
          </Button>
          <Link to="/login" className="mt-4 inline-block text-sm font-semibold text-brand-600 hover:underline dark:text-brand-400">
            {t('auth.backToSignIn')}
          </Link>
        </div>
      )}
    </AuthShell>
  );
}
