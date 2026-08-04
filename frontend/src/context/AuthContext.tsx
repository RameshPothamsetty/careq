import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import { api, type AuthResponse } from '../services/api';
import { UNAUTHORIZED_EVENT } from '../services/rtk/baseQuery';
import { resetApiState } from '../store';

interface User {
  id: string;
  email: string;
  fullName: string;
  role: string;
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (fullName: string, email: string, password: string, role: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Restore session from localStorage on mount
  useEffect(() => {
    const savedToken = localStorage.getItem('careq_token');
    const savedUser = localStorage.getItem('careq_user');
    if (savedToken && savedUser) {
      setToken(savedToken);
      setUser(JSON.parse(savedUser));
    }
    setIsLoading(false);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response: AuthResponse = await api.login({ email, password });
    const userData: User = {
      id: response.userId,
      email: response.email,
      fullName: response.fullName,
      role: response.role,
    };
    setToken(response.token);
    setUser(userData);
    localStorage.setItem('careq_token', response.token);
    localStorage.setItem('careq_user', JSON.stringify(userData));
  }, []);

  const signup = useCallback(async (fullName: string, email: string, password: string, role: string) => {
    const response: AuthResponse = await api.signup({ fullName, email, password, role });
    const userData: User = {
      id: response.userId,
      email: response.email,
      fullName: response.fullName,
      role: response.role,
    };
    setToken(response.token);
    setUser(userData);
    localStorage.setItem('careq_token', response.token);
    localStorage.setItem('careq_user', JSON.stringify(userData));
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('careq_token');
    localStorage.removeItem('careq_user');
    // Wipe RTK Query caches so the next session never sees this user's data.
    resetApiState();
  }, []);

  // A 401 from any authenticated endpoint (expired/invalid JWT) is detected
  // centrally in the RTK Query baseQuery, which fires this event → force
  // logout so the user is never left stuck on stale cached data. `logout` is
  // stable, so this listener is registered once.
  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, logout);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logout);
  }, [logout]);

  const value: AuthContextType = {
    user,
    token,
    isAuthenticated: !!token && !!user,
    isLoading,
    login,
    signup,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
