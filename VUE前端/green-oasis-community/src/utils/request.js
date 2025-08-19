import axios from 'axios'
import { useUserStore } from '@/stores'
// import { ElMessage } from 'element-plus'
// import { useRouter } from 'vue-router'
// const baseURL = 'http://localhost:9000'
// const baseURL = 'http://localhost:8080'
// const baseURL = 'http://120.26.231.14:8000'
// const baseURL = 'https://eb15-117-150-6-107.ngrok-free.app'
// const baseURL = 'http://localhost:12345'
// const router =useRouter()

// 使用环境变量或者默认值
const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://120.26.231.14:8000'

const instance = axios.create({
  baseURL,
  timeout: 60000
})

// 请求拦截器
instance.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = userStore.token
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
