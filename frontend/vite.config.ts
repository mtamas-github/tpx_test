import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const apiTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080';
const apiProxy = { target: apiTarget, xfwd: true };

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/shorten': apiProxy,
      '/urls': apiProxy,
      '^/[a-zA-Z0-9-]{2,64}$': apiProxy,
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
});
