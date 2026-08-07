import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { useAuth } from '../context/AuthContext';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const mockedUseAuth = vi.mocked(useAuth);

function mockAuth(overrides: Partial<ReturnType<typeof useAuth>>) {
  mockedUseAuth.mockReturnValue({
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    login: vi.fn(),
    signup: vi.fn(),
    logout: vi.fn(),
    ...overrides,
  } as ReturnType<typeof useAuth>);
}

function renderProtected() {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route path="/login" element={<div>LOGIN PAGE</div>} />
        <Route path="/patient/dashboard" element={<div>PATIENT DASHBOARD</div>} />
        <Route path="/doctor/dashboard" element={<div>DOCTOR DASHBOARD</div>} />
        <Route
          path="/protected"
          element={
            <ProtectedRoute allowedRoles={['DOCTOR']}>
              <div>PROTECTED CONTENT</div>
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    mockedUseAuth.mockReset();
  });

  it('shows a loading placeholder while the session is restoring', () => {
    mockAuth({ isAuthenticated: false, isLoading: true });
    renderProtected();
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('redirects unauthenticated users to /login', () => {
    mockAuth({ isAuthenticated: false, isLoading: false });
    renderProtected();
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument();
  });

  it('redirects a role that is not allowed to its own dashboard', () => {
    mockAuth({
      isAuthenticated: true,
      isLoading: false,
      user: { id: 'u1', email: 'p@careq.com', fullName: 'Patient', role: 'PATIENT' },
    });
    renderProtected();
    expect(screen.getByText('PATIENT DASHBOARD')).toBeInTheDocument();
  });

  it('renders the children when the role is allowed', () => {
    mockAuth({
      isAuthenticated: true,
      isLoading: false,
      user: { id: 'u2', email: 'd@careq.com', fullName: 'Doctor', role: 'DOCTOR' },
    });
    renderProtected();
    expect(screen.getByText('PROTECTED CONTENT')).toBeInTheDocument();
  });
});
