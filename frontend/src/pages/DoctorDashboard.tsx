function DoctorDashboard() {
  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '800px', margin: '0 auto' }}>
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '2rem',
          paddingBottom: '1rem',
          borderBottom: '1px solid #e2e8f0',
        }}
      >
        <h1 style={{ margin: 0, color: '#1a202c' }}>🩺 Doctor Dashboard</h1>
        <a
          href="/login"
          style={{ color: '#667eea', textDecoration: 'none', fontWeight: 500 }}
        >
          Logout
        </a>
      </header>

      <div
        style={{
          background: '#fff',
          borderRadius: '12px',
          padding: '2rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          marginBottom: '1.5rem',
        }}
      >
        <h2 style={{ margin: '0 0 0.5rem', color: '#4a5568' }}>Current Queue</h2>
        <p style={{ color: '#a0aec0', marginBottom: '1rem' }}>
          Patients sorted by triage priority (Critical → Urgent → Normal)
        </p>
        <div
          style={{
            padding: '1rem',
            background: '#f7fafc',
            borderRadius: '8px',
            textAlign: 'center',
            color: '#a0aec0',
          }}
        >
          No patients in queue yet. Queue data will render here when connected to backend.
        </div>
      </div>

      <div
        style={{
          background: '#fff',
          borderRadius: '12px',
          padding: '2rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        }}
      >
        <h2 style={{ margin: '0 0 0.5rem', color: '#4a5568' }}>Doctor Info</h2>
        <p style={{ color: '#a0aec0' }}>
          Your profile details, department, and specialization will be displayed here.
        </p>
      </div>

      <footer style={{ marginTop: '2rem', textAlign: 'center', color: '#a0aec0', fontSize: '0.875rem' }}>
        CareQ — SmartOPD AI | Routing Skeleton (No API calls yet)
      </footer>
    </div>
  );
}

export default DoctorDashboard;
