import { createPinia } from 'pinia'
import persist from 'pinia-plugin-persistedstate'

// 先导出 store 模块
export * from './modules/user'
export * from './modules/aiMessage'

// 然后创建和配置 pinia
const pinia = createPinia()
pinia.use(persist)

export default pinia