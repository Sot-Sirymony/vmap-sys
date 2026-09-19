import { readFileSync } from 'node:fs';
import path from 'node:path';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const { version } = JSON.parse(
  readFileSync(path.resolve(__dirname, './package.json'), 'utf-8'),
) as { version: string };

// Exposed to the app as import.meta.env.VITE_APP_VERSION. Set here rather than
// via `define` because define is only statically replaced at build time, so a
// bare global would be undefined in dev.
process.env.VITE_APP_VERSION = version;

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
  },
  build: {
    rollupOptions: {
      output: {
        // React/router/MUI change on our own release cadence, not the
        // browser's own cache lifetime, and they're already part of the
        // eager entry bundle every page depends on either way — naming
        // them their own chunk just means a deploy that only touches app
        // code no longer busts the cache for library code that didn't
        // change. Everything else (recharts, per-page code) is left to
        // Rollup's own chunking so the existing lazy-route splitting
        // (see App.tsx) keeps recharts out of this eager graph.
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined;
          }
          if (/[\\/](react|react-dom|scheduler)[\\/]/.test(id)) {
            return 'vendor-react';
          }
          if (id.includes('react-router')) {
            return 'vendor-router';
          }
          if (id.includes('@mui') || id.includes('@emotion')) {
            return 'vendor-mui';
          }
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
});
