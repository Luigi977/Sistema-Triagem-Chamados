import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.DEV_API_TARGET || 'http://localhost:8085'
  return {
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      proxy: { '/api': { target, changeOrigin: true }, '/health': { target, changeOrigin: true } },
    },
  }
})
