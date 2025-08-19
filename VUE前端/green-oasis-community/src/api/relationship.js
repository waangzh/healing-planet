import request from '@/utils/request'

// 关注用户
export const followUser = (userId) => request.get(`/relationship/subscribe/${userId}`)

// 取消关注
export const unfollowUser = (userId) => request.get(`/relationship/unsubscribe/${userId}`)

// 验证是否关注
export const checkFollow = (topicUserId) => request.get(`/relationship/validate/${topicUserId}`)

// 获取粉丝列表
export const getFollowers = (username) => request.get(`/relationship/fans?username=${username}`)

// 获取关注列表
export const getFollowing = (username) => request.get(`/relationship/followers?username=${username}`)