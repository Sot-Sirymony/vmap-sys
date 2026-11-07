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
        //
        // These all go into ONE chunk, not one each. @mui/@emotion
        // reference React at module scope, so splitting them into
        // separate chunks from react/react-router creates a circular
        // chunk dependency: Rollup can't fully order the load, and one
        // chunk ends up reading a const/class binding from another
        // before that chunk finished initializing ("Cannot access 'X'
        // before initialization" at runtime). Keeping them together
        // avoids the cross-chunk cycle while still isolating them from
        // app code for caching purposes.
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined;
          }
          // Recharts and the d3 packages under it are the largest thing the
          // app ships, and only the dashboard's figures use them. They are
          // already reached exclusively through lazy imports (see
          // components/dashboard/charts), so Rollup was giving them a chunk of
          // their own anyway — but naming it after whichever shared module it
          // happened to pick made a 345kB chart bundle read as
          // "ChartTooltipContent" in the build output. Naming it here only
          // fixes that label; the loading behaviour is unchanged. It depends
          // on the vendor chunk below and nothing depends on it, so there is
          // no cross-chunk cycle of the kind described next.
          if (
            id.includes('recharts') ||
            id.includes('victory-vendor') ||
            /[\\/]d3-[a-z-]+[\\/]/.test(id)
          ) {
            return 'charts';
          }
          if (
            /[\\/](react|react-dom|scheduler)[\\/]/.test(id) ||
            id.includes('react-router') ||
            id.includes('@mui') ||
            id.includes('@emotion')
          ) {
            return 'vendor';
          }
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    // `npm run test:coverage` writes coverage/lcov.info for SonarQube Cloud.
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      // BRD 12 COV-FR-04: only these are left out, each for the reason given.
      // Anything else needs the engineering lead's approval, recorded in the BRD.
      exclude: [
        'src/**/*.test.{ts,tsx}', // the tests themselves
        'src/test/**', // test set-up and shared test helpers
        'src/**/*.d.ts', // type declarations: no code runs
        'src/types/**', // type-only files: no code runs
        'src/main.tsx', // entry file: mounts the app into the page, run by the browser only
      ],
      // BRD 12 COV-FR-02, COV-FR-05: CI fails below these. They start just under
      // the measured level and are raised at the end of each phase, never lowered
      // without the product owner's approval.
      // Phase 1 (7 Nov 2026) measured lines 41.5%, statements 41.5%, branches
      // 40.5%, functions 36.4%. Phase 2 (7 Nov 2026) measured lines 53.3%,
      // statements 53.0%, branches 49.5%, functions 50.5%. Phase 3 (7 Nov 2026)
      // measured lines 69.7%, statements 68.9%, branches 64.1%, functions 64.3%.
      thresholds: {
        lines: 69,
        statements: 68,
        branches: 64,
        functions: 64,
      },
    },
  },
});
