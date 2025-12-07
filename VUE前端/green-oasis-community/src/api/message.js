import request from '@/utils/request'

// 获取消息通知
export const getMessageNotifications = () => {
    return request.get('/notification')
}

// 标记消息已读
export const markMessageAsRead = (messageId) => {
    return request.put(`/notification/${messageId}`)
}

// 获取未读消息数量
export const getUnreadMessageCount = () => {
    return request.get('/notification/getUnreadCount')
}

// 删除消息
export const deleteMessage = (ids) => {
    return request.delete(`/notification/delete?ids=${ids.join(',')}`)
}
