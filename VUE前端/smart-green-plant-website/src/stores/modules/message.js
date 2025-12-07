import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMessageList, getUnreadCount, readMessage } from '@/api/message'

export const useMessageStore = defineStore(
  'message',
  () => {
    const messages = ref([])
    const unreadCount = ref(0)

    // 获取消息列表
    const fetchMessages = async () => {
      try {
        const res = await getMessageList()
        if (res.data.code === '1') {
          messages.value = res.data.data
        }
      } catch (error) {
        console.error('获取消息列表失败:', error)
      }
    }

    // 获取未读消息数量
    const fetchUnreadCount = async () => {
      try {
        const res = await getUnreadCount()
        if (res.data.code === '1') {
          unreadCount.value = res.data.data
        }
      } catch (error) {
        console.error('获取未读消息数量失败:', error)
      }
    }

    // 标记消息为已读
    const markAsRead = async (messageId) => {
      try {
        const res = await readMessage(messageId)
        if (res.data.code === '1') {
          // 更新本地消息状态
          const message = messages.value.find(msg => msg.id === messageId)
          if (message) {
            message.isRead = true
          }
          // 更新未读数量
          unreadCount.value = Math.max(0, unreadCount.value - 1)
        }
      } catch (error) {
        console.error('标记消息已读失败:', error)
      }
    }

    // 手动刷新消息
    const refreshMessages = async () => {
      try {
        await Promise.all([
          fetchMessages(),
          fetchUnreadCount()
        ])
      } catch (error) {
        console.error('刷新消息失败:', error)
      }
    }


    // reset
    const $reset = () => {
      messages.value = []
      unreadCount.value = 0
    }
    
    return {
      messages,
      unreadCount,
      fetchMessages,
      fetchUnreadCount,
      markAsRead,
      refreshMessages,
      $reset
    }
  },
  {
    persist: true
  }
) 