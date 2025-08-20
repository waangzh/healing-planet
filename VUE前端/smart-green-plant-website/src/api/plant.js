// 植物种类接口
import request from '@/utils/request'

// 获取植物种类
export const getPlants = (userId) => {
  return request.get(`/plant/${userId}`)
}

// 新增植物种类
export const addPlant = (data) => {
  return request.post('/plant/add', data)
}

// 批量删除植物种类
export const deletePlant = (ids) => {
  return request.delete(`/plant/${ids}`)
}

// 更新植物种类
export const updatePlant = (data) => {
  return request.put('/plant', data)
}

// 分页查询植物种类
export const getPlantPage = ({ pageNum, pageSize, search }) => {
  return request.get(`/plant/findPage?pageNum=${pageNum}&pageSize=${pageSize}&search=${search}`)
}
