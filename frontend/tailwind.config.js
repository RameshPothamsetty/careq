/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // CareQ healthcare palette — calm, trustworthy soft teals/blues.
        // Harsh saturated colors are reserved for urgency badges only.
        brand: {
          50: '#f0fbfa',
          100: '#d2f3f0',
          200: '#a5e6e2',
          300: '#6fd2cf',
          400: '#3eb8b8',
          500: '#219a9d',
          600: '#0e7c81',
          700: '#0f6369',
          800: '#115057',
          900: '#11434a',
        },
        ink: {
          DEFAULT: '#10343a',
          soft: '#45666d',
          muted: '#7d9ba1',
        },
        surface: '#f4f9f9',
        // Dark-mode surfaces (staff dashboards) — deep teal-tinted charcoal,
        // Linear/Vercel style layered elevation instead of drop shadows.
        night: {
          950: '#070d11',
          900: '#0a1216',
          800: '#101a20',
          700: '#16222a',
          600: '#1e2c35',
        },
      },
      fontFamily: {
        sans: [
          'Inter',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Noto Sans Devanagari',
          'Noto Sans Telugu',
          'sans-serif',
        ],
        display: [
          'Plus Jakarta Sans',
          'Inter',
          'Noto Sans Devanagari',
          'Noto Sans Telugu',
          'sans-serif',
        ],
      },
      boxShadow: {
        card: '0 1px 3px rgba(16,52,58,0.06), 0 4px 16px rgba(16,52,58,0.05)',
        lift: '0 8px 24px rgba(16,52,58,0.12)',
        glow: '0 0 0 1px rgba(14,124,129,0.18), 0 8px 32px rgba(14,124,129,0.28)',
        'glow-soft': '0 0 24px rgba(33,154,157,0.22)',
      },
      keyframes: {
        'soft-pulse': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.45' },
        },
        'fade-in-up': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'pulse-dot': {
          '0%, 100%': { boxShadow: '0 0 0 0 rgba(14,124,129,0.45)' },
          '50%': { boxShadow: '0 0 0 6px rgba(14,124,129,0)' },
        },
        'toast-in': {
          '0%': { opacity: '0', transform: 'translateX(24px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
        'drawer-in': {
          '0%': { opacity: '0.4', transform: 'translateX(100%)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
        // ── 2026 motion system ─────────────────────────────────────
        'blob-drift': {
          '0%': { transform: 'translate(0, 0) scale(1)' },
          '50%': { transform: 'translate(36px, -28px) scale(1.18)' },
          '100%': { transform: 'translate(-24px, 30px) scale(0.94)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        'gradient-x': {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
        'glow-pulse': {
          '0%, 100%': { boxShadow: '0 0 0 0 rgba(33,154,157,0.45)' },
          '50%': { boxShadow: '0 0 0 10px rgba(33,154,157,0)' },
        },
        'scale-in': {
          '0%': { opacity: '0', transform: 'scale(0.96)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
      },
      animation: {
        'soft-pulse': 'soft-pulse 2.4s ease-in-out infinite',
        'fade-in-up': 'fade-in-up 0.45s ease-out both',
        'pulse-dot': 'pulse-dot 2s ease-in-out infinite',
        'toast-in': 'toast-in 0.3s ease-out both',
        'drawer-in': 'drawer-in 0.28s ease-out both',
        'blob-drift': 'blob-drift 18s ease-in-out infinite alternate',
        shimmer: 'shimmer 1.6s infinite',
        'gradient-x': 'gradient-x 8s ease infinite',
        'glow-pulse': 'glow-pulse 2.4s ease-in-out infinite',
        'scale-in': 'scale-in 0.25s ease-out both',
      },
    },
  },
  plugins: [],
};
