import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import { login as loginApi, getUserInfo } from '@/api/user'

export const useAdminStore = defineStore('admin', () => {
  const adminInfo = ref(null)
  const token = ref(null)
  const permissions = ref([])
  const systemConfig = reactive({
    siteName: '绿植社区管理后台',
    theme: 'light'
  })

  const isLoggedIn = computed(() => !!token.value)
  const adminName = computed(() => adminInfo.value?.name || '管理员')
  const adminAvatar = computed(() => adminInfo.value?.avatar || '/default-avatar.png')
  const hasPermission = (permission) => permissions.value.includes(permission) || permissions.value.includes('*')

  const setToken = (val) => { token.value = val }
  const setAdminInfo = (info) => { adminInfo.value = info }
  const setPermissions = (list) => { permissions.value = Array.isArray(list) ? list : [] }
  const updateSystemConfig = (patch) => { Object.assign(systemConfig, patch) }
  const logout = () => { token.value = null; adminInfo.value = null; permissions.value = [] }

  // 登录
  const login = async ({ username, password, nonceStr, value }) => {
    const res = await loginApi({ username, password, nonceStr, value })
    if (res.data.code === 200) {
      setToken(res.data.data.token)
      // 获取基本信息
      getUserInfo(username).then(infoRes => {
        if (infoRes.data.code === 200) setAdminInfo(infoRes.data.data)
      })
      return { success: true }
    }
    return { success: false, message: res.data.message || '登录失败' }
  }

  // 单独刷新管理员信息
  const fetchAdminInfo = async (username) => {
    if (!username) return { success: false, message: '用户名为空' }
    const res = await getUserInfo(username)
    if (res.data.code === 200) { setAdminInfo(res.data.data); return { success: true } }
    return { success: false, message: res.data.message }
  }

  return {
    adminInfo, token, permissions, systemConfig,
    isLoggedIn, adminName, adminAvatar, hasPermission,
    setToken, setAdminInfo, setPermissions, updateSystemConfig, logout,
    login, fetchAdminInfo
  }
}, {
  persist: {
    enabled: true,
    strategies: [
      { key: 'sprout-admin', storage: localStorage, paths: ['token', 'adminInfo', 'permissions', 'systemConfig'] }
    ]
  }
})
