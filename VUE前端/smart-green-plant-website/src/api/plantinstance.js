import request from '@/utils/request'

// 根据用户id查询种植的信息
export const getPlantInstanceId = (userId) => {
  return request.get(`/plantinstance/${userId}`)
}

// 获取养护建议
export const getPlantAdvice = (data) => {
  return request.post('/plantinstance/getAdvice', data)
}

// 添加新植物
export const addPlantInstance = (data) => {
  return request.post('/plantinstance/add', data)
}

// 删除植物
export const deletePlantInstance = (id) => {
  return request.delete(`/plantinstance/${id}`)
}

// 更新植物信息
export const updatePlantInstance = (data) => {
  return request.post('/plantinstance/update', data)
}
