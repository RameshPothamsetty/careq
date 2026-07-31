import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api, type DoctorCatalogResponse, type DepartmentResponse } from '../services/api';

// ─── Color palette ───
const colors = {
  primary: '#0f4c81',
  primaryLight: '#e8f0fe',
  primaryDark: '#0a3559',
  secondary: '#667eea',
  accent: '#48bb78',
  accentLight: '#c6f6d5',
  accentDark: '#276749',
  danger: '#e53e3e',
  dangerLight: '#fed7d7',
  warning: '#ed8936',
  warningLight: '#fefcbf',
  bg: '#f0f4f8',
  card: '#ffffff',
  text: '#1a202c',
  textSecondary: '#4a5568',
  textMuted: '#a0aec0',
  border: '#e2e8f0',
  shadow: 'rgba(0,0,0,0.08)',
};

// ─── Specialization icons map ───
const specIcons: Record<string, string> = {
  'cardiology': '❤️',
  'neurology': '🧠',
  'orthopedics': '🦴',
  'pediatrics': '👶',
  'dermatology': '🧴',
  'ophthalmology': '👁️',
  'ent': '👂',
  'gastroenterology': '🫀',
  'pulmonology': '🫁',
  'nephrology': '🔬',
};

function getSpecIcon(spec: string): string {
  const key = Object.keys(specIcons).find(k => spec.toLowerCase().includes(k));
  return key ? specIcons[key] : '🩺';
}

function getGradient(spec: string): string {
  const gradients = [
    'linear-gradient(135deg, #0f4c81, #2d6db5)',
    'linear-gradient(135deg, #6b46c1, #9f7aea)',
    'linear-gradient(135deg, #2b6cb0, #4299e1)',
    'linear-gradient(135deg, #276749, #48bb78)',
    'linear-gradient(135deg, #c05621, #ed8936)',
    'linear-gradient(135deg, #702459, #b83280)',
    'linear-gradient(135deg, #2c5282, #3182ce)',
    'linear-gradient(135deg, #744210, #d69e2e)',
    'linear-gradient(135deg, #553c9a, #805ad5)',
    'linear-gradient(135deg, #285e61, #319795)',
  ];
  const hash = spec.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return gradients[hash % gradients.length];
}

