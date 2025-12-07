import request from '@/utils/request'

// 根据用户id查询设备
export const getDeviceByUserId = (userId) => {
  return request.get(`/device/getDevice?userId=${userId}`)
}

//获取设备名称
export const getDeviceName = (plantId) => {
  return request.get(`/device/getDeviceName/${plantId}`)
}

// 获取设备控制信息
export const fetchDeviceControl = (deviceName) => {
  return request.get(`/device/found/${deviceName}`)
}

// 设置设备状态
// export const setDeviceControl = (data) => {
//   return request.put('/device/set', data)
// }

// 设置设备开关
export const setDeviceSwitch = (data) => {
  return request.put('/device/setSwitch', data)
}

// 设置设备阈值
export const setDeviceThreshold = (data) => {
  return request.put('/device/setThreshold', data, {
    headers: {
      'Content-Type': 'application/json'
    }
  })
}

// 获取设备阈值
export const getDeviceThreshold = (deviceId) => {
  return request.get(`/device/getThreshold?deviceId=${deviceId}`)
}

// 设置自动控制
export const setAutoControl = (data) => {
  return request.post('/device/setAuto', data)
}

// 获取设备状态
export const getDeviceStatus = (deviceName) => {
  return request.get(`/device/status/${deviceName}`)
}

// 设置设备预警
export const setDeviceWarning = (data) => {
  return request.post('/device/warning', data)
}