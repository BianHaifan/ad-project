import {loadEnv} from 'vite';
import {defineConfig} from 'vitest/config';
import react from '@vitejs/plugin-react';
export default defineConfig(({mode}) => {
  const env = loadEnv(mode, '.', '');
  return {
    plugins: [react()],
    server: {
      port: 4173,
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html'],
        exclude: [
          'src/**/__tests__/**',
          'src/**/*.test.*',
          'src/**/*.spec.*',
          'src/mocks/**',
          'src/**/index.ts',
        ],
      },
    },
  };
});
