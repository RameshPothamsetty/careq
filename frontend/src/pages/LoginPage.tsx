import { useState, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, LogIn } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
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

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      await login(email, password);
      const role = localStorage.getItem('careq_user')
        ? JSON.parse(localStorage.getItem('careq_user')!).role
        : null;
      navigate(`/${role?.toLowerCase() || 'patient'}/dashboard`, { replace: true });
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(t('auth.loginFailed'));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell>
      <div className="glass w-full rounded-3xl p-8 shadow-lift sm:p-10">
        <div className="text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-gradient-to-br from-brand-500 to-brand-800 shadow-lift">
            <Activity className="h-8 w-8 text-white" />
          </div>
          <h1 className="mt-5 font-display text-3xl font-extrabold tracking-tight text-slate-800">CareQ</h1>
          <p className="mt-1.5 text-sm text-slate-500">{t('auth.signInToContinue')}</p>
        </div>

        {error && (
          <div className="mt-6 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            ⚠ {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-7 space-y-5">
          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">{t('auth.email')}</label>
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
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">{t('auth.password')}</label>
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

        <p className="mt-7 text-center text-sm text-slate-500">
          {t('auth.noAccount')}{' '}
          <Link to="/signup" className="font-semibold text-brand-600 hover:text-brand-700 hover:underline">
            {t('auth.createOne')}
          </Link>
        </p>
      </div>
    </AuthShell>
  );
}
