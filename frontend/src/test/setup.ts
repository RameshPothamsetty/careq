import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Unmount rendered trees after each test so state never leaks between tests.
afterEach(() => {
  cleanup();
});
