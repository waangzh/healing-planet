import request from '@/utils/request'

// 上传文件
export const uploadFileService = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  
  return request.post('/common/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 流式请求接口 - 使用fetch代替axios
export const aiMessageStreamService = ({ id, userMessage }) => {
  const url = 'http://120.26.231.14:8000/common/chat/stream';
  const requestData = {
    id,
    userMessage
  };
  
  return fetch(url, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`,
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    },
    body: JSON.stringify(requestData)
  }).then(response => {
    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`);
    }
    return response;
  });
}

// 获取登录验证图片
export const getCodeImg = (data) => {
  return request.post('/common/getCaptcha', data);
};
