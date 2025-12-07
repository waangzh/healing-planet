<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { getPostList, likePost, collectPost, checkLike, checkCollect } from '@/api/post';
import { getTagList } from '@/api/tag';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores';

const postList = ref([]);
const activeTab = ref('latest');
const tagList = ref([]);
const total = ref(0);
const loading = ref(false);
const finished = ref(false);
const currentPage = ref(1);

const params = ref({
  tab: 'latest',
  pageNo: 1,
  size: 5,
});

const router = useRouter();
const route = useRouter().currentRoute.value;

const userStore = useUserStore();

// 帖子排序函数
const sortPosts = (posts) => {
  return posts.sort((a, b) => {
    // 如果都是置顶帖，按精华排序
    if (a.top && b.top) {
      return b.essence - a.essence;
    }
    // 置顶帖排在前面
    if (a.top || b.top) {
      return b.top - a.top;
    }
    // 如果都不是置顶帖，按精华排序
    if (a.essence || b.essence) {
      return b.essence - a.essence;
    }
    // 都不是置顶和精华的按创建时间倒序
    return new Date(b.createTime) - new Date(a.createTime);
  });
};

// 获取标签列表
const fetchTags = async () => {
  try {
    const res = await getTagList(1);
    if (res.data.code === 200) {
      tagList.value = res.data.data;
    }
  } catch (error) {
    console.error('获取标签列表失败:', error);
  }
};

const switchTab = (tab) => {
  activeTab.value = tab;
  params.value.tab = tab;
  postList.value = [];
  finished.value = false;
  params.value.size = 5; // 重置size
  
  // 更新路由参数
  router.push({
    query: { 
      ...route.query,
      tab: tab 
    }
  });
  
  fetchPosts();
};

const loadMore = () => {
  if (loading.value || finished.value) return;
  fetchPosts();
};

const fetchPosts = async () => {
  loading.value = true;
  try {
    const res = await getPostList(params.value);
    if (res.data.code === 200) {
      const { records, total: totalCount, current, pages } = res.data.data;
      
      // 为每个帖子检查点赞和收藏状态
      const postsWithStatus = await Promise.all(
        records.map(async (post) => {
          try {
            // 并行检查点赞和收藏状态
            const [likeRes, collectRes] = await Promise.all([
              checkLike(post.id),
              checkCollect(post.id)
            ]);
            
            return {
              ...post,
              isLiked: likeRes.data.code === 200 ? likeRes.data.data : false,
              isCollected: collectRes.data.code === 200 ? collectRes.data.data : false
            };
          } catch (error) {
            console.error(`检查帖子 ${post.id} 状态失败:`, error);
            // 如果检查状态失败，默认为未点赞未收藏
            return {
              ...post,
              isLiked: false,
              isCollected: false
            };
          }
        })
      );
      
      postList.value = sortPosts(postsWithStatus);
      
      total.value = totalCount;
      
      // 判断是否加载完所有数据
      if (current >= pages || records.length === 0) {
        finished.value = true;
      } else {
        params.value.size += 5;
      }
    }
  } catch (error) {
    console.error('获取帖子列表失败:', error);
  } finally {
    loading.value = false;
  }
};

const formatDate = (dateString) => {
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const goToPostDetail = (postId) => {
  router.push(`/post/${postId}`);
};

// 处理标题点击
const handleTitleClick = (postId, event) => {
  event.stopPropagation(); // 阻止事件冒泡，避免触发整个帖子项的点击
  router.push(`/post/${postId}`);
};

// 处理点赞
const handleLike = async (post, event) => {
  event.stopPropagation(); // 阻止事件冒泡，避免触发跳转
  
  try {
    const res = await likePost(post.id);
    if (res.data.code === 200) {
      // 更新本地状态
      const newLikedState = !post.isLiked;
      post.likes = newLikedState ? post.likes + 1 : Math.max(0, post.likes - 1);
      post.isLiked = newLikedState;
      ElMessage.success(post.isLiked ? '点赞成功' : '取消点赞');
    } else {
      ElMessage.error(res.data.message || '操作失败');
    }
  } catch (error) {
    console.error('点赞失败:', error);
    ElMessage.error('操作失败，请重试');
  }
};

// 处理收藏
const handleCollect = async (post, event) => {
  event.stopPropagation(); // 阻止事件冒泡，避免触发跳转
  
  try {
    const res = await collectPost({ topicId: post.id ,userName: userStore.user.username});
    if (res.data.code === 200) {
      // 更新本地状态
      const newCollectedState = !post.isCollected;
      post.collects = newCollectedState ? post.collects + 1 : Math.max(0, post.collects - 1);
      post.isCollected = newCollectedState;
      ElMessage.success(post.isCollected ? '收藏成功' : '取消收藏');
    } else {
      ElMessage.error(res.data.message || '操作失败');
    }
  } catch (error) {
    console.error('收藏失败:', error);
    ElMessage.error('操作失败，请重试');
  }
};

// 处理查看和评论点击（跳转到详情页）
const handleViewOrComment = (postId, anchor = '') => {
  if (anchor) {
    router.push(`/post/${postId}#${anchor}`);
  } else {
    router.push(`/post/${postId}`);
  }
};

// 定义刷新方法
const refreshList = async () => {
  currentPage.value = 1;
  await fetchPosts();
};

const handleScroll = () => {
  if (loading.value || finished.value) return;
  const scrollTop = window.scrollY || document.documentElement.scrollTop;
  const windowHeight = window.innerHeight;
  const docHeight = document.documentElement.scrollHeight;
  if (scrollTop + windowHeight >= docHeight - 10) {
    loadMore();
  }
};

// 暴露方法给父组件使用
defineExpose({
  refreshList
});

onMounted(() => {
  fetchTags();
  if (route.query.tab) {
    activeTab.value = route.query.tab;
    params.value.tab = route.query.tab;
  }
  fetchPosts();
  window.addEventListener('scroll', handleScroll);
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll);
});
</script>

