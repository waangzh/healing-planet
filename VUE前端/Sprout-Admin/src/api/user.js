import request from '@/utils/request'

// 管理员登录
// data: {
//   "username": "admin",
//   "password": "123456"
// }
// 返回值{
//     "code": 200,
//     "data": {
//         "token": "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VyTmFtZSI6ImFkbWluIiwiZXhwIjoxNzU4NTg0MjEwfQ.LJRsUxd36w3_pT_6Zh5PVu5CFOSExSVBgiA7-_26Lr1zhxY5nFDUk_OHYPBC2oWiaNrygLKrJxu1WCKGj7VB-Q"
//     },
//     "message": "登录成功"
// }
export const login = (data) => request.post('/admin/login', data)

// 根据用户名获取用户信息
// 返回值{
//     "code": 200,
//     "data": {
//         "id": "1915043541686710273",
//         "username": "zmjkk",
//         "alias": "zmjkk",
//         "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/659517f8-7ef2-4af3-b945-24a70e42986a.png",
//         "email": "33155@qq.com",
//         "mobile": null,
//         "bio": "自由职业者",
//         "score": 34,
//         "active": true,
//         "status": true,
//         "createTime": "2025-04-23T14:02:15.000+00:00",
//         "modifyTime": "2025-05-02T11:40:43.000+00:00",
//         "message": null,
//         "postCount": 17,
//         "followerCount": 3,
//         "followingCount": 12,
//         "isPurchased": true
//     },
//     "message": "操作成功"
// }
export const getUserInfo = (username) => request.get(`/admin/info?userName=${username}`)

// 分页查询用户列表
// data: {
//     "username":"",
//     "alias":"", // 昵称
//     "status":true, // 是否激活
//     "postCount":0, // 最小发帖数,可以不传,默认值为0
//     "followingCount":0, // 最小关注数,可以不传,默认值为0
//     "followerCount":3, // 最小粉丝数,可以不传,默认值为0
//     "pageNo":1, // 当前页码,默认值为1
//     "pageSize":10 // 每页显示数量,默认值为10
// }
// // 返回值{
//     "code": 200,
//     "data": {
//         "records": [
//             {
//                 "id": "1349290158897311745",
//                 "username": "admin",
//                 "alias": "admin",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/7fdbd99d-9901-47f3-b7e1-f980e49eb278.png",
//                 "email": "23456@qq.com",
//                 "mobile": null,
//                 "bio": "自由职业者",
//                 "score": 3,
//                 "active": true,
//                 "status": true,
//                 "createTime": "2021-01-13T09:40:17.000+00:00",
//                 "modifyTime": "2025-05-02T12:08:58.000+00:00",
//                 "message": null,
//                 "postCount": 2,
//                 "followerCount": 3,
//                 "followingCount": 2,
//                 "roleId": 1, 1为管理员,0为用户
//                 "isPurchased": false
//             },
//             {
//                 "id": "1349618748226658305",
//                 "username": "zhangsan",
//                 "alias": "zhangsan",
//                 "avatar": "https://btpomf.oss-cn-beijing.aliyuncs.com/42e5c94b-ed15-4bf1-b09c-b30e6c51a3fc.jpg",
//                 "email": "23456@qq.com",
//                 "mobile": null,
//                 "bio": "自由职业者",
//                 "score": 0,
//                 "active": true,
//                 "status": true,
//                 "createTime": "2021-01-14T07:25:59.000+00:00",
//                 "modifyTime": null,
//                 "message": "外面的声音太嘈杂了，听自己的心就好了。",
//                 "postCount": 0,
//                 "followerCount": 3,
//                 "followingCount": 1,
//                 "roleId": 0,
//                 "isPurchased": false
//             }
//         ],
//         "total": 5,
//         "size": 10,
//         "current": 1,
//         "orders": [],
//         "optimizeCountSql": true,
//         "hitCount": false,
//         "countId": null,
//         "maxLimit": null,
//         "searchCount": true,
//         "pages": 1
//     },
//     "message": "操作成功"
// }
export const getUserList = (data) => request.post('/admin/list', data)

// 更新用户信息
// data: {
//     "username":"www",
//     "alias": "wwx",
//     "avatar": "https://s3.ax1x.com/2020/12/01/DfHNo4.jpg",
//     "email": "3073561696@qq.com",
//     "mobile": "123456",
//     "bio": "自由职业者",
//     "message":"123456"
// }
export const updateUser = (data) => request.put('/admin/update', data)

// 新增用户
// data: {
//     "username":"test",
//     "password":"123456",
//     "checkPass":"123456",
//     "email":"307sa3666@qq.com"
// }
// 返回值
export const createUser = (data) => request.post('/admin/add', data)

// 批量删除用户
export const deleteUsers = (ids) => request.delete(`/admin/delete?ids=${ids.join(',')}`)
