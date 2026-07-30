import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api, type DoctorCatalogResponse, type DepartmentResponse } from '../services/api';

export default function PatientDoctorBrowser() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [doctors, setDoctors] = useState<DoctorCatalogResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  // Filters
  const [selectedDeptId, setSelectedDeptId] = useState<string>('');
  const [searchSpecialization, setSearchSpecialization] = useState('');

  useEffect(() => {
    fetchDepartments();
    fetchDoctors();
  }, []);

  useEffect(() => {
    fetchDoctors();
  }, [selectedDeptId, searchSpecialization]);

  const fetchDepartments = async () => {
    try {
      const data = await api.getDepartments();
      setDepartments(data);
    } catch {
      // Silently fail
    }
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
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load doctors');
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const handleClearFilters = () => {
    setSelectedDeptId('');
    setSearchSpecialization('');
  };

  const selectStyle: React.CSSProperties = {
    padding: '0.6rem 1rem',
    border: '1px solid #e2e8f0',
    borderRadius: '8px',
    fontSize: '0.9rem',
    background: '#fff',
    outline: 'none',
    minWidth: '200px',
    cursor: 'pointer',
  };

  const inputStyle: React.CSSProperties = {
    padding: '0.6rem 1rem',
    border: '1px solid #e2e8f0',
    borderRadius: '8px',
    fontSize: '0.9rem',
    outline: 'none',
    minWidth: '200px',
  };

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '1000px', margin: '0 auto', background: '#f7fafc', minHeight: '100vh' }}>
      <header style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginBottom: '2rem', padding: '1rem 1.5rem', background: '#fff',
        borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      }}>
        <div>
          <h1 style={{ margin: 0, color: '#1a202c', fontSize: '1.5rem' }}>🔍 Browse Doctors</h1>
          <p style={{ margin: '0.25rem 0 0', color: '#718096', fontSize: '0.875rem' }}>
            Welcome, {user?.fullName || 'Patient'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Link to="/profile" style={btnSecondary}>Profile</Link>
          <Link to="/patient/dashboard" style={btnSecondary}>Dashboard</Link>
          <button onClick={handleLogout} style={btnDanger}>Logout</button>
        </div>
      </header>

      {/* Filters */}
      <div style={{
        background: '#fff', borderRadius: '12px', padding: '1.5rem', marginBottom: '1.5rem',
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      }}>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div>
            <label style={{ display: 'block', marginBottom: '0.3rem', fontWeight: 600, fontSize: '0.85rem', color: '#4a5568' }}>
              Department
            </label>
            <select
              value={selectedDeptId}
              onChange={(e) => setSelectedDeptId(e.target.value)}
              style={selectStyle}
            >
              <option value="">All Departments</option>
              {departments.map((dept) => (
                <option key={dept.id} value={dept.id}>{dept.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '0.3rem', fontWeight: 600, fontSize: '0.85rem', color: '#4a5568' }}>
              Specialization
            </label>
            <input
              type="text"
              value={searchSpecialization}
              onChange={(e) => setSearchSpecialization(e.target.value)}
              placeholder="Search by specialization..."
              style={inputStyle}
              onFocus={(e) => e.target.style.borderColor = '#667eea'}
              onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
            />
          </div>
          <button onClick={handleClearFilters} style={btnSecondary}>
            Clear Filters
          </button>
        </div>
      </div>

      {error && <Alert type="error" message={error} />}

      {/* Doctor Cards */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#718096' }}>Loading doctors...</div>
      ) : doctors.length === 0 ? (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '3rem', textAlign: 'center', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', color: '#a0aec0' }}>
          No doctors found matching your filters.
        </div>
      ) : (
        <div style={{ display: 'grid', gap: '1rem' }}>
          {doctors.map((doc) => (
            <div key={doc.id} style={{
              background: '#fff', borderRadius: '12px', padding: '1.5rem',
              boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              transition: 'box-shadow 0.2s',
            }}
              onMouseEnter={(e) => e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.1)'}
              onMouseLeave={(e) => e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.1)'}
            >
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
                  <div style={{
                    width: '48px', height: '48px', borderRadius: '50%',
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#fff', fontWeight: 700, fontSize: '1.2rem', flexShrink: 0,
                  }}>
                    {doc.specialization.charAt(0)}
                  </div>
                  <div>
                    <h3 style={{ margin: 0, color: '#1a202c', fontSize: '1rem' }}>
                      {doc.specialization}
                    </h3>
                    <p style={{ margin: '0.15rem 0 0', color: '#718096', fontSize: '0.85rem' }}>
                      {doc.departmentName} · {doc.qualification}
                    </p>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: '1.5rem', fontSize: '0.85rem', color: '#4a5568' }}>
                  <span>⭐ {doc.experienceYears} yrs exp</span>
                  <span>💰 ₹{doc.consultationFee}</span>
                  <span>⏱ {doc.avgConsultationTimeMinutes} min/patient</span>
                </div>
              </div>
              <div style={{ textAlign: 'right', flexShrink: 0 }}>
                <div style={{
                  display: 'inline-block', padding: '0.2rem 0.75rem', borderRadius: '999px',
                  fontSize: '0.75rem', fontWeight: 600, marginBottom: '0.5rem',
                  background: doc.isAvailable ? '#c6f6d5' : '#fed7d7',
                  color: doc.isAvailable ? '#276749' : '#c53030',
                }}>
                  {doc.isAvailable ? '● Available' : '● Offline'}
                </div>
                <br />
                <button
                  disabled={!doc.isAvailable}
                  style={{
                    padding: '0.5rem 1.25rem', fontSize: '0.85rem', fontWeight: 600,
                    border: 'none', borderRadius: '8px',
                    background: doc.isAvailable ? '#667eea' : '#e2e8f0',
                    color: doc.isAvailable ? '#fff' : '#a0aec0',
                    cursor: doc.isAvailable ? 'pointer' : 'not-allowed',
                    transition: 'background 0.2s',
                  }}
                  onMouseEnter={(e) => { if (doc.isAvailable) e.currentTarget.style.background = '#5a67d8'; }}
                  onMouseLeave={(e) => { if (doc.isAvailable) e.currentTarget.style.background = '#667eea'; }}
                >
                  {doc.isAvailable ? 'Join Queue' : 'Unavailable'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

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
