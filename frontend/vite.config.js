import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { sentryVitePlugin } from '@sentry/vite-plugin'
import { fileURLToPath, URL } from 'node:url'

const sentryUploadEnabled = [
  process.env.SENTRY_AUTH_TOKEN,
  process.env.SENTRY_ORG,
  process.env.SENTRY_PROJECT,
].every((value) => Boolean(value?.trim()))
const sentryRelease = process.env.VITE_SENTRY_RELEASE || process.env.CI_COMMIT_SHA

export default defineConfig({
  plugins: [
    vue(),
    sentryUploadEnabled &&
      sentryVitePlugin({
        authToken: process.env.SENTRY_AUTH_TOKEN,
        org: process.env.SENTRY_ORG,
        project: process.env.SENTRY_PROJECT,
        release: {
          name: sentryRelease,
        },
        sourcemaps: {
          filesToDeleteAfterUpload: ['./dist/**/*.map'],
        },
        telemetry: false,
      }),
  ].filter(Boolean),
  build: {
    sourcemap: sentryUploadEnabled ? 'hidden' : false,
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  optimizeDeps: {
    // ffmpeg.wasm은 내부적으로 Web Worker(worker.js)를 자기 자신 기준 상대 경로로 띄우는데,
    // Vite dev 서버의 의존성 사전 번들링을 거치면 그 경로가 깨져 개발 모드에서만 로딩에 실패합니다.
    exclude: ['@ffmpeg/ffmpeg', '@ffmpeg/util'],
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:18080',
        ws: true,
      },
    },
  },
})
