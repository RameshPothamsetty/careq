import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api, type DepartmentResponse } from '../services/api';

export default function AdminDepartmentManager() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Form state
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    fetchDepartments();
  }, []);

  const fetchDepartments = async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await api.getDepartments();
      setDepartments(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load departments');
    } finally {
      setIsLoading(false);
    }
  };

  const handleEdit = (dept: DepartmentResponse) => {
    setEditingId(dept.id);
    setName(dept.name);
    setDescription(dept.description || '');
    setShowForm(true);
    setError('');
    setSuccess('');
  };

  const handleAdd = () => {
    setEditingId(null);
    setName('');
    setDescription('');
    setShowForm(true);
    setError('');
    setSuccess('');
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingId(null);
    setName('');
    setDescription('');
    setError('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsSaving(true);

    try {
      if (editingId) {
        await api.updateDepartment(editingId, { name, description });
        setSuccess('Department updated successfully');
      } else {
        await api.createDepartment({ name, description });
        setSuccess('Department created successfully');
      }
      setShowForm(false);
      setEditingId(null);
      setName('');
      setDescription('');
      await fetchDepartments();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save department');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this department?')) return;
    setError('');
    setSuccess('');
    try {
      await api.deleteDepartment(id);
      setSuccess('Department deleted successfully');
      await fetchDepartments();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to delete department');
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
          <h1 style={{ margin: 0, color: '#1a202c', fontSize: '1.5rem' }}>🏥 Manage Departments</h1>
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
            + Add Department
          </button>
        </div>
      )}

      {showForm && (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '2rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', marginBottom: '1.5rem' }}>
          <h2 style={{ margin: '0 0 1.25rem', color: '#4a5568', fontSize: '1.15rem' }}>
            {editingId ? 'Edit Department' : 'New Department'}
          </h2>
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: '1.25rem' }}>
              <label style={labelStyle}>Name *</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g., Cardiology"
                required
                style={inputStyle}
                onFocus={(e) => e.target.style.borderColor = '#667eea'}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              />
            </div>
            <div style={{ marginBottom: '1.25rem' }}>
              <label style={labelStyle}>Description</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Brief description of the department"
                rows={3}
                style={{ ...inputStyle, resize: 'vertical', fontFamily: 'inherit' }}
                onFocus={(e) => e.target.style.borderColor = '#667eea'}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              />
            </div>
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button type="submit" disabled={isSaving} style={{
                ...btnPrimary,
                background: isSaving ? '#a0aec0' : '#48bb78',
                cursor: isSaving ? 'not-allowed' : 'pointer',
              }}>
                {isSaving ? 'Saving...' : editingId ? 'Update' : 'Create'}
              </button>
              <button type="button" onClick={handleCancel} style={btnSecondary}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#718096' }}>Loading departments...</div>
      ) : departments.length === 0 ? (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '3rem', textAlign: 'center', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', color: '#a0aec0' }}>
          No departments found. Click "Add Department" to create one.
        </div>
      ) : (
        <div style={{ background: '#fff', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f7fafc', borderBottom: '2px solid #e2e8f0' }}>
                <th style={thStyle}>ID</th>
                <th style={thStyle}>Name</th>
                <th style={thStyle}>Description</th>
                <th style={thStyle}>Status</th>
                <th style={{ ...thStyle, textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {departments.map((dept) => (
                <tr key={dept.id} style={{ borderBottom: '1px solid #f0f0f0' }}>
                  <td style={tdStyle}>{dept.id}</td>
                  <td style={{ ...tdStyle, fontWeight: 600 }}>{dept.name}</td>
                  <td style={{ ...tdStyle, color: '#718096' }}>{dept.description || '—'}</td>
                  <td style={tdStyle}>
                    <span style={{
                      display: 'inline-block', padding: '0.15rem 0.6rem', borderRadius: '999px',
                      fontSize: '0.75rem', fontWeight: 600,
                      background: dept.isActive ? '#c6f6d5' : '#fed7d7',
                      color: dept.isActive ? '#276749' : '#c53030',
                    }}>
                      {dept.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td style={{ ...tdStyle, textAlign: 'right' }}>
                    <button onClick={() => handleEdit(dept)} style={btnSmallSecondary}>Edit</button>{' '}
                    <button onClick={() => handleDelete(dept.id)} style={btnSmallDanger}>Delete</button>
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

// Shared styles
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
