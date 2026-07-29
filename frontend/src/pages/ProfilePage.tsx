import { useState, useEffect, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { api } from '../services/api';

interface UserProfile {
  id: number;
  userId: string;
  phone: string | null;
  address: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  profilePictureUrl: string | null;
  role: string;
}

interface UpdateProfilePayload {
  phone?: string;
  address?: string;
  dateOfBirth?: string;
  gender?: string;
  profilePictureUrl?: string;
}

export default function ProfilePage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  // Form fields
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [gender, setGender] = useState('');
  const [profilePictureUrl, setProfilePictureUrl] = useState('');

  useEffect(() => {
    fetchProfile();
  }, []);

  const fetchProfile = async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await api.getProfile();
      setProfile(data);
      setPhone(data.phone || '');
      setAddress(data.address || '');
      setDateOfBirth(data.dateOfBirth || '');
      setGender(data.gender || '');
      setProfilePictureUrl(data.profilePictureUrl || '');
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load profile');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsSaving(true);

    try {
      const payload: UpdateProfilePayload = {};
      if (phone) payload.phone = phone;
      if (address) payload.address = address;
      if (dateOfBirth) payload.dateOfBirth = dateOfBirth;
      if (gender) payload.gender = gender;
      if (profilePictureUrl) payload.profilePictureUrl = profilePictureUrl;

      const updated = await api.updateProfile(payload);
      setProfile(updated);
      setSuccess('Profile updated successfully');
      setIsEditing(false);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to update profile');
      }
    } finally {
      setIsSaving(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '0.75rem',
    border: '1px solid #e2e8f0',
    borderRadius: '8px',
    fontSize: '0.95rem',
    boxSizing: 'border-box',
    outline: 'none',
    backgroundColor: isEditing ? '#fff' : '#f7fafc',
  };

  const labelStyle: React.CSSProperties = {
    display: 'block',
    marginBottom: '0.25rem',
    fontWeight: 600,
    fontSize: '0.85rem',
    color: '#4a5568',
  };

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', fontFamily: 'system-ui, sans-serif', color: '#4a5568' }}>
        Loading profile...
      </div>
    );
  }

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '900px', margin: '0 auto', background: '#f7fafc', minHeight: '100vh' }}>
      {/* Header */}
      <header style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginBottom: '2rem', padding: '1rem 1.5rem', background: '#fff',
        borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
      }}>
        <div>
          <h1 style={{ margin: 0, color: '#1a202c', fontSize: '1.5rem' }}>👤 My Profile</h1>
          <p style={{ margin: '0.25rem 0 0', color: '#718096', fontSize: '0.875rem' }}>
            Welcome, {user?.fullName || 'User'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button
            onClick={() => navigate(-1)}
            style={{
              padding: '0.5rem 1rem', border: '1px solid #e2e8f0', borderRadius: '8px',
              background: '#fff', color: '#4a5568', cursor: 'pointer', fontSize: '0.875rem',
              transition: 'all 0.2s'
            }}
            onMouseEnter={(e) => { e.currentTarget.style.background = '#f7fafc'; }}
            onMouseLeave={(e) => { e.currentTarget.style.background = '#fff'; }}
          >
            ← Back
          </button>
          <button
            onClick={handleLogout}
            style={{
              padding: '0.5rem 1.25rem', border: '1px solid #e2e8f0', borderRadius: '8px',
              background: '#fff', color: '#e53e3e', cursor: 'pointer', fontSize: '0.875rem',
              fontWeight: 500, transition: 'all 0.2s'
            }}
            onMouseEnter={(e) => { e.currentTarget.style.background = '#fff5f5'; e.currentTarget.style.borderColor = '#fc8181'; }}
            onMouseLeave={(e) => { e.currentTarget.style.background = '#fff'; e.currentTarget.style.borderColor = '#e2e8f0'; }}
          >
            Logout
          </button>
        </div>
      </header>

      {/* Notification messages */}
      {error && (
        <div style={{ background: '#fed7d7', color: '#c53030', padding: '0.75rem 1rem', borderRadius: '8px', marginBottom: '1rem', fontSize: '0.875rem' }}>
          {error}
        </div>
      )}
      {success && (
        <div style={{ background: '#c6f6d5', color: '#276749', padding: '0.75rem 1rem', borderRadius: '8px', marginBottom: '1rem', fontSize: '0.875rem' }}>
          {success}
        </div>
      )}

      {/* Main content */}
      <div style={{ background: '#fff', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', overflow: 'hidden' }}>
        {/* Profile header */}
        <div style={{ padding: '2rem 2rem 1.5rem', borderBottom: '1px solid #e2e8f0' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
            <div style={{
              width: '80px', height: '80px', borderRadius: '50%',
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: '#fff', fontSize: '2rem', fontWeight: 700
            }}>
              {user?.fullName?.charAt(0) || 'U'}
            </div>
            <div>
              <h2 style={{ margin: 0, color: '#1a202c', fontSize: '1.35rem' }}>{user?.fullName}</h2>
              <p style={{ margin: '0.25rem 0', color: '#718096', fontSize: '0.9rem' }}>{user?.email}</p>
              <span style={{
                display: 'inline-block', padding: '0.2rem 0.75rem', borderRadius: '999px',
                fontSize: '0.75rem', fontWeight: 600, background: '#ebf8ff', color: '#2b6cb0'
              }}>
                {user?.role}
              </span>
            </div>
          </div>
        </div>

        {/* Profile form */}
        <form onSubmit={handleSubmit} style={{ padding: '1.5rem 2rem 2rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem' }}>
            <div>
              <label style={labelStyle}>Phone</label>
              <input
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+1234567890"
                disabled={!isEditing}
                style={inputStyle}
                onFocus={(e) => { if (isEditing) e.target.style.borderColor = '#667eea'; }}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              />
            </div>

            <div>
              <label style={labelStyle}>Gender</label>
              <select
                value={gender}
                onChange={(e) => setGender(e.target.value)}
                disabled={!isEditing}
                style={inputStyle}
                onFocus={(e) => { if (isEditing) e.target.style.borderColor = '#667eea'; }}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              >
                <option value="">Select gender</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
            </div>

            <div>
              <label style={labelStyle}>Date of Birth</label>
              <input
                type="date"
                value={dateOfBirth}
                onChange={(e) => setDateOfBirth(e.target.value)}
                disabled={!isEditing}
                style={inputStyle}
                onFocus={(e) => { if (isEditing) e.target.style.borderColor = '#667eea'; }}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              />
            </div>

            <div>
              <label style={labelStyle}>Profile Picture URL</label>
              <input
                type="url"
                value={profilePictureUrl}
                onChange={(e) => setProfilePictureUrl(e.target.value)}
                placeholder="https://example.com/avatar.jpg"
                disabled={!isEditing}
                style={inputStyle}
                onFocus={(e) => { if (isEditing) e.target.style.borderColor = '#667eea'; }}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              />
            </div>
          </div>

          <div style={{ marginTop: '1.25rem' }}>
            <label style={labelStyle}>Address</label>
            <textarea
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              placeholder="Enter your address"
              disabled={!isEditing}
              rows={3}
              style={{ ...inputStyle, resize: 'vertical', fontFamily: 'inherit' }}
              onFocus={(e) => { if (isEditing) e.target.style.borderColor = '#667eea'; }}
              onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
            />
          </div>

          {/* Action buttons */}
          <div style={{ marginTop: '1.5rem', display: 'flex', gap: '0.75rem' }}>
            {!isEditing ? (
              <button
                type="button"
                onClick={() => setIsEditing(true)}
                style={{
                  padding: '0.75rem 2rem', fontSize: '0.95rem', fontWeight: 600,
                  border: 'none', borderRadius: '8px', background: '#667eea',
                  color: '#fff', cursor: 'pointer', transition: 'background 0.2s'
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = '#5a67d8'; }}
                onMouseLeave={(e) => { e.currentTarget.style.background = '#667eea'; }}
              >
                Edit Profile
              </button>
            ) : (
              <>
                <button
                  type="submit"
                  disabled={isSaving}
                  style={{
                    padding: '0.75rem 2rem', fontSize: '0.95rem', fontWeight: 600,
                    border: 'none', borderRadius: '8px',
                    background: isSaving ? '#a0aec0' : '#48bb78',
                    color: '#fff', cursor: isSaving ? 'not-allowed' : 'pointer',
                    transition: 'background 0.2s'
                  }}
                  onMouseEnter={(e) => { if (!isSaving) e.currentTarget.style.background = '#38a169'; }}
                  onMouseLeave={(e) => { if (!isSaving) e.currentTarget.style.background = '#48bb78'; }}
                >
                  {isSaving ? 'Saving...' : 'Save Changes'}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setIsEditing(false);
                    setPhone(profile?.phone || '');
                    setAddress(profile?.address || '');
                    setDateOfBirth(profile?.dateOfBirth || '');
                    setGender(profile?.gender || '');
                    setProfilePictureUrl(profile?.profilePictureUrl || '');
                  }}
                  style={{
                    padding: '0.75rem 2rem', fontSize: '0.95rem', fontWeight: 500,
                    border: '1px solid #e2e8f0', borderRadius: '8px', background: '#fff',
                    color: '#4a5568', cursor: 'pointer', transition: 'all 0.2s'
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = '#f7fafc'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = '#fff'; }}
                >
                  Cancel
                </button>
              </>
            )}
          </div>
        </form>
      </div>

      <footer style={{ marginTop: '2rem', textAlign: 'center', color: '#a0aec0', fontSize: '0.8rem' }}>
        CareQ — SmartOPD AI | Day 3 — User Module Complete
      </footer>
    </div>
  );
}
