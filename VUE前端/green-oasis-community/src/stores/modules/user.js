import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserInfo } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
  const token = ref(localStorage.getItem('token') || '')

  function setUser(newUser) {
    user.value = newUser
    localStorage.setItem('user', JSON.stringify(newUser))
  }

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function resetUser() {
    user.value = {}
    token.value = ''
    localStorage.removeItem('user')
    localStorage.removeItem('token')
  }

  // 刷新用户信息
  async function refreshUserInfo() {
    if (!user.value?.username) return false
    
    try {
      const res = await getUserInfo(user.value.username)
      if (res.data.code === 200) {
        setUser(res.data.data)
        // 触发全局用户信息更新事件
        window.dispatchEvent(new CustomEvent('user-info-updated'))
        return true
      }
    } catch (error) {
      console.error('刷新用户信息失败:', error)
    }
    return false
  }

  // 增加帖子数（本地计数，用于即时反馈）
  function incrementPostCount() {
    if (user.value.postCount !== undefined) {
      user.value.postCount += 1
      localStorage.setItem('user', JSON.stringify(user.value))
      // 触发更新事件
      window.dispatchEvent(new CustomEvent('user-info-updated'))
    }
  }

  return { 
    user, 
    token, 
    setUser, 
    setToken, 
    resetUser,
    refreshUserInfo,
    incrementPostCount
  }
}, {
  persist: {
    key: 'user-store',
    storage: localStorage,
    paths: ['user', 'token']
  }
})

