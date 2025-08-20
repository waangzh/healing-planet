import request from '@/utils/request'

export const aiMessageService = (id, userMessage) => {
  return request.post(`/common/chat?id=${id}`, userMessage, {
    headers: {
      'Content-Type': 'multipart/form-data',
    }
  })
}

// 流式请求接口 - 使用fetch代替axios
export const aiMessageStreamService = ({ id, userMessage }) => {
  const url = 'http://localhost:9000/common/chat/stream';
  const requestData = {
    id,
    userMessage
  };
  
  return fetch(url, {
    method: 'POST',
    headers: {
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
