import request from '@/utils/request'

// 登录
export const login = (username, password, nonceStr, value) => {
  const params = { username, password };
  
  // 如果有验证数据，添加到参数中
  if (nonceStr && value !== undefined) {
    params.nonceStr = nonceStr;
    params.value = value;
  }
  
  return request.post('/ums/user/login', params);
}

// 注册
export const register = (data) => request.post('/ums/user/register', data)

// 获取用户信息
export const getUserInfo = (userName) => request.get('/ums/user/info',{params:{userName}})

// 退出登录
export const logout = (userName) => request.get('/ums/user/logout',{params:{userName}})

// 获取用户话题
export const getUserTopics = ({username, pageNo, size}) => request.get(`/ums/user/${username}?pageNo=${pageNo}&size=${size}`)

// 更新用户信息
export const updateUserInfo = (data) => request.post('/ums/user/update', data)

