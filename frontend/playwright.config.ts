import { defineConfig } from '@playwright/test';

/**
 * Real-browser checks against the production build. The backend must already be
 * running on port 8080 with a local database. The build is served on 5174
 * because that origin is already allowed by the backend's CORS default.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5174',
    channel: 'chrome',
  },
  webServer: {
    command: 'npm run build && npx vite preview --port 5174 --strictPort',
    url: 'http://localhost:5174',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
