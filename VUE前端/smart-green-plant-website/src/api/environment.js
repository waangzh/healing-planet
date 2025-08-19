import request from '@/utils/request'

// 获取自定义天数的环境数据
export const fetchEnvironmentData = (plantInstanceId, days) => {
  return request.get(`/getSevenDayData?plantInstanceId=${plantInstanceId}&days=${days}`)
}

// 分页查询历史环境数据
export const fetchHistoryData = (data) => {
  return request.post('/history-data', data)
}

// 导出环境数据
export const exportEnvironmentData = (data) => {
  return request.post('/export', {
    id: data.id,
    plantId: data.plantId,
    potNumber: data.potNumber
  })
}

// 根据历史数据分析植物健康状况
export const analyzePlantHealth = (data) => {
  return request.post('/analysis', data)
}
