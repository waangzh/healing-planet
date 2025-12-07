import request from '@/utils/request'

// 获取消息列表
export const getMessageList = () => {
  return request.get('/messages')
}

// 获取未读消息数量
export const getUnreadCount = () => {
  return request.get('/messages/unreadNum')
}

// 标记消息为已读
export const readMessage = (messageId) => {
  return request.put(`/messages/${messageId}/read`)
}

