<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import avatar from '@/assets/img/用户.svg';
import DailyMessage from '@/components/common/DailyMessage.vue';
import { getUserRecommend, getArticleRecommend } from '@/api/recommend';
import { followUser, unfollowUser, checkFollow } from '@/api/relationship';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';

const router = useRouter();
const suggestedUsers = ref([]);
const currentPage = ref(1);
const pageSize = ref(5);
const loading = ref(false);

// 推荐文章相关
const recommendPosts = ref([]);
const recommendPage = ref(1);
const recommendSize = ref(8); // 一次获取8篇
const recommendLoading = ref(false);

// 添加节流控制
const throttleTimers = ref({
  recommend: null,
  users: null
});

// 获取推荐用户
const fetchSuggestedUsers = async () => {
  try {
    loading.value = true;
    const res = await getUserRecommend(currentPage.value, pageSize.value);
    if (res.data && res.data.code === 200) {
      // 先获取用户列表
      const users = res.data.data;
      
      // 创建临时数组存储用户数据
      const tempUsers = [];
      
      // 依次检查每个用户的关注状态
      for (const user of users) {
        try {
          const followRes = await checkFollow(user.id);
          const isFollowing = followRes.data && followRes.data.code === 200 && followRes.data.data.hasFollow;
          
          // 添加用户和关注状态到临时数组
          tempUsers.push({
            ...user,
            following: isFollowing
          });
        } catch (error) {
          console.error(`获取用户${user.id}关注状态失败:`, error);
          // 如果检查失败，假定未关注
          tempUsers.push({
            ...user,
            following: false
          });
        }
      }
      
      // 更新状态
      suggestedUsers.value = tempUsers;
    }
  } catch (error) {
    console.error('获取推荐用户失败:', error);
  } finally {
    loading.value = false;
  }
};

// 获取推荐文章
const fetchRecommendPosts = async () => {
  try {
    recommendLoading.value = true;
    const res = await getArticleRecommend(recommendPage.value, recommendSize.value);
    if (res.data && res.data.code === 200) {
      recommendPosts.value = res.data.data;
    }
  } catch (error) {
    console.error('获取推荐文章失败:', error);
  } finally {
    setTimeout(() => {
      recommendLoading.value = false;
    }, 1000);
  }
};

// 换一批推荐文章
const loadNextRecommend = () => {
  // 如果正在加载或节流定时器存在，直接返回
  if (recommendLoading.value || throttleTimers.value.recommend) return;
  
  recommendPage.value++;
  fetchRecommendPosts();
  
  // 设置节流定时器，1秒内不能再次触发
  // throttleTimers.value.recommend = setTimeout(() => {
  //   throttleTimers.value.recommend = null;
  // }, 1000);
};

// 换一批
const loadNextBatch = () => {
  // 如果正在加载或节流定时器存在，直接返回
  if (loading.value || throttleTimers.value.users) return;
  
  currentPage.value++;
  fetchSuggestedUsers();
  
  // 设置节流定时器，1秒内不能再次触发
  throttleTimers.value.users = setTimeout(() => {
    throttleTimers.value.users = null;
  }, 1000);
};

// 关注或取消关注用户
const handleFollow = async (user, index) => {
  try {
    let res;
    
    if (user.following) {
      // 如果已关注，则取消关注
      res = await unfollowUser(user.id);
    } else {
      // 如果未关注，则关注
      res = await followUser(user.id);
    }
    
    if (res.data && res.data.code === 200) {
      // 切换关注状态
      suggestedUsers.value[index].following = !suggestedUsers.value[index].following;
      
      // 显示成功消息
      ElMessage.success(res.data.message || (user.following ? '取消关注成功' : '关注成功'));
    }
  } catch (error) {
    console.error(user.following ? '取消关注失败:' : '关注用户失败:', error);
    ElMessage.error(user.following ? '取消关注失败，请重试' : '关注失败，请重试');
  }
};

// 跳转到用户页面
const goToUserProfile = (user) => {
  router.push(`/user/${user.username}`);
};

// 跳转到文章详情
const goToPostDetail = (postId) => {
  router.push(`/post/${postId}`);
};

// 初始化
onMounted(() => {
  fetchSuggestedUsers();
  fetchRecommendPosts();
});

// 组件销毁时清除所有定时器
onUnmounted(() => {
  if (throttleTimers.value.recommend) {
    clearTimeout(throttleTimers.value.recommend);
  }
  if (throttleTimers.value.users) {
    clearTimeout(throttleTimers.value.users);
  }
});
</script>

