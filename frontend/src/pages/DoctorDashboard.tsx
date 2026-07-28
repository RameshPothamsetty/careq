import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function DoctorDashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '900px', margin: '0 auto', background: '#f7fafc', minHeight: '100vh' }}>
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '2rem',
          padding: '1rem 1.5rem',
          background: '#fff',
          borderRadius: '12px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        }}
      >
        <div>
          <h1 style={{ margin: 0, color: '#1a202c', fontSize: '1.5rem' }}>🩺 Doctor Dashboard</h1>
          <p style={{ margin: '0.25rem 0 0', color: '#718096', fontSize: '0.875rem' }}>
            Welcome, Dr. {user?.fullName || 'Doctor'}
          </p>
        </div>
        <button
          onClick={handleLogout}
          style={{
            padding: '0.5rem 1.25rem',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            background: '#fff',
            color: '#e53e3e',
            cursor: 'pointer',
            fontSize: '0.875rem',
            fontWeight: 500,
            transition: 'all 0.2s',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = '#fff5f5';
            e.currentTarget.style.borderColor = '#fc8181';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = '#fff';
            e.currentTarget.style.borderColor = '#e2e8f0';
          }}
        >
          Logout
        </button>
      </header>

      <div style={{ display: 'grid', gap: '1.5rem' }}>
        <div style={{ background: '#fff', borderRadius: '12px', padding: '2rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
          <h2 style={{ margin: '0 0 0.5rem', color: '#4a5568', fontSize: '1.15rem' }}>Welcome, Dr. {user?.fullName}!</h2>
          <p style={{ color: '#718096', fontSize: '0.95rem', margin: 0 }}>
            You are logged in as a <strong>Doctor</strong>. Your patient queue and triage information will appear here starting Day 4.
          </p>
          <div style={{ marginTop: '1rem', padding: '1rem', background: '#fffaF0', borderRadius: '8px', color: '#c05621', fontSize: '0.875rem' }}>
            Queue management features will be available starting Day 4.
          </div>
        </div>

        <div style={{ background: '#fff', borderRadius: '12px', padding: '2rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
          <h2 style={{ margin: '0 0 1rem', color: '#4a5568', fontSize: '1.15rem' }}>Account Info</h2>
          <div style={{ display: 'grid', gap: '0.75rem', fontSize: '0.9rem' }}>
            <div><strong>Name:</strong> Dr. {user?.fullName}</div>
            <div><strong>Email:</strong> {user?.email}</div>
            <div><strong>Role:</strong> {user?.role}</div>
          </div>
        </div>
      </div>

      <footer style={{ marginTop: '2rem', textAlign: 'center', color: '#a0aec0', fontSize: '0.8rem' }}>
        CareQ — SmartOPD AI | Day 2 — Auth Module Complete
      </footer>
    </div>
  );
}
