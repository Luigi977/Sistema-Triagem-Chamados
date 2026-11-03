import { defineConfig } from '@playwright/test'
import { randomUUID } from 'node:crypto'
process.env.FRONTEND_TEST_PASSWORD ??= randomUUID() + 'aA!'
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.live.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60000,
  use: {
    baseURL: 'http://127.0.0.1:15173',
    browserName: 'chromium',
    headless: true,
    viewport: { width: 1366, height: 900 },
    trace: 'off',
    screenshot: 'off',
  },
  webServer: [
    {
      command: 'node scripts/start-test-backend.mjs',
      url: 'http://127.0.0.1:18085/health',
      timeout: 120000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev -- --port 15173 --strictPort',
      url: 'http://127.0.0.1:15173',
      timeout: 60000,
      reuseExistingServer: false,
      env: {
        DEV_API_TARGET: 'http://127.0.0.1:18085',
        VITE_BACKEND_ORIGIN: 'http://127.0.0.1:18085',
        VITE_ENABLE_AI_DEMO: 'true',
      },
    },
  ],
})