<template>
  <div class="post-list-container">
    <div class="tabs-container">
      <div class="main-tabs">
        <div 
          class="tab-item" 
          :class="{ active: activeTab === 'latest' }"
          @click="switchTab('latest')"
        >
          最新
        </div>
        <div 
          class="tab-item" 
          :class="{ active: activeTab === 'hot' }"
          @click="switchTab('hot')"
        >
          热门
        </div>
        <div class="divider"></div>
      </div>
      <div class="tag-tabs">
        <div 
          v-for="tag in tagList"
          :key="tag.id"
          class="tab-item tag-tab"
          :class="{ active: activeTab === tag.name }"
          @click="switchTab(tag.name)"
        >
          {{ tag.name }}
        </div>
      </div>
    </div>

    <div class="posts-container">
      <!-- 左侧帖子列表 -->
      <div class="posts-list">
        <div v-if="postList.length === 0 && !loading" class="no-posts">
          暂无主题
        </div>
        <div v-else v-for="post in postList" :key="post.id" class="post-item" @click="goToPostDetail(post.id)">
          <!-- 左侧用户信息和主要内容 -->
          <div class="post-main">
            <div class="post-header">
              <div class="user-info">
                <img :src="post.avatar" :alt="post.username" class="user-avatar">
                <div class="user-meta">
                  <div class="alias">{{ post.alias }}</div>
                  <div class="post-time">{{ formatDate(post.createTime) }}</div>
                </div>
              </div>
            </div>

            <div class="post-content">
              <div class="post-title" @click="handleTitleClick(post.id, $event)">
                <span v-if="post.top" class="post-tag top">置顶</span>
                <span v-if="post.essence" class="post-tag essence">精华</span>
                <span class="title-text">{{ post.title }}</span>
              </div>
              <div class="post-tags">
                <span v-for="tag in post.tags" :key="tag.id" class="tag">
                  #{{ tag.name }}
                </span>
              </div>
            </div>

            <div class="post-footer">
              <div class="action-item clickable" @click="handleViewOrComment(post.id)">
                <i class="far fa-eye"></i>
                <span>{{ post.view }}</span>
              </div>
              <div class="action-item clickable" @click="handleViewOrComment(post.id, 'comments')">
                <i class="far fa-comment"></i>
                <span>{{ post.comments }}</span>
              </div>
              <div class="action-item clickable" 
                   :class="{ active: post.isLiked }" 
                   @click="handleLike(post, $event)">
                <i :class="post.isLiked ? 'fas fa-thumbs-up' : 'far fa-thumbs-up'"></i>
                <span>{{ post.likes }}</span>
              </div>
              <div class="action-item clickable" 
                   :class="{ active: post.isCollected }" 
                   @click="handleCollect(post, $event)">
                <i :class="post.isCollected ? 'fas fa-star' : 'far fa-star'"></i>
                <span>{{ post.collects }}</span>
              </div>
            </div>
          </div>

          <!-- 右侧封面图 -->
          <div class="post-cover" v-if="post.coverImg">
            <img :src="post.coverImg" :alt="post.title">
          </div>
        </div>

        <div class="load-more-container" v-if="postList.length > 0">
          <div v-if="loading" class="loading">
            <i class="el-icon-loading"></i>
            <span>加载中...</span>
          </div>
          <!-- 自动加载，无需按钮 -->
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.post-list-container {
  background-color: var(--el-bg-color-page);
  border-radius: 10px;
  box-shadow: 0 1px 3px var(--el-box-shadow);
  overflow: hidden;
}

