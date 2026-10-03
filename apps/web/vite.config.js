import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

const demoReadOnly = process.env.LULU_PUBLIC_DEMO === 'true'

const publicDemoGuard = {
  name: 'lulu-public-demo-guard',
  configureServer(server) {
    if (!demoReadOnly) return
    server.middlewares.use((req, res, next) => {
      const requestPath = (req.url || '').split('?')[0]
      if (!requestPath.startsWith('/api/')) return next()
      const allowedReads = new Set([
        '/api/health',
        '/api/ai/system/status',
        '/api/ai/system/models'
      ])
      if (req.method === 'GET' && allowedReads.has(requestPath)) return next()
      res.statusCode = 403
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ error: 'public_demo_read_only' }))
    })
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), publicDemoGuard],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    cors: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8123',
        changeOrigin: true
      }
    }
  }
})
