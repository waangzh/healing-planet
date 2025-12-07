import request from '@/utils/request'

// 病理检测
export const checkDisease = (deviceId, image) => {
  const formData = new FormData()
  formData.append('detectInfoDTO', JSON.stringify({ deviceId }))
  formData.append('image', image)

  return request({
    url: '/model/detection',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

