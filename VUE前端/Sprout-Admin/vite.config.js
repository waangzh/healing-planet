import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  // 本地开发服务器配置：通过代理解决后端未开启 CORS 导致的跨域问题
  server: {
    proxy: {
      // 以 /api 作为前缀转发到真实后端，避免浏览器跨域
      '/api': {
        // target: 'http://120.26.231.14:8000',
        target: 'http://localhost:8000',
        changeOrigin: true,
        // 去掉前缀 /api，再转发给后端
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
