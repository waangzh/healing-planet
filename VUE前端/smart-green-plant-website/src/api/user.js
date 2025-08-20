import request from '@/utils/request'

export const userRegisterService = ({ username, password, email }) =>
  request.post('/user/register', { username, password, email })

export const userLoginService = ({ username, password, nonceStr, value }) =>
  request.post('/user/login', { username, password, nonceStr, value })

// 修改用户信息
export const userUpdateInfoService = ({ id, nickName, avatar, email, phone, diyBk }) =>
  request.put('/user/update', {
    id,
    nickName,
    avatar,
    email,
    phone,
    diyBk
  })

// 修改密码
export const userUpdatePasswordService = ({ id, password }) =>
  request.put('/user', {
    id,
    password
  })


