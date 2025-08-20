import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore(
  'Termite-recognition-user',
  () => {
    const token = ref('')
    const setToken = (newToken) => {
      token.value = newToken
    }
    const removeToken = () => {
      token.value = ''
    }
    const user = ref({})
    
    const setUser = (obj) => {
      user.value = obj
    }

    // reset
    const $reset = () => {
      token.value = ''
      user.value = {}
    }

    return {
      token,
      setToken,
      removeToken,
      user,
      setUser,
      $reset
    }
  },
  { persist: true }
)
