import { useState, useEffect, useRef, type FormEvent } from 'react';
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

export default function ProfilePage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [gender, setGender] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    fetchProfile();
  }, []);

  const fetchProfile = async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await api.getProfile();
      setProfile(data);
      fillForm(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load profile');
    } finally {
      setIsLoading(false);
    }
  };

  const fillForm = (data: UserProfile) => {
    setPhone(data.phone || '');
    setAddress(data.address || '');
    setDateOfBirth(data.dateOfBirth || '');
    setGender(data.gender || '');
  };

  const handleEdit = () => {
    if (profile) fillForm(profile);
    setSelectedFile(null);
    setPreviewUrl(null);
    setIsEditing(true);
    setError('');
    setSuccess('');
  };

  const handleCancel = () => {
    if (profile) fillForm(profile);
    setSelectedFile(null);
    setPreviewUrl(null);
    setIsEditing(false);
    setError('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsSaving(true);

    try {
      let updatedProfile = profile;

      // Step 1: Upload profile picture if a file was selected
      if (selectedFile) {
        try {
          updatedProfile = await api.uploadProfilePicture(selectedFile);
        } catch (uploadErr) {
          setError(uploadErr instanceof Error ? uploadErr.message : 'Failed to upload picture');
          setIsSaving(false);
          return;
        }
      }

      // Step 2: Update profile fields
      const payload: Record<string, string> = {};
      if (phone) payload.phone = phone;
      if (address) payload.address = address;
      if (dateOfBirth) payload.dateOfBirth = dateOfBirth;
      if (gender) payload.gender = gender;

      if (Object.keys(payload).length > 0) {
        updatedProfile = await api.updateProfile(payload);
      }

      setProfile(updatedProfile);
      setSuccess('Profile updated successfully');
      setSelectedFile(null);
      setPreviewUrl(null);
      setIsEditing(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update profile');
    } finally {
      setIsSaving(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      // Create a local preview URL
      setPreviewUrl(URL.createObjectURL(file));
    }
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

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', fontFamily: 'system-ui, sans-serif', color: '#4a5568' }}>
        Loading profile...
      </div>
    );
  }

  // ---- VIEW MODE ----
  if (!isEditing) {
    return (
      <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '900px', margin: '0 auto', background: '#f7fafc', minHeight: '100vh' }}>
        <Header user={user} onBack={() => navigate(-1)} onLogout={handleLogout} />

        {error && <Alert type="error" message={error} />}
        {success && <Alert type="success" message={success} />}

        <Card>
          <ProfileAvatar user={user} profilePictureUrl={profile?.profilePictureUrl} previewUrl={previewUrl} />

          <div style={{ padding: '0 2rem 2rem' }}>
            <InfoRow label="Phone" value={profile?.phone || 'Not set'} />
            <InfoRow label="Email" value={user?.email || ''} />
            <InfoRow label="Gender" value={profile?.gender || 'Not set'} />
            <InfoRow label="Date of Birth" value={profile?.dateOfBirth || 'Not set'} />
            <InfoRow label="Address" value={profile?.address || 'Not set'} />
            <InfoRow label="Profile Picture" value={profile?.profilePictureUrl || 'Not set'} isLast />

            <div style={{ marginTop: '1.5rem' }}>
              <button
                onClick={handleEdit}
                style={{
                  padding: '0.75rem 2rem',
                  fontSize: '0.95rem',
                  fontWeight: 600,
                  border: 'none',
                  borderRadius: '8px',
                  background: '#667eea',
                  color: '#fff',
                  cursor: 'pointer',
                  transition: 'background 0.2s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = '#5a67d8'; }}
                onMouseLeave={(e) => { e.currentTarget.style.background = '#667eea'; }}
              >
                Edit Profile
              </button>
            </div>
          </div>
        </Card>

        <Footer />
      </div>
    );
  }

  // ---- EDIT MODE ----
  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '900px', margin: '0 auto', background: '#f7fafc', minHeight: '100vh' }}>
      <Header user={user} onBack={() => navigate(-1)} onLogout={handleLogout} />

      {error && <Alert type="error" message={error} />}

      <Card>          <ProfileAvatar user={user} profilePictureUrl={profile?.profilePictureUrl} previewUrl={previewUrl} />

        <form onSubmit={handleSubmit} style={{ padding: '0 2rem 2rem' }}>
          <div style={{ marginBottom: '1.25rem' }}>
            <label style={labelStyle}>Phone</label>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+1234567890"
              style={inputStyle}
              autoFocus
              onFocus={(e) => e.target.style.borderColor = '#667eea'}
              onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem', marginBottom: '1.25rem' }}>
            <div>
              <label style={labelStyle}>Gender</label>
              <select
                value={gender}
                onChange={(e) => setGender(e.target.value)}
                style={inputStyle}
                onFocus={(e) => e.target.style.borderColor = '#667eea'}
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
                style={inputStyle}
                onFocus={(e) => e.target.style.borderColor = '#667eea'}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              />
            </div>
          </div>

          <div style={{ marginBottom: '1.25rem' }}>
            <label style={labelStyle}>Address</label>
            <textarea
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              placeholder="Enter your address"
              rows={3}
              style={{ ...inputStyle, resize: 'vertical', fontFamily: 'inherit' }}
              onFocus={(e) => e.target.style.borderColor = '#667eea'}
              onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
            />
          </div>

          <div style={{ marginBottom: '1.5rem' }}>
            <label style={labelStyle}>Profile Picture</label>
            <div style={{
              display: 'flex', alignItems: 'center', gap: '0.75rem',
              padding: '0.7rem 0.85rem', border: '1px solid #e2e8f0',
              borderRadius: '8px', background: '#fff',
            }}>
              <input
                type="file"
                accept="image/*"
                ref={fileInputRef}
                onChange={handleFileChange}
                style={{ display: 'none' }}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                style={{
                  padding: '0.5rem 1rem',
                  background: '#667eea',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  fontWeight: 500,
                  whiteSpace: 'nowrap',
                }}
              >
                Choose Image
              </button>
              <span style={{ color: selectedFile ? '#4a5568' : '#a0aec0', fontSize: '0.9rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {selectedFile ? selectedFile.name : (profile?.profilePictureUrl || 'No file selected')}
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <button
              type="submit"
              disabled={isSaving}
              style={{
                padding: '0.75rem 2rem',
                fontSize: '0.95rem',
                fontWeight: 600,
                border: 'none',
                borderRadius: '8px',
                background: isSaving ? '#a0aec0' : '#48bb78',
                color: '#fff',
                cursor: isSaving ? 'not-allowed' : 'pointer',
                transition: 'background 0.2s',
              }}
              onMouseEnter={(e) => { if (!isSaving) e.currentTarget.style.background = '#38a169'; }}
              onMouseLeave={(e) => { if (!isSaving) e.currentTarget.style.background = '#48bb78'; }}
            >
              {isSaving ? 'Saving...' : 'Save Changes'}
            </button>
            <button
              type="button"
              onClick={handleCancel}
              style={{
                padding: '0.75rem 2rem',
                fontSize: '0.95rem',
                fontWeight: 500,
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
                background: '#fff',
                color: '#4a5568',
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.background = '#f7fafc'; }}
              onMouseLeave={(e) => { e.currentTarget.style.background = '#fff'; }}
            >
              Cancel
            </button>
          </div>
        </form>
      </Card>

      <Footer />
    </div>
  );
}

