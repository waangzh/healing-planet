import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  // 设置基础路径为根路径
  base: '/', // 绝对路径，避免深链接时资源404
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    // 生产环境配置
    assetsDir: 'assets', // 指定静态资源存放目录
  },
  server: {
    proxy: {
      // 以 /api 作为前缀转发到真实后端，避免浏览器跨域
      '/api': {
        // target: 'http://47.121.27.48:8000',
        target: 'http://localhost:8000',
        changeOrigin: true,
        // 去掉前缀 /api，再转发给后端
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/ai-api': {
        target: 'http://localhost:8010',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/ai-api/, '')
      }
    }
  }
})
