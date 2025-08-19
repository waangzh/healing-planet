import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAiMessageStore = defineStore(
  'Smart-green-plant',
    () => {
      const messages = ref([])
      
      // 重置消息列表
      const $reset = () => {
        messages.value = []
      }
      
      // 清空所有消息
      const clearMessages = () => {
        messages.value = []
      }
      
      // 添加新消息
      const addMessage = (message) => {
        messages.value.push(message)
      }
      
      // 加载消息 (从持久化存储读取)
      const loadMessages = () => {
        // 这里什么都不做，因为pinia-plugin-persistedstate会自动处理
        // 此方法仅用于显式指出加载操作
        return messages.value
      }

      return {
        messages,
        $reset,
        clearMessages,
        addMessage,
        loadMessages
      }
    },
    { persist: true }
)
