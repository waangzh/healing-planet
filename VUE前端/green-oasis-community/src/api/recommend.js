import request from '@/utils/request';
// 获取推荐文章
export const getArticleRecommend = (pageNo = 1, size = 8) => request.get('/recommend/posts', {
  params: { pageNo, size }
});

// 获取推荐用户
export const getUserRecommend = (pageNo = 1, size = 5) => request.get('/recommend/users', {
  params: { pageNo, size }
});

// 获取生成日志
export const getGenerateLog = (message="") => request.post('/writePost', { message });