// ---- Sub-components ----

function Header({ user, onBack, onLogout }: { user: any; onBack: () => void; onLogout: () => void }) {
  return (
    <header style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      marginBottom: '2rem', padding: '1rem 1.5rem', background: '#fff',
      borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
    }}>
      <div>
        <h1 style={{ margin: 0, color: '#1a202c', fontSize: '1.5rem' }}>👤 My Profile</h1>
        <p style={{ margin: '0.25rem 0 0', color: '#718096', fontSize: '0.875rem' }}>
          Welcome, {user?.fullName || 'User'}
        </p>
      </div>
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <button onClick={onBack} style={btnSecondary}>← Back</button>
        <button onClick={onLogout} style={btnDanger}>Logout</button>
      </div>
    </header>
  );
}

function Alert({ type, message }: { type: 'error' | 'success'; message: string }) {
  const bg = type === 'error' ? '#fed7d7' : '#c6f6d5';
  const color = type === 'error' ? '#c53030' : '#276749';
  return (
    <div style={{ background: bg, color, padding: '0.75rem 1rem', borderRadius: '8px', marginBottom: '1rem', fontSize: '0.875rem' }}>
      {message}
    </div>
  );
}

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ background: '#fff', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', overflow: 'hidden' }}>
      {children}
    </div>
  );
}

