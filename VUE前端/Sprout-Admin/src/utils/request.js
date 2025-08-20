import axios from 'axios'
import { useAdminStore } from '@/stores/admin'

// 使用环境变量或者默认值
// 开发环境建议通过 Vite 代理 (/api) 转发，避免浏览器直接跨域；生产可配置真实域名
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const instance = axios.create({
  baseURL,
  timeout: 60000
})

// 请求拦截器
instance.interceptors.request.use(
  (config) => {
    const adminStore = useAdminStore()
    if (adminStore.token) {
      config.headers.Authorization = adminStore.token
      config.headers['ngrok-skip-browser-warning'] = '69420'
    }
    return config
  },
  (err) => Promise.reject(err)
)

//响应拦截器
// instance.interceptors.response.use(
//   (res) => {
//     if (res.data.code === 1) {
//       return res
//     }
//   },
//   (err) => {
//     if (err.response) {
//       // 处理 HTTP 错误
//       if (err.response.status === 401) {
//         router.push('/login');
//       }
//       const errorMessage = err.response?.data?.message || '服务异常';
//       ElMessage.error(errorMessage);
//       return Promise.reject(err.response.data || { message: '服务异常' });
//     } else if (err.request) {
//       // 处理请求未发送成功的错误
//       ElMessage.error('请求未发送成功');
//       return Promise.reject({ message: '请求未发送成功' });
//     } else {
//       // 处理其他错误
//       ElMessage.error(`调用失败: ${err.message}`);
//       return Promise.reject({ message: `调用失败: ${err.message}` });
//     }
//   }
// );

export default instance
export { baseURL }
