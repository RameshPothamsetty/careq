import { useState, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, UserPlus } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import Button from '../components/ui/Button';

const ROLES = [
  { value: 'PATIENT', label: 'Patient', icon: '👤', desc: 'Browse doctors & join queues' },
  { value: 'DOCTOR', label: 'Doctor', icon: '🩺', desc: 'Manage your live queue' },
  { value: 'ADMIN', label: 'Admin', icon: '⚙️', desc: 'Departments, doctors & oversight' },
];

export default function SignupPage() {
  const navigate = useNavigate();
  const { signup } = useAuth();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('PATIENT');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      await signup(fullName, email, password, role);
      navigate(`/${role.toLowerCase()}/dashboard`, { replace: true });
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Signup failed. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-gray-50 px-4 py-10">
      {/* Soft decorative blobs */}
      <div className="pointer-events-none absolute -left-24 -top-24 h-72 w-72 rounded-full bg-brand-200/40 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-28 -right-20 h-80 w-80 rounded-full bg-sky-200/30 blur-3xl" />

      <div className="card w-full max-w-md animate-fade-in-up p-8 sm:p-10">
        <div className="text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 shadow-lift">
            <Activity className="h-7 w-7 text-white" />
          </div>
          <h1 className="mt-4 text-2xl font-extrabold tracking-tight text-slate-800">Create Account</h1>
          <p className="mt-1 text-sm text-slate-500">Join CareQ — SmartOPD AI</p>
        </div>

        {error && (
          <div className="mt-6 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            ⚠ {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-5">
          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">Full Name</label>
            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Dr. John Doe"
              required
              className="input-field"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="john@hospital.com"
              required
              autoComplete="email"
              className="input-field"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 6 characters"
              required
              minLength={6}
              autoComplete="new-password"
              className="input-field"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">I am a…</label>
            <div className="grid grid-cols-3 gap-2">
              {ROLES.map((r) => (
                <button
                  key={r.value}
                  type="button"
                  onClick={() => setRole(r.value)}
                  className={`flex flex-col items-center gap-1 rounded-xl border-2 px-2 py-3 text-center transition-all duration-200 ${
                    role === r.value
                      ? 'border-brand-500 bg-brand-50'
                      : 'border-slate-200 bg-white hover:border-brand-300'
                  }`}
                >
                  <span className="text-xl">{r.icon}</span>
                  <span className={`text-xs font-semibold ${role === r.value ? 'text-brand-700' : 'text-slate-600'}`}>
                    {r.label}
                  </span>
                </button>
              ))}
            </div>
            <p className="mt-1.5 text-xs text-slate-400">
              {ROLES.find((r) => r.value === role)?.desc}
            </p>
          </div>

          <Button type="submit" loading={isSubmitting} className="w-full py-3 text-base">
            {!isSubmitting && <UserPlus className="h-4 w-4" />}
            {isSubmitting ? 'Creating account…' : 'Create Account'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