<template>
  <aside class="right-sidebar">
    
    <div class="sidebar-card">
      <h3 class="sidebar-title">推荐关注</h3>
      
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
      
      <div v-else class="user-suggestion-list">
        <div 
          class="user-suggestion-item" 
          v-for="(user, index) in suggestedUsers" 
          :key="user.id"
        >
          <div class="user-avatar" @click="goToUserProfile(user)">
            <img :src="user.avatar || avatar" :alt="user.username">
          </div>
          <div class="user-info" @click="goToUserProfile(user)">
            <div class="user-name">{{ user.username }}</div>
            <div class="user-role">{{ user.bio || '暂无简介' }}</div>
          </div>
          <div class="follow-btn">
            <button 
              :class="{ 'following': user.following }"
              @click="handleFollow(user, index)"
            >
              <template v-if="user.following">
                <span class="follow-text">已关注</span>
                <span class="unfollow-text">取消关注</span>
              </template>
              <template v-else>
                <i class="fas fa-plus"></i>
              </template>
            </button>
          </div>
        </div>
      </div>
      
      <div class="view-more">
        <a href="javascript:void(0);" @click="loadNextBatch">换一批</a>
      </div>
    </div>
    
    
    <DailyMessage />
    
    <!-- 推荐文章 -->
    <div class="sidebar-card">
      <div class="recommend-header">
        <h3 class="sidebar-title">
          <span class="emoji">🧐</span> 猜你想看
        </h3>
        <span class="refresh-btn" @click="loadNextRecommend" :class="{ 'loading': recommendLoading }">
          <i class="fas fa-sync-alt"></i> 换一批
        </span>
      </div>

      <div v-if="recommendLoading" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
      
      <div v-else-if="recommendPosts.length === 0" class="no-recommend">
        暂无推荐文章
      </div>
      
      <div v-else class="recommend-list">
        <div 
          v-for="(post, index) in recommendPosts" 
          :key="post.id" 
          class="recommend-item"
          @click="goToPostDetail(post.id)"
        >
          <span class="post-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="recommend-post-title" :title="post.title">
            {{ post.title }}
          </span>
        </div>
      </div>
    </div>
    <!-- <div class="sidebar-card">
      <h3 class="sidebar-title">今日新闻</h3>
      
      <div class="news-list">
        <div class="news-item" v-for="item in news" :key="item.id">
          <div class="news-title">{{ item.title }}</div>
          <div class="news-time">{{ item.timeAgo }}</div>
        </div>
      </div>
    </div> -->
  </aside>
</template>

<style scoped>
.right-sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.sidebar-card {
  background-color: var(--el-bg-color-page);
  border-radius: 10px;
  box-shadow: 0 2px 8px var(--el-box-shadow);
  overflow: hidden;
  padding: 15px;
}

.sidebar-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 15px;
  color: var(--el-text-color-primary);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  color: var(--el-text-color-secondary);
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--el-border-color-lighter);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: spinner 0.8s linear infinite;
  margin-bottom: 8px;
}

@keyframes spinner {
  to {transform: rotate(360deg);}
}

.user-suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.user-suggestion-item {
  display: flex;
  align-items: center;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 10px;
  cursor: pointer;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.user-name {
  font-weight: 500;
  font-size: 14px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.follow-btn button {
  border-radius: 15px;
  background-color: var(--el-color-primary);
  color: white;
  border: none;
  font-size: 14px;
  padding: 3px 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 32px;
  height: 28px;
  position: relative;
  overflow: hidden;
}

.follow-btn button:hover {
  background-color: var(--el-color-primary-light-3);
}

.follow-btn button.following {
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  padding: 3px 10px;
  min-width: 65px;
}

.follow-btn button.following .follow-text,
.follow-btn button.following .unfollow-text {
  transition: opacity 0.2s, transform 0.2s;
  position: absolute;
  width: 100%;
  left: 0;
}

.follow-btn button.following .follow-text {
  opacity: 1;
  transform: translateY(0);
}

.follow-btn button.following .unfollow-text {
  opacity: 0;
  transform: translateY(20px);
  color: var(--el-color-danger);
}

.follow-btn button.following:hover {
  background-color: var(--el-color-danger-light-8);
}

.follow-btn button.following:hover .follow-text {
  opacity: 0;
  transform: translateY(-20px);
}

.follow-btn button.following:hover .unfollow-text {
  opacity: 1;
  transform: translateY(0);
}

.view-more {
  text-align: center;
  margin-top: 15px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-light);
}

.view-more a {
  color: var(--el-color-primary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s;
}

.view-more a:hover {
  color: var(--el-color-primary-light-3);
  text-decoration: underline;
}

/* 推荐文章样式 */
.recommend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.emoji {
  font-size: 18px;
  margin-right: 5px;
}

.refresh-btn {
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  gap: 5px;
}

.refresh-btn:hover {
  color: var(--el-color-primary-light-3);
  transform: translateY(-1px);
}

.refresh-btn.loading i {
  animation: spinner 0.8s linear infinite;
}

.no-recommend {
  text-align: center;
  padding: 20px 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recommend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  transition: background-color 0.3s;
  cursor: pointer;
}

.recommend-item:hover {
  background-color: var(--el-color-primary-light-9);
}

.post-index {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  min-width: 22px;
  text-align: center;
}

.recommend-post-title {
  font-size: 14px;
  color: var(--el-text-color-primary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* .news-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.news-item {
  cursor: pointer;
}

.news-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 5px;
}

.news-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
} */
</style> 