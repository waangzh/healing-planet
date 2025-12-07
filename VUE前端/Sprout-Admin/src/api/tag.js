import request from '@/utils/request'

// 获取标签列表
// 返回值{
//     "code": 200,
//     "data": [
//         {
//             "id": "1332650453377708034",
//             "name": "浇水指南"
//         },
//         {
//             "id": "1332681213568589825",
//             "name": "光照秘籍"
//         },
//         {
//             "id": "1332681213631504385",
//             "name": "施肥攻略"
//         }
//     ],
//     "message": "操作成功"
// }
export const getTagsList = () => request.get('/admin/tag/all?category=1')

// 新增标签
// data: {
//     "name":"测试22",
//     "category":1 // 类别固定为1
// }
export const addTag = (data) => request.post('/admin/tag/add', data)

// 更新标签
// data: {
//     "id":"1955453767537172482",
//     "name":"测试22333",
//     "category":1
// }
export const updateTag = (data) => request.put('/admin/tag/update', data)

// 删除标签
export const deleteTag = (ids) => request.delete(`/admin/tag/delete?ids=${ids.join(',')}`)