/** @type {import('tailwindcss').Config} */
export default {
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
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 3px rgba(16,52,58,0.06), 0 4px 16px rgba(16,52,58,0.05)',
        lift: '0 8px 24px rgba(16,52,58,0.12)',
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
      },
      animation: {
        'soft-pulse': 'soft-pulse 2.4s ease-in-out infinite',
        'fade-in-up': 'fade-in-up 0.45s ease-out both',
        'pulse-dot': 'pulse-dot 2s ease-in-out infinite',
        'toast-in': 'toast-in 0.3s ease-out both',
      },
    },
  },
  plugins: [],
};