export default function PatientDoctorBrowser() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [doctors, setDoctors] = useState<DoctorCatalogResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedDeptId, setSelectedDeptId] = useState<string>('');
  const [searchSpecialization, setSearchSpecialization] = useState('');
  const [stats, setStats] = useState({ total: 0, available: 0, departments: 0 });
  const [animateIn, setAnimateIn] = useState(false);

  useEffect(() => {
    fetchDepartments();
    fetchDoctors();
    setTimeout(() => setAnimateIn(true), 100);
  }, []);

  useEffect(() => {
    fetchDoctors();
  }, [selectedDeptId, searchSpecialization]);

  const fetchDepartments = async () => {
    try {
      const data = await api.getDepartments();
      setDepartments(data.filter(d => d.isActive));
    } catch { /* silent */ }
  };

  const fetchDoctors = async () => {
    setIsLoading(true);
    setError('');
    try {
      const params: { departmentId?: number; specialization?: string } = {};
      if (selectedDeptId) params.departmentId = Number(selectedDeptId);
      if (searchSpecialization.trim()) params.specialization = searchSpecialization.trim();
      const data = await api.getDoctors(params);
      setDoctors(data);
      setStats({
        total: data.length,
        available: data.filter(d => d.isAvailable).length,
        departments: new Set(data.map(d => d.departmentName)).size,
      });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load doctors');
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogout = () => { logout(); navigate('/login', { replace: true }); };

  // ─── Styles ───
  const s = {
    container: {
      fontFamily: "'Inter', system-ui, -apple-system, sans-serif",
      padding: '1.5rem',
      maxWidth: '1100px',
      margin: '0 auto',
      background: colors.bg,
      minHeight: '100vh',
    } as React.CSSProperties,
    header: {
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      marginBottom: '1.5rem', padding: '1rem 1.5rem',
      background: colors.card, borderRadius: '16px',
      boxShadow: `0 1px 3px ${colors.shadow}`,
      border: `1px solid ${colors.border}`,
    } as React.CSSProperties,
    headerTitle: {
      margin: 0, color: colors.text, fontSize: '1.35rem', fontWeight: 700,
      letterSpacing: '-0.02em',
    } as React.CSSProperties,
    headerSub: {
      margin: '0.15rem 0 0', color: colors.textSecondary, fontSize: '0.85rem',
    } as React.CSSProperties,
    statsRow: {
      display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
      gap: '0.75rem', marginBottom: '1.25rem',
    } as React.CSSProperties,
    statCard: {
      background: colors.card, borderRadius: '12px', padding: '1rem 1.25rem',
      boxShadow: `0 1px 3px ${colors.shadow}`, border: `1px solid ${colors.border}`,
      textAlign: 'center',
    } as React.CSSProperties,
    statValue: {
      fontSize: '1.5rem', fontWeight: 700, color: colors.primary,
      lineHeight: 1.2,
    } as React.CSSProperties,
    statLabel: {
      fontSize: '0.75rem', color: colors.textMuted, fontWeight: 500,
      textTransform: 'uppercase', letterSpacing: '0.05em', marginTop: '0.15rem',
    } as React.CSSProperties,
    filterCard: {
      background: colors.card, borderRadius: '16px', padding: '1.25rem 1.5rem',
      marginBottom: '1.25rem', boxShadow: `0 1px 3px ${colors.shadow}`,
      border: `1px solid ${colors.border}`,
    } as React.CSSProperties,
    filterRow: {
      display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap',
    } as React.CSSProperties,
    filterLabel: {
      display: 'block', marginBottom: '0.35rem', fontWeight: 600,
      fontSize: '0.8rem', color: colors.textSecondary,
      textTransform: 'uppercase', letterSpacing: '0.04em',
    } as React.CSSProperties,
    select: {
      padding: '0.6rem 2rem 0.6rem 0.85rem',
      border: `1.5px solid ${colors.border}`,
      borderRadius: '10px', fontSize: '0.9rem', background: colors.card,
      outline: 'none', minWidth: '200px', cursor: 'pointer',
      color: colors.text, fontWeight: 500,
      appearance: 'none', backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%234a5568' d='M6 8L1 3h10z'/%3E%3C/svg%3E")`,
      backgroundRepeat: 'no-repeat', backgroundPosition: 'right 0.75rem center',
      transition: 'border-color 0.2s',
    } as React.CSSProperties,
    input: {
      padding: '0.6rem 0.85rem', border: `1.5px solid ${colors.border}`,
      borderRadius: '10px', fontSize: '0.9rem', outline: 'none',
      minWidth: '220px', color: colors.text, fontWeight: 500,
      transition: 'border-color 0.2s', background: colors.card,
    } as React.CSSProperties,
    chip: (isActive: boolean) => ({
      display: 'inline-flex', alignItems: 'center', gap: '0.3rem',
      padding: '0.2rem 0.75rem', borderRadius: '999px',
      fontSize: '0.72rem', fontWeight: 600,
      background: isActive ? colors.accentLight : colors.dangerLight,
      color: isActive ? colors.accentDark : colors.danger,
    }) as React.CSSProperties,
  };

  // ─── Shared buttons ───
  const btnSecondary = {
    padding: '0.45rem 1rem', border: `1px solid ${colors.border}`,
    borderRadius: '10px', background: colors.card, color: colors.textSecondary,
    cursor: 'pointer', fontSize: '0.8rem', fontWeight: 600,
    textDecoration: 'none', display: 'inline-block',
    transition: 'all 0.2s',
  } as React.CSSProperties;

  const btnDanger = {
    padding: '0.45rem 1rem', border: `1px solid ${colors.border}`,
    borderRadius: '10px', background: colors.card, color: colors.danger,
    cursor: 'pointer', fontSize: '0.8rem', fontWeight: 600,
    transition: 'all 0.2s',
  } as React.CSSProperties;

  const btnSmall = {
    padding: '0.45rem 1.25rem', border: 'none', borderRadius: '10px',
    fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer',
    transition: 'all 0.2s',
    background: colors.secondary, color: '#fff',
  } as React.CSSProperties;

  const btnSmallDisabled = {
    ...btnSmall, background: '#e2e8f0', color: colors.textMuted, cursor: 'not-allowed',
  } as React.CSSProperties;

  return (
    <div style={s.container}>
      {/* Header */}
      <header style={s.header}>
        <div>
          <h1 style={s.headerTitle}>🔍 Browse Doctors</h1>
          <p style={s.headerSub}>Find the right specialist for your care</p>
        </div>
        <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
          <Link to="/profile" style={btnSecondary}>Profile</Link>
          <Link to="/patient" style={btnSecondary}>Dashboard</Link>
          <button onClick={handleLogout} style={btnDanger}>Logout</button>
        </div>
      </header>

      {/* Stats */}
      {!isLoading && doctors.length > 0 && (
        <div style={s.statsRow}>
          <div style={s.statCard}>
            <div style={s.statValue}>{stats.total}</div>
            <div style={s.statLabel}>Total Doctors</div>
          </div>
          <div style={s.statCard}>
            <div style={{ ...s.statValue, color: colors.accent }}>{stats.available}</div>
            <div style={s.statLabel}>Available Now</div>
          </div>
          <div style={s.statCard}>
            <div style={{ ...s.statValue, color: colors.warning }}>{stats.departments}</div>
            <div style={s.statLabel}>Departments</div>
          </div>
        </div>
      )}

      {/* Filters */}
      <div style={s.filterCard}>
        <div style={s.filterRow}>
          <div>
            <label style={s.filterLabel}>🏥 Department</label>
            <select
              value={selectedDeptId}
              onChange={(e) => setSelectedDeptId(e.target.value)}
              style={s.select}
              onFocus={(e) => e.target.style.borderColor = colors.primary}
              onBlur={(e) => e.target.style.borderColor = colors.border}
            >
              <option value="">All Departments</option>
              {departments.map((dept) => (
                <option key={dept.id} value={dept.id}>{dept.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label style={s.filterLabel}>🔎 Specialization</label>
            <input
              type="text"
              value={searchSpecialization}
              onChange={(e) => setSearchSpecialization(e.target.value)}
              placeholder="e.g. Cardiology"
              style={s.input}
              onFocus={(e) => e.target.style.borderColor = colors.primary}
              onBlur={(e) => e.target.style.borderColor = colors.border}
            />
          </div>
          <button
            onClick={() => { setSelectedDeptId(''); setSearchSpecialization(''); }}
            style={{
              ...btnSecondary, padding: '0.6rem 1.25rem', fontWeight: 600,
            }}
          >
            ✕ Clear
          </button>
        </div>
      </div>

      {error && <Alert type="error" message={error} />}

      {/* Loading */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '4rem' }}>
          <div style={{
            width: '48px', height: '48px', border: `4px solid ${colors.border}`,
            borderTopColor: colors.primary, borderRadius: '50%',
            margin: '0 auto 1rem', animation: 'spin 0.8s linear infinite',
          }} />
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
          <p style={{ color: colors.textSecondary, fontSize: '0.9rem' }}>Finding doctors for you...</p>
        </div>
      ) : doctors.length === 0 ? (
        /* Empty state */
        <div style={{
          background: colors.card, borderRadius: '16px', padding: '3rem',
          textAlign: 'center', boxShadow: `0 1px 3px ${colors.shadow}`,
          border: `1px solid ${colors.border}`,
        }}>
          <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>🩺</div>
          <h2 style={{ color: colors.text, margin: '0 0 0.5rem', fontSize: '1.25rem' }}>
            No doctors found
          </h2>
          <p style={{ color: colors.textMuted, margin: '0 0 1.5rem', fontSize: '0.9rem' }}>
            {selectedDeptId || searchSpecialization
              ? 'Try adjusting your filters to see more results'
              : 'Doctor catalog entries will appear here once added by an Admin'}
          </p>
          {(selectedDeptId || searchSpecialization) && (
            <button
              onClick={() => { setSelectedDeptId(''); setSearchSpecialization(''); }}
              style={{
                padding: '0.7rem 2rem', border: 'none', borderRadius: '10px',
                background: colors.primary, color: '#fff', fontSize: '0.9rem',
                fontWeight: 600, cursor: 'pointer', transition: 'background 0.2s',
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = colors.primaryDark}
              onMouseLeave={(e) => e.currentTarget.style.background = colors.primary}
            >
              Clear All Filters
            </button>
          )}
        </div>
      ) : (
        /* Doctor Cards */
        <div style={{ display: 'grid', gap: '1rem' }}>
          {doctors.map((doc, idx) => (
            <div
              key={doc.id}
              style={{
                background: colors.card, borderRadius: '16px', padding: '1.25rem 1.5rem',
                boxShadow: `0 1px 3px ${colors.shadow}`,
                border: `1px solid ${colors.border}`,
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                transition: 'all 0.25s ease',
                opacity: animateIn ? 1 : 0,
                transform: animateIn ? 'translateY(0)' : 'translateY(12px)',
                transitionDelay: `${idx * 50}ms`,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
                e.currentTarget.style.transform = 'translateY(-2px)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = `0 1px 3px ${colors.shadow}`;
                e.currentTarget.style.transform = 'translateY(0)';
              }}
            >
              {/* Left: Avatar + Info */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flex: 1, minWidth: 0 }}>
                {/* Avatar */}
                <div style={{
                  width: '56px', height: '56px', borderRadius: '14px',
                  background: getGradient(doc.specialization),
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '1.6rem', flexShrink: 0, boxShadow: '0 4px 10px rgba(0,0,0,0.1)',
                }}>
                  {getSpecIcon(doc.specialization)}
                </div>

                {/* Details */}
                <div style={{ minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                    <h3 style={{
                      margin: 0, color: colors.text, fontSize: '1.05rem', fontWeight: 700,
                    }}>
                      {doc.specialization}
                    </h3>
                    <span style={s.chip(doc.isAvailable)}>
                      {doc.isAvailable ? '● Available' : '● Offline'}
                    </span>
                  </div>
                  <p style={{
                    margin: '0.25rem 0 0', color: colors.textSecondary, fontSize: '0.85rem',
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  }}>
                    <span style={{
                      display: 'inline-block', padding: '0.1rem 0.5rem', borderRadius: '6px',
                      background: colors.primaryLight, color: colors.primary,
                      fontWeight: 600, fontSize: '0.78rem', marginRight: '0.4rem',
                    }}>
                      {doc.departmentName}
                    </span>
                    {doc.qualification}
                  </p>

                  {/* Tags */}
                  <div style={{ display: 'flex', gap: '1.25rem', marginTop: '0.45rem', flexWrap: 'wrap' }}>
                    <Tag icon="⭐" label="Experience" value={`${doc.experienceYears} years`} />
                    <Tag icon="💰" label="Fee" value={`₹${doc.consultationFee}`} />
                    <Tag icon="⏱" label="Avg. Time" value={`${doc.avgConsultationTimeMinutes} min`} />
                  </div>
                </div>
              </div>

              {/* Right: CTA */}
              <div style={{ textAlign: 'right', flexShrink: 0, marginLeft: '1rem' }}>
                <button
                  disabled={!doc.isAvailable}
                  style={doc.isAvailable ? btnSmall : btnSmallDisabled}
                  onClick={() => navigate(`/patient/queue?doctor=${doc.id}`)}
                  onMouseEnter={(e) => {
                    if (doc.isAvailable) e.currentTarget.style.background = '#5a67d8';
                  }}
                  onMouseLeave={(e) => {
                    if (doc.isAvailable) e.currentTarget.style.background = colors.secondary;
                  }}
                >
                  {doc.isAvailable ? 'Join Queue →' : 'Unavailable'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <footer style={{ marginTop: '2rem', textAlign: 'center', color: colors.textMuted, fontSize: '0.75rem' }}>
        CareQ — SmartOPD AI | Find the right specialist
      </footer>
    </div>
  );
}

// ─── Sub-components ───

function Tag({ icon, label, value }: { icon: string; label: string; value: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem', fontSize: '0.78rem', color: '#4a5568' }}>
      <span style={{ fontSize: '0.85rem' }}>{icon}</span>
      <span style={{ color: '#a0aec0', marginRight: '0.1rem' }}>{label}:</span>
      <span style={{ fontWeight: 600 }}>{value}</span>
    </div>
  );
}

function Alert({ type, message }: { type: 'error' | 'success'; message: string }) {
  const bg = type === 'error' ? colors.dangerLight : colors.accentLight;
  const color = type === 'error' ? colors.danger : colors.accentDark;
  return (
    <div style={{
      background: bg, color, padding: '0.75rem 1rem', borderRadius: '10px',
      marginBottom: '1rem', fontSize: '0.85rem', fontWeight: 500,
      border: `1px solid ${type === 'error' ? '#fc8181' : '#9ae6b4'}`,
    }}>
      {message}
    </div>
  );
}
