import { useAuth } from '../context/AuthContext';
import { useNavigate, Link } from 'react-router-dom';

export default function PatientDashboard() {
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
          <h1 style={{ margin: 0, color: '#1a202c', fontSize: '1.5rem' }}>👤 Patient Dashboard</h1>
          <p style={{ margin: '0.25rem 0 0', color: '#718096', fontSize: '0.875rem' }}>
            Welcome, {user?.fullName || 'Patient'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Link
            to="/profile"
            style={btnStyle}
          >
            Profile
          </Link>
          <button
            onClick={handleLogout}
            style={logoutBtnStyle}
          >
            Logout
          </button>
        </div>
      </header>

      <div style={{ display: 'grid', gap: '1.5rem' }}>
        {/* Doctor Browser Card */}
        <Link to="/patient/doctors" style={{ textDecoration: 'none' }}>
          <div
            style={{
              background: '#fff',
              borderRadius: '12px',
              padding: '2rem',
              boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
              transition: 'all 0.2s',
              cursor: 'pointer',
              border: '2px solid transparent',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.borderColor = '#667eea'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.1)'; }}
            onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'transparent'; e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.1)'; }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <div style={{ fontSize: '2.5rem' }}>🔍</div>
              <div>
                <h2 style={{ margin: '0 0 0.25rem', color: '#1a202c', fontSize: '1.25rem' }}>Browse Doctors</h2>
                <p style={{ margin: 0, color: '#718096', fontSize: '0.9rem' }}>
                  Find and filter doctors by department and specialization
                </p>
              </div>
            </div>
          </div>
        </Link>

        <div style={{ background: '#fff', borderRadius: '12px', padding: '2rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
          <h2 style={{ margin: '0 0 0.5rem', color: '#4a5568', fontSize: '1.15rem' }}>Welcome, {user?.fullName}!</h2>
          <p style={{ color: '#718096', fontSize: '0.95rem', margin: 0 }}>
            You are logged in as <strong>Patient</strong>. Browse doctors and join a queue to get started.
          </p>
        </div>

        <div style={{ background: '#fff', borderRadius: '12px', padding: '2rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
          <h2 style={{ margin: '0 0 1rem', color: '#4a5568', fontSize: '1.15rem' }}>My Queue</h2>
          <div style={{ padding: '1rem', background: '#ebf8ff', borderRadius: '8px', color: '#2b6cb0', fontSize: '0.875rem' }}>
            Queue features will be available starting Day 5. You can browse doctors now.
          </div>
        </div>

        <div style={{ background: '#fff', borderRadius: '12px', padding: '2rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
          <h2 style={{ margin: '0 0 1rem', color: '#4a5568', fontSize: '1.15rem' }}>Account Info</h2>
          <div style={{ display: 'grid', gap: '0.75rem', fontSize: '0.9rem' }}>
            <div><strong>Name:</strong> {user?.fullName}</div>
            <div><strong>Email:</strong> {user?.email}</div>
            <div><strong>Role:</strong> {user?.role}</div>
          </div>
        </div>
      </div>

      <footer style={{ marginTop: '2rem', textAlign: 'center', color: '#a0aec0', fontSize: '0.8rem' }}>
        CareQ — SmartOPD AI | Day 4 — Doctor/Department Module
      </footer>
    </div>
  );
}

const btnStyle: React.CSSProperties = {
  padding: '0.5rem 1.25rem',
  border: '1px solid #e2e8f0',
  borderRadius: '8px',
  background: '#fff',
  color: '#4a5568',
  cursor: 'pointer',
  fontSize: '0.875rem',
  fontWeight: 500,
  textDecoration: 'none',
  transition: 'all 0.2s',
};

const logoutBtnStyle: React.CSSProperties = {
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
