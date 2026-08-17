import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3030,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Real-time notification socket — WS upgrade proxied to the gateway.
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  // `vite preview` does NOT apply server.proxy — mirror it here so
  // `npm run build && npm run preview` keeps working with relative /api calls.
  preview: {
    port: 3030,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  // Component tests run in jsdom with the jest-dom matchers loaded once.
  // (Only src is included — the Playwright E2E specs under e2e/ are run via
  // `npm run test:e2e`, not Vitest.)
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
