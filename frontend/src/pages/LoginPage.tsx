import { useState, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
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
        setError('Login failed. Please check your credentials.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '0.75rem',
    border: '1px solid #e2e8f0',
    borderRadius: '8px',
    fontSize: '1rem',
    boxSizing: 'border-box',
    outline: 'none',
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        fontFamily: 'system-ui, sans-serif',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        color: '#fff',
        padding: '1rem',
      }}
    >
      <div
        style={{
          background: '#fff',
          borderRadius: '16px',
          padding: '2rem',
          width: '100%',
          maxWidth: '420px',
          color: '#1a202c',
          boxShadow: '0 10px 30px rgba(0,0,0,0.2)',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
          <h1 style={{ margin: '0', color: '#667eea', fontSize: '2rem' }}>CareQ</h1>
          <p style={{ color: '#a0aec0', fontSize: '0.9rem', margin: '0.25rem 0 0' }}>
            SmartOPD AI — Sign in to continue
          </p>
        </div>

        {error && (
          <div
            style={{
              background: '#fed7d7',
              color: '#c53030',
              padding: '0.75rem',
              borderRadius: '8px',
              marginBottom: '1rem',
              fontSize: '0.875rem',
              textAlign: 'center',
            }}
          >
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '1rem' }}>
            <label style={{ display: 'block', marginBottom: '0.25rem', fontWeight: 500, fontSize: '0.875rem' }}>
              Email
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@hospital.com"
              required
              style={inputStyle}
              onFocus={(e) => (e.target.style.borderColor = '#667eea')}
              onBlur={(e) => (e.target.style.borderColor = '#e2e8f0')}
            />
          </div>

          <div style={{ marginBottom: '1.5rem' }}>
            <label style={{ display: 'block', marginBottom: '0.25rem', fontWeight: 500, fontSize: '0.875rem' }}>
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              required
              style={inputStyle}
              onFocus={(e) => (e.target.style.borderColor = '#667eea')}
              onBlur={(e) => (e.target.style.borderColor = '#e2e8f0')}
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            style={{
              width: '100%',
              padding: '0.85rem',
              fontSize: '1rem',
              fontWeight: 600,
              border: 'none',
              borderRadius: '8px',
              background: isSubmitting ? '#a0aec0' : '#667eea',
              color: '#fff',
              cursor: isSubmitting ? 'not-allowed' : 'pointer',
              transition: 'background 0.2s',
            }}
            onMouseEnter={(e) => {
              if (!isSubmitting) e.currentTarget.style.background = '#5a67d8';
            }}
            onMouseLeave={(e) => {
              if (!isSubmitting) e.currentTarget.style.background = '#667eea';
            }}
          >
            {isSubmitting ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <p style={{ textAlign: 'center', marginTop: '1.5rem', fontSize: '0.875rem', color: '#a0aec0' }}>
          Don't have an account?{' '}
          <Link to="/signup" style={{ color: '#667eea', textDecoration: 'none', fontWeight: 500 }}>
            Create one
          </Link>
        </p>
      </div>
    </div>
  );
}
