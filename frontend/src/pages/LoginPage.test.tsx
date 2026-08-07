import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import LoginPage from './LoginPage';
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

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/:role/dashboard" element={<div>ROLE DASHBOARD</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    mockedUseAuth.mockReset();
    localStorage.clear();
  });

  it('renders the sign-in form with email, password and submit button', () => {
    mockAuth({ login: vi.fn() });
    renderLogin();
    expect(screen.getByRole('heading', { name: 'CareQ' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('you@hospital.com')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Enter your password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
    // Link to signup exists for new users.
    expect(screen.getByRole('link', { name: /create one/i })).toBeInTheDocument();
  });

  it('submits the credentials and navigates to the role dashboard', async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    mockAuth({ login });
    // The real login flow persists the user to localStorage; emulate that so
    // the component's role lookup after a successful login resolves to PATIENT.
    localStorage.setItem('careq_user', JSON.stringify({ role: 'PATIENT' }));
    renderLogin();

    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText('you@hospital.com'), 'john@careq.com');
    await user.type(screen.getByPlaceholderText('Enter your password'), 'password123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(login).toHaveBeenCalledWith('john@careq.com', 'password123');
    expect(await screen.findByText('ROLE DASHBOARD')).toBeInTheDocument();
  });

  it('shows the error message when login fails and stays on the form', async () => {
    mockAuth({
      login: vi.fn().mockRejectedValue(new Error('Invalid email or password')),
    });
    renderLogin();

    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText('you@hospital.com'), 'john@careq.com');
    await user.type(screen.getByPlaceholderText('Enter your password'), 'wrong');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    // The error renders inside a "⚠ <message>" banner, so match on the message substring.
    expect(await screen.findByText(/Invalid email or password/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });
});