.tabs-container {
  display: flex;
  flex-wrap: wrap;
  border-bottom: 1px solid var(--el-border-color);
  background-color: var(--el-bg-color-page);
  padding: 0 20px;
  gap: 8px;
  
  .main-tabs {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 0;
  }
  
  .tag-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px 0;
  }
}

.divider {
  width: 1px;
  height: 20px;
  background-color: var(--el-border-color-light);
  margin: 0 8px;
}

.tab-item {
  padding: 8px 16px;
  cursor: pointer;
  font-weight: 500;
  color: var(--el-text-color-regular);
  position: relative;
  transition: all 0.3s;
  border-radius: 16px;

  &:hover {
    color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
  }

  &.active {
    color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);

    &::after {
      display: none;
    }
  }
  
  &.tag-tab {
    font-size: 14px;
    padding: 6px 12px;
    background-color: var(--el-fill-color-light);
    
    &:hover, &.active {
      background-color: var(--el-color-primary-light-9);
    }
  }
}

.posts-container {
  padding: 20px;
}

.posts-list {
  background: var(--el-bg-color-page);
  border-radius: 10px;
  // box-shadow: 0 1px 3px var(--el-box-shadow);
}

.post-item {
  display: flex;
  gap: 15px;
  padding: 18px 0;
  background: transparent;
  box-shadow: none;
  border-bottom: 1px solid var(--el-border-color-light);
  transition: background 0.2s;
  cursor: pointer;

  &:hover {
    background: var(--el-bg-color-overlay);
  }
}

.post-main {
  flex: 1;
  min-width: 0;
}

.post-header {
  margin-bottom: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.user-meta {
  display: flex;
  flex-direction: column;
}

.alias {
  font-weight: 400;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.post-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 2px;
}

.post-content {
  margin-bottom: 16px;
}

.post-title {
  font-size: 22px;
  font-weight: 1000;
  font-family: monospace;
  color: var(--el-text-color-primary);
  margin-left: 5px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  
  .title-text {
    color: var(--el-text-color-primary);
    transition: all 0.3s ease;
    
    &:hover {
      color: var(--el-color-primary);
    }
  }
  
  &:hover .title-text {
    color: var(--el-color-primary);
  }
}

.post-tag {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: normal;

  &.top {
    background-color: var(--el-color-danger-light-9);
    color: var(--el-color-danger);
  }

  &.essence {
    background-color: var(--el-color-success-light-9);
    color: var(--el-color-success);
  }
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  font-size: 12px;
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  padding: 2px 8px;
  border-radius: 4px;
}

.post-footer {
  display: flex;
  gap: 20px;
  color: var(--el-text-color-secondary);
}

.action-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  transition: all 0.3s ease;

  i {
    font-size: 16px;
    transition: all 0.3s ease;
  }

  &.clickable {
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 6px;
    
    &:hover {
      background-color: var(--el-color-primary-light-9);
      transform: translateY(-1px);
      
      i {
        transform: scale(1.1);
      }
    }
    
    // 查看按钮hover效果
    &:nth-child(1):hover {
      color: var(--el-color-info);
      background-color: var(--el-color-info-light-9);
    }
    
    // 评论按钮hover效果
    &:nth-child(2):hover {
      color: var(--el-color-primary);
      background-color: var(--el-color-primary-light-9);
    }
    
    // 点赞按钮hover效果
    &:nth-child(3):hover {
      color: var(--el-color-danger);
      background-color: var(--el-color-danger-light-9);
    }
    
    // 收藏按钮hover效果
    &:nth-child(4):hover {
      color: var(--el-color-warning);
      background-color: var(--el-color-warning-light-9);
    }
    
    &.active {
      &:nth-child(3) {
        color: var(--el-color-danger);
        i {
          color: var(--el-color-danger);
        }
      }
      
      &:nth-child(4) {
        color: var(--el-color-warning);
        i {
          color: var(--el-color-warning);
        }
      }
    }
  }
}

.post-cover {
  width: 200px;
  height: 150px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.load-more-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px 0;
  
  .loading {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--el-text-color-secondary);
    
    i {
      font-size: 20px;
    }
  }
  
  .no-more {
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}

@media (max-width: 768px) {
  .post-cover {
    display: none;
  }

  .post-item {
    padding: 15px;
  }

  .post-title {
    font-size: 17px;
  }
  
  .user-avatar {
    width: 28px;
    height: 28px;
  }
  
  .alias {
    font-size: 13px;
  }
  
  .post-time {
    font-size: 10px;
  }
  
  .post-footer {
    gap: 12px;
    
    .action-item {
      font-size: 13px;
      
      &.clickable {
        padding: 6px;
      }
      
      i {
        font-size: 14px;
      }
    }
  }
}
</style>