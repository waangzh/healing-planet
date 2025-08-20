// 注意: res.data才是这里的返回值
import request from '@/utils/request'

// 获取所有公告
// 返回值{
//     "code": 200,
//     "data": [
//         {
//             "id": 2,
//             "content": "R1.0 开始已实现护眼模式 ,妈妈再也不用担心我的眼睛了。",
//             "modifyTime": "2020-11-19T09:16:19.000+00:00",
//             "show": false
//         },
//         {
//             "id": 4,
//             "content": "系统已更新至最新版1.0.1",
//             "modifyTime": "2025-08-11T08:27:19.000+00:00",
//             "show": true
//         }
//     ],
//     "message": "操作成功"
// }
export const getAllBillboards = () => {
  return request.get('/billboard/admin/all')
}

// 根据 ID 获取公告
// 返回值{
//     "code": 200,
//     "data": {
//         "id": 2,
//         "content": "R1.0 开始已实现护眼模式 ,妈妈再也不用担心我的眼睛了。",
//         "modifyTime": "2020-11-19T09:16:19.000+00:00",
//         "show": false
//     },
//     "message": "操作成功"
// }
export const getBillboardById = (id) => {
  return request.get(`/billboard/admin/?id=${id}`)
}

// 修改公告
// data: {
//     "id":2,
//     "content":"测试",
//     "modifyTime":"2020-11-19T17:16:19",
//     "show":true
// }
// 返回值{
//     "code": 200,
//     "data": null,
//     "message": "操作成功"
// }
export const updateBillboard = (data) => {
  return request.put(`/billboard/admin/update`, data)
}

// 删除公告
// 返回值{
//     "code": 200,
//     "data": null,
//     "message": "操作成功"
// }
// admin/billboard/delete?ids=1,2
export const deleteBillboard = (ids) => {
  return request.delete(`/billboard/admin/delete?ids=${ids.join(',')}`)
}

// 新增公告
// data: {
//     "content":"测试",
//     "show":true
// }
// 返回值{
//     "code": 200,
//     "data": null,
//     "message": "操作成功"
// }
export const addBillboard = (data) => {
  return request.post(`/billboard/admin/add`, data)
}