import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api, type DoctorCatalogResponse, type DepartmentResponse } from '../services/api';

export default function AdminDoctorManager() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [doctors, setDoctors] = useState<DoctorCatalogResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Form state
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [userId, setUserId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [specialization, setSpecialization] = useState('');
  const [qualification, setQualification] = useState('');
  const [experienceYears, setExperienceYears] = useState('');
  const [consultationFee, setConsultationFee] = useState('');
  const [avgConsultationTimeMinutes, setAvgConsultationTimeMinutes] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    Promise.all([fetchDoctors(), fetchDepartments()]);
  }, []);

  const fetchDoctors = async () => {
    try {
      const data = await api.getDoctors();
      setDoctors(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load doctors');
    } finally {
      setIsLoading(false);
    }
  };

  const fetchDepartments = async () => {
    try {
      const data = await api.getDepartments();
      setDepartments(data);
    } catch {
      // Silently fail - departments might load separately
    }
  };

  const resetForm = () => {
    setEditingId(null);
    setUserId('');
    setDepartmentId('');
    setSpecialization('');
    setQualification('');
    setExperienceYears('');
    setConsultationFee('');
    setAvgConsultationTimeMinutes('');
    setShowForm(false);
    setError('');
    setSuccess('');
  };

  const handleEdit = (doc: DoctorCatalogResponse) => {
    setEditingId(doc.id);
    setUserId(doc.userId);
    setDepartmentId(String(doc.departmentId));
    setSpecialization(doc.specialization);
    setQualification(doc.qualification);
    setExperienceYears(String(doc.experienceYears));
    setConsultationFee(String(doc.consultationFee));
    setAvgConsultationTimeMinutes(String(doc.avgConsultationTimeMinutes));
    setShowForm(true);
    setError('');
    setSuccess('');
  };

  const handleAdd = () => {
    resetForm();
    setShowForm(true);
    setError('');
    setSuccess('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsSaving(true);

    try {
      const payload = {
        userId,
        departmentId: Number(departmentId),
        specialization,
        qualification,
        experienceYears: Number(experienceYears),
        consultationFee: Number(consultationFee),
        avgConsultationTimeMinutes: Number(avgConsultationTimeMinutes),
      };

      if (editingId) {
        await api.updateDoctor(editingId, payload);
        setSuccess('Doctor catalog entry updated successfully');
      } else {
        await api.createDoctor(payload);
        setSuccess('Doctor catalog entry created successfully');
      }

      resetForm();
      await fetchDoctors();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save doctor');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this doctor catalog entry?')) return;
    setError('');
    setSuccess('');
    try {
      await api.deleteDoctor(id);
      setSuccess('Doctor catalog entry deleted successfully');
      await fetchDoctors();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to delete doctor');
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '0.7rem 0.85rem',
    border: '1px solid #e2e8f0',
    borderRadius: '8px',
    fontSize: '0.95rem',
    boxSizing: 'border-box',
    outline: 'none',
    transition: 'border-color 0.2s',
  };

  const labelStyle: React.CSSProperties = {
    display: 'block',
    marginBottom: '0.3rem',
    fontWeight: 600,
    fontSize: '0.85rem',
    color: '#4a5568',
  };

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '1000px', margin: '0 auto', background: '#f7fafc', minHeight: '100vh' }}>
      <header style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginBottom: '2rem', padding: '1rem 1.5rem', background: '#fff',
        borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      }}>
        <div>
          <h1 style={{ margin: 0, color: '#1a202c', fontSize: '1.5rem' }}>🩺 Manage Doctor Catalog</h1>
          <p style={{ margin: '0.25rem 0 0', color: '#718096', fontSize: '0.875rem' }}>
            Admin — {user?.fullName || 'Admin'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button onClick={() => navigate('/admin/dashboard')} style={btnSecondary}>← Back</button>
          <button onClick={handleLogout} style={btnDanger}>Logout</button>
        </div>
      </header>

      {error && <Alert type="error" message={error} />}
      {success && <Alert type="success" message={success} />}

      {!showForm && (
        <div style={{ marginBottom: '1rem' }}>
          <button onClick={handleAdd} style={btnPrimary}>
            + Add Doctor Entry
          </button>
        </div>
      )}

      {showForm && (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '2rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', marginBottom: '1.5rem' }}>
          <h2 style={{ margin: '0 0 1.25rem', color: '#4a5568', fontSize: '1.15rem' }}>
            {editingId ? 'Edit Doctor Entry' : 'New Doctor Catalog Entry'}
          </h2>
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: '1.25rem' }}>
              <label style={labelStyle}>User ID *</label>
              <input
                type="text"
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                placeholder="UUID from auth-service"
                required
                disabled={!!editingId}
                style={{ ...inputStyle, background: editingId ? '#f7fafc' : '#fff' }}
                onFocus={(e) => e.target.style.borderColor = '#667eea'}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              />
            </div>

            <div style={{ marginBottom: '1.25rem' }}>
              <label style={labelStyle}>Department *</label>
              <select
                value={departmentId}
                onChange={(e) => setDepartmentId(e.target.value)}
                required
                style={inputStyle}
                onFocus={(e) => e.target.style.borderColor = '#667eea'}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              >
                <option value="">Select department</option>
                {departments.map((dept) => (
                  <option key={dept.id} value={dept.id}>{dept.name}</option>
                ))}
              </select>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem', marginBottom: '1.25rem' }}>
              <div>
                <label style={labelStyle}>Specialization *</label>
                <input
                  type="text"
                  value={specialization}
                  onChange={(e) => setSpecialization(e.target.value)}
                  placeholder="e.g., Interventional Cardiology"
                  required
                  style={inputStyle}
                  onFocus={(e) => e.target.style.borderColor = '#667eea'}
                  onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
                />
              </div>
              <div>
                <label style={labelStyle}>Qualification *</label>
                <input
                  type="text"
                  value={qualification}
                  onChange={(e) => setQualification(e.target.value)}
                  placeholder="e.g., MD, DM Cardiology"
                  required
                  style={inputStyle}
                  onFocus={(e) => e.target.style.borderColor = '#667eea'}
                  onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1.25rem', marginBottom: '1.25rem' }}>
              <div>
                <label style={labelStyle}>Experience (Years) *</label>
                <input
                  type="number"
                  value={experienceYears}
                  onChange={(e) => setExperienceYears(e.target.value)}
                  min="0"
                  required
                  style={inputStyle}
                  onFocus={(e) => e.target.style.borderColor = '#667eea'}
                  onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
                />
              </div>
              <div>
                <label style={labelStyle}>Consultation Fee *</label>
                <input
                  type="number"
                  step="0.01"
                  value={consultationFee}
                  onChange={(e) => setConsultationFee(e.target.value)}
                  min="0"
                  required
                  style={inputStyle}
                  onFocus={(e) => e.target.style.borderColor = '#667eea'}
                  onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
                />
              </div>
              <div>
                <label style={labelStyle}>Avg Consult Time (min) *</label>
                <input
                  type="number"
                  value={avgConsultationTimeMinutes}
                  onChange={(e) => setAvgConsultationTimeMinutes(e.target.value)}
                  min="1"
                  required
                  style={inputStyle}
                  onFocus={(e) => e.target.style.borderColor = '#667eea'}
                  onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
                />
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button type="submit" disabled={isSaving} style={{
                ...btnPrimary,
                background: isSaving ? '#a0aec0' : '#48bb78',
                cursor: isSaving ? 'not-allowed' : 'pointer',
              }}>
                {isSaving ? 'Saving...' : editingId ? 'Update' : 'Create'}
              </button>
              <button type="button" onClick={resetForm} style={btnSecondary}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#718096' }}>Loading doctors...</div>
      ) : doctors.length === 0 ? (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '3rem', textAlign: 'center', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', color: '#a0aec0' }}>
          No doctor catalog entries found. Click "Add Doctor Entry" to create one.
        </div>
      ) : (
        <div style={{ background: '#fff', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f7fafc', borderBottom: '2px solid #e2e8f0' }}>
                <th style={thStyle}>ID</th>
                <th style={thStyle}>User ID</th>
                <th style={thStyle}>Department</th>
                <th style={thStyle}>Specialization</th>
                <th style={thStyle}>Fee</th>
                <th style={thStyle}>Avg Time</th>
                <th style={thStyle}>Status</th>
                <th style={{ ...thStyle, textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {doctors.map((doc) => (
                <tr key={doc.id} style={{ borderBottom: '1px solid #f0f0f0' }}>
                  <td style={tdStyle}>{doc.id}</td>
                  <td style={{ ...tdStyle, fontSize: '0.8rem', fontFamily: 'monospace' }}>{doc.userId.substring(0, 8)}...</td>
                  <td style={tdStyle}>{doc.departmentName}</td>
                  <td style={tdStyle}>{doc.specialization}</td>
                  <td style={tdStyle}>₹{doc.consultationFee}</td>
                  <td style={tdStyle}>{doc.avgConsultationTimeMinutes} min</td>
                  <td style={tdStyle}>
                    <span style={{
                      display: 'inline-block', padding: '0.15rem 0.6rem', borderRadius: '999px',
                      fontSize: '0.75rem', fontWeight: 600,
                      background: doc.isAvailable ? '#c6f6d5' : '#fed7d7',
                      color: doc.isAvailable ? '#276749' : '#c53030',
                    }}>
                      {doc.isAvailable ? 'Online' : 'Offline'}
                    </span>
                  </td>
                  <td style={{ ...tdStyle, textAlign: 'right' }}>
                    <button onClick={() => handleEdit(doc)} style={btnSmallSecondary}>Edit</button>{' '}
                    <button onClick={() => handleDelete(doc.id)} style={btnSmallDanger}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <footer style={{ marginTop: '2rem', textAlign: 'center', color: '#a0aec0', fontSize: '0.8rem' }}>
        CareQ — SmartOPD AI | Day 4 — Doctor/Department Module
      </footer>
    </div>
  );
}

const thStyle: React.CSSProperties = {
  padding: '0.85rem 1rem',
  textAlign: 'left',
  fontSize: '0.8rem',
  fontWeight: 600,
  color: '#4a5568',
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
};

const tdStyle: React.CSSProperties = {
  padding: '0.85rem 1rem',
  fontSize: '0.9rem',
  color: '#1a202c',
};

const btnPrimary: React.CSSProperties = {
  padding: '0.75rem 1.5rem',
  fontSize: '0.95rem',
  fontWeight: 600,
  border: 'none',
  borderRadius: '8px',
  background: '#667eea',
  color: '#fff',
  cursor: 'pointer',
  transition: 'background 0.2s',
};

const btnSecondary: React.CSSProperties = {
  padding: '0.5rem 1rem',
  border: '1px solid #e2e8f0',
  borderRadius: '8px',
  background: '#fff',
  color: '#4a5568',
  cursor: 'pointer',
  fontSize: '0.875rem',
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

const btnSmallSecondary: React.CSSProperties = {
  padding: '0.35rem 0.85rem',
  border: '1px solid #e2e8f0',
  borderRadius: '6px',
  background: '#fff',
  color: '#4a5568',
  cursor: 'pointer',
  fontSize: '0.8rem',
  transition: 'all 0.2s',
};

const btnSmallDanger: React.CSSProperties = {
  padding: '0.35rem 0.85rem',
  border: '1px solid #e2e8f0',
  borderRadius: '6px',
  background: '#fff',
  color: '#e53e3e',
  cursor: 'pointer',
  fontSize: '0.8rem',
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