function ProfileAvatar({ user, profilePictureUrl, previewUrl }: { user: any; profilePictureUrl?: string | null; previewUrl?: string | null }) {
  const displayUrl = previewUrl || profilePictureUrl;
  return (
    <div style={{ padding: '2rem 2rem 1.5rem', borderBottom: '1px solid #e2e8f0' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
        {displayUrl ? (
          <img
            src={displayUrl}
            alt="Profile"
            style={{
              width: '80px', height: '80px', borderRadius: '50%',
              objectFit: 'cover', border: previewUrl ? '3px solid #48bb78' : 'none',
            }}
          />
        ) : (
          <div style={{
            width: '80px', height: '80px', borderRadius: '50%',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#fff', fontSize: '2rem', fontWeight: 700,
          }}>
            {user?.fullName?.charAt(0) || 'U'}
          </div>
        )}
        <div>
          <h2 style={{ margin: 0, color: '#1a202c', fontSize: '1.35rem' }}>{user?.fullName}</h2>
          <p style={{ margin: '0.25rem 0', color: '#718096', fontSize: '0.9rem' }}>{user?.email}</p>
          <span style={{
            display: 'inline-block', padding: '0.2rem 0.75rem', borderRadius: '999px',
            fontSize: '0.75rem', fontWeight: 600, background: '#ebf8ff', color: '#2b6cb0',
          }}>
            {user?.role}
          </span>
        </div>
      </div>
    </div>
  );
}

function InfoRow({ label, value, isLast }: { label: string; value: string; isLast?: boolean }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      padding: '0.85rem 0', borderBottom: isLast ? 'none' : '1px solid #f0f0f0',
    }}>
      <span style={{ fontWeight: 500, color: '#4a5568', fontSize: '0.9rem' }}>{label}</span>
      <span style={{ color: value === 'Not set' ? '#a0aec0' : '#1a202c', fontSize: '0.9rem' }}>{value}</span>
    </div>
  );
}

function Footer() {
  return (
    <footer style={{ marginTop: '2rem', textAlign: 'center', color: '#a0aec0', fontSize: '0.8rem' }}>
      CareQ — SmartOPD AI | Day 3 — User Module Complete
    </footer>
  );
}

const btnSecondary: React.CSSProperties = {
  padding: '0.5rem 1rem', border: '1px solid #e2e8f0', borderRadius: '8px',
  background: '#fff', color: '#4a5568', cursor: 'pointer', fontSize: '0.875rem',
  transition: 'all 0.2s',
};

const btnDanger: React.CSSProperties = {
  padding: '0.5rem 1.25rem', border: '1px solid #e2e8f0', borderRadius: '8px',
  background: '#fff', color: '#e53e3e', cursor: 'pointer', fontSize: '0.875rem',
  fontWeight: 500, transition: 'all 0.2s',
};
