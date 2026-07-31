import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate, Link } from 'react-router-dom';
import { api, type DoctorCatalogResponse } from '../services/api';

export default function DoctorDashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [doctorEntry, setDoctorEntry] = useState<DoctorCatalogResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isToggling, setIsToggling] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    fetchDoctorEntry();
  }, []);

  const fetchDoctorEntry = async () => {
    setIsLoading(true);
    setError('');
    try {
      const doctors = await api.getDoctors();
      // Find this doctor's entry by matching userId
      const myEntry = doctors.find(doc => doc.userId === user?.id);
      if (myEntry) {
        setDoctorEntry(myEntry);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load doctor profile');
    } finally {
      setIsLoading(false);
    }
  };

  const handleToggleAvailability = async () => {
    if (!doctorEntry) return;
    setIsToggling(true);
    setError('');
    setSuccess('');

    try {
      const updated = await api.toggleAvailability({
        isAvailable: !doctorEntry.isAvailable,
      });
      setDoctorEntry(updated);
      setSuccess(`You are now ${updated.isAvailable ? 'online' : 'offline'}`);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update availability');
    } finally {
      setIsToggling(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', fontFamily: 'system-ui, sans-serif', color: '#4a5568' }}>
        Loading...
      </div>
    );
  }

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '900px', margin: '0 auto', background: '#f7fafc', minHeight: '100vh' }}>
      <header style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginBottom: '2rem', padding: '1rem 1.5rem', background: '#fff',
        borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      }}>
        <div>
          <h1 style={{ margin: 0, color: '#1a202c', fontSize: '1.5rem' }}>🩺 Doctor Dashboard</h1>
          <p style={{ margin: '0.25rem 0 0', color: '#718096', fontSize: '0.875rem' }}>
            Welcome, Dr. {user?.fullName || 'Doctor'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Link to="/profile" style={btnSecondary}>Profile</Link>
          <button onClick={handleLogout} style={btnDanger}>Logout</button>
        </div>
      </header>

      {/* Availability Toggle Card */}
      {doctorEntry && (
        <div style={{
          background: '#fff', borderRadius: '12px', padding: '2rem', marginBottom: '1.5rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h2 style={{ margin: '0 0 0.25rem', color: '#1a202c', fontSize: '1.15rem' }}>
                {doctorEntry.specialization}
              </h2>
              <p style={{ margin: 0, color: '#718096', fontSize: '0.9rem' }}>
                {doctorEntry.departmentName} · {doctorEntry.qualification} · {doctorEntry.experienceYears} years exp
              </p>
              <div style={{ marginTop: '0.5rem', display: 'flex', gap: '1.5rem', fontSize: '0.85rem', color: '#4a5568' }}>
                <span>💰 Fee: ₹{doctorEntry.consultationFee}</span>
                <span>⏱ Avg: {doctorEntry.avgConsultationTimeMinutes} min/patient</span>
              </div>
            </div>
            <div style={{ textAlign: 'center' }}>
              <div style={{
                fontSize: '0.85rem', fontWeight: 600, marginBottom: '0.5rem',
                color: doctorEntry.isAvailable ? '#276749' : '#c53030',
              }}>
                {doctorEntry.isAvailable ? '🟢 Online' : '🔴 Offline'}
              </div>
              <button
                onClick={handleToggleAvailability}
                disabled={isToggling}
                style={{
                  padding: '0.6rem 1.5rem', fontSize: '0.9rem', fontWeight: 600,
                  border: 'none', borderRadius: '8px',
                  background: isToggling ? '#a0aec0' : (doctorEntry.isAvailable ? '#fc8181' : '#48bb78'),
                  color: '#fff',
                  cursor: isToggling ? 'not-allowed' : 'pointer',
                  transition: 'all 0.2s',
                  minWidth: '120px',
                }}
                onMouseEnter={(e) => {
                  if (!isToggling) {
                    e.currentTarget.style.background = doctorEntry.isAvailable ? '#f56565' : '#38a169';
                  }
                }}
                onMouseLeave={(e) => {
                  if (!isToggling) {
                    e.currentTarget.style.background = doctorEntry.isAvailable ? '#fc8181' : '#48bb78';
                  }
                }}
              >
                {isToggling ? 'Updating...' : doctorEntry.isAvailable ? 'Go Offline' : 'Go Online'}
              </button>
            </div>
          </div>
        </div>
      )}

      {error && <Alert type="error" message={error} />}
      {success && <Alert type="success" message={success} />}

      {/* Queue management card */}
      <Link to="/doctor/queue" style={{ textDecoration: 'none' }}>
        <div style={{
          background: '#fff', borderRadius: '12px', padding: '2rem', marginBottom: '1.5rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          transition: 'all 0.2s', cursor: 'pointer', border: '2px solid transparent',
        }}
        onMouseEnter={(e) => { e.currentTarget.style.borderColor = '#0e7c81'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.1)'; }}
        onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'transparent'; e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.1)'; }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <div style={{ fontSize: '2.5rem' }}>🕐</div>
            <div>
              <h2 style={{ margin: '0 0 0.25rem', color: '#1a202c', fontSize: '1.25rem' }}>Live Patient Queue</h2>
              <p style={{ margin: 0, color: '#718096', fontSize: '0.9rem' }}>
                Color-coded AI triage, override controls, call-next & complete
              </p>
            </div>
          </div>
        </div>
      </Link>

      <div style={{
        background: '#fff', borderRadius: '12px', padding: '2rem',
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      }}>
        <h2 style={{ margin: '0 0 1rem', color: '#4a5568', fontSize: '1.15rem' }}>Account Info</h2>
        <div style={{ display: 'grid', gap: '0.75rem', fontSize: '0.9rem' }}>
          <div><strong>Name:</strong> Dr. {user?.fullName}</div>
          <div><strong>Email:</strong> {user?.email}</div>
          <div><strong>Role:</strong> {user?.role}</div>
        </div>
      </div>

      <footer style={{ marginTop: '2rem', textAlign: 'center', color: '#a0aec0', fontSize: '0.8rem' }}>
        CareQ — SmartOPD AI | Day 4 — Doctor/Department Module
      </footer>
    </div>
  );
}

const btnSecondary: React.CSSProperties = {
  padding: '0.5rem 1rem',
  border: '1px solid #e2e8f0',
  borderRadius: '8px',
  background: '#fff',
  color: '#4a5568',
  cursor: 'pointer',
  fontSize: '0.875rem',
  textDecoration: 'none',
  display: 'inline-block',
  transition: 'all 0.2s',
};

const btnDanger: React.CSSProperties = {
  padding: '0.5rem 1.25rem',
  border: '1px solid #e2e8f0',
  borderRadius: '8px',
  background: '#fff',
  color: '#e53e3e',
  cursor: 'pointer',
  fontSize: '0.875rem',
  fontWeight: 500,
  transition: 'all 0.2s',
};

function Alert({ type, message }: { type: 'error' | 'success'; message: string }) {
  const bg = type === 'error' ? '#fed7d7' : '#c6f6d5';
  const color = type === 'error' ? '#c53030' : '#276749';
  return (
    <div style={{ background: bg, color, padding: '0.75rem 1rem', borderRadius: '8px', marginBottom: '1rem', fontSize: '0.875rem' }}>
      {message}
    </div>
  );
}
