import request from '@/utils/request'

// 文章文章列表
export const getPostList = (params) => request.get('/post/list', {params});

// 文章详情
export const getPostDetail = (id) => request.get(`/post?id=${id}`)

// 获取文章评论
export const getPostComment = (topicId) => request.get(`/comment/get_comments?topicId=${topicId}`)

// 发表评论
export const addPostComment = (data) => request.post('/comment/add_comment', data)

// 更新帖子
export const updatePost = (data) => request.post('/post/update', data)

// 删除帖子
export const deletePost = (id) => request.delete(`/post/delete?id=${id}`)

// 获取推荐文章
export const getRecommendPost = (topicId) => request.get(`/post/recommend?topicId=${topicId}`)

// 发表帖子
export const addPost = (data) => request.post('/post/create', data)

// 点赞/取消点赞
export const likePost = (topicId) => request.post(`/like/post/${topicId}`)

// 验证是否点赞
export const checkLike = (topicId) => request.get(`/like/validate?topicId=${topicId}`)

// 收藏/取消收藏
export const collectPost = (data) => request.post(`/collect`, data)

// 验证是否收藏
export const checkCollect = (topicId) => request.get(`/collect/validate?topicId=${topicId}`)

// 记录文章浏览日志
export const recordPostView = (id) => request.get(`/post/postLog?id=${id}`)
