import { useNavigate } from 'react-router-dom';

function LoginPage() {
  const navigate = useNavigate();

  const handleRoleLogin = (role: string) => {
    // Placeholder: navigate to the appropriate dashboard without real auth
    switch (role) {
      case 'PATIENT':
        navigate('/patient');
        break;
      case 'DOCTOR':
        navigate('/doctor');
        break;
      case 'ADMIN':
        navigate('/admin');
        break;
      default:
        break;
    }
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
      }}
    >
      <h1 style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>CareQ</h1>
      <p style={{ fontSize: '1.1rem', marginBottom: '2rem', opacity: 0.9 }}>
        SmartOPD AI — Intelligent Patient Flow Platform
      </p>
      <p style={{ marginBottom: '1.5rem' }}>Select a role to preview the dashboard (login not yet implemented):</p>
      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', justifyContent: 'center' }}>
        <button
          onClick={() => handleRoleLogin('PATIENT')}
          style={{
            padding: '1rem 2rem',
            fontSize: '1rem',
            border: '2px solid rgba(255,255,255,0.3)',
            borderRadius: '12px',
            background: 'rgba(255,255,255,0.1)',
            color: '#fff',
            cursor: 'pointer',
            backdropFilter: 'blur(10px)',
            transition: 'all 0.2s',
            minWidth: '160px',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(255,255,255,0.25)';
            e.currentTarget.style.borderColor = '#fff';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'rgba(255,255,255,0.1)';
            e.currentTarget.style.borderColor = 'rgba(255,255,255,0.3)';
          }}
        >
          👤 Patient
        </button>
        <button
          onClick={() => handleRoleLogin('DOCTOR')}
          style={{
            padding: '1rem 2rem',
            fontSize: '1rem',
            border: '2px solid rgba(255,255,255,0.3)',
            borderRadius: '12px',
            background: 'rgba(255,255,255,0.1)',
            color: '#fff',
            cursor: 'pointer',
            backdropFilter: 'blur(10px)',
            transition: 'all 0.2s',
            minWidth: '160px',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(255,255,255,0.25)';
            e.currentTarget.style.borderColor = '#fff';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'rgba(255,255,255,0.1)';
            e.currentTarget.style.borderColor = 'rgba(255,255,255,0.3)';
          }}
        >
          🩺 Doctor
        </button>
        <button
          onClick={() => handleRoleLogin('ADMIN')}
          style={{
            padding: '1rem 2rem',
            fontSize: '1rem',
            border: '2px solid rgba(255,255,255,0.3)',
            borderRadius: '12px',
            background: 'rgba(255,255,255,0.1)',
            color: '#fff',
            cursor: 'pointer',
            backdropFilter: 'blur(10px)',
            transition: 'all 0.2s',
            minWidth: '160px',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(255,255,255,0.25)';
            e.currentTarget.style.borderColor = '#fff';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'rgba(255,255,255,0.1)';
            e.currentTarget.style.borderColor = 'rgba(255,255,255,0.3)';
          }}
        >
          ⚙️ Admin
        </button>
      </div>
    </div>
  );
}

export default LoginPage;
