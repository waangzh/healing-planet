import request from '@/utils/request';

// 上传图片
export const userUploadImageService = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  
  return request.post('/common/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 获取登录验证图片
export const getCodeImg = (data) => {
  return request.post('/common/getCaptcha', data);
};