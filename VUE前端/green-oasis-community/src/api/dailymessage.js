import request from '@/utils/request'

// 获取每日一句
export const getDailyMessage = () => {
  return request.get('/tip/today')
}



