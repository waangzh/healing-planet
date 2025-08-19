import request from '@/utils/request'
// 获取标签列表
// category: 1 文章 0 绿植
export const getTagList = (category) => request.get('/tag/all', {params: {category}})


