import request from '@/utils/request'

// 检索话题
export const searchTopic = (params) => request.get(`/search`,{params})
