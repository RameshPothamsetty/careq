import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useAuth } from './AuthContext';

export type ThemeMode = 'auto' | 'light' | 'dark';

const STORAGE_KEY = 'careq_theme';

interface ThemeContextType {
  mode: ThemeMode;
  /** True when the effective theme is dark (manual override OR auto+staff). */
  isDark: boolean;
  setMode: (mode: ThemeMode) => void;
}

const DEFAULT_CONTEXT: ThemeContextType = {
  mode: 'auto',
  isDark: false,
  setMode: () => {},
};

const ThemeContext = createContext<ThemeContextType>(DEFAULT_CONTEXT);

function readStoredMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark' || saved === 'auto') return saved;
  } catch {
    // localStorage unavailable (private mode / tests) — default to auto.
  }
  return 'auto';
}

/**
 * ThemeProvider — light/dark theming with a manual override on top of the
 * role-based default:
 *
 *   auto  → staff (DOCTOR/ADMIN) get dark, patients get light
 *   light → force light everywhere
 *   dark  → force dark everywhere
 *
 * The choice persists in localStorage (`careq_theme`) so it survives
 * refreshes and logins. Must live INSIDE AuthProvider (the auto default
 * depends on the signed-in role).
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [mode, setModeState] = useState<ThemeMode>(readStoredMode);

  const isStaff = user?.role === 'DOCTOR' || user?.role === 'ADMIN';
  const isDark = mode === 'dark' || (mode === 'auto' && isStaff);

  // Hoist the effective theme to <html> so ANY chrome outside a page root
  // (toasts, portals, future modals/drawers) inherits dark: variants too.
  // Pages still carry their own `dark` class for first-paint correctness
  // (the effect runs after paint), but the two are idempotent.
  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark);
  }, [isDark]);

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Ignore storage failures — the choice just won't persist.
    }
  }, []);

  const value = useMemo(() => ({ mode, isDark, setMode }), [mode, isDark, setMode]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextType {
  return useContext(ThemeContext);
}
