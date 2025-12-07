import { createPinia } from 'pinia'
import persist from 'pinia-plugin-persistedstate'

// 先导出 store 模块
export * from './modules/user'
export * from './modules/device'
export * from './modules/devicestatus'
export * from './modules/message'
export * from './modules/history'
export * from './modules/aimessage'

// 然后创建和配置 pinia
const pinia = createPinia()
pinia.use(persist)

export default pinia
