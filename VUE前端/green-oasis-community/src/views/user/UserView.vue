<script setup>
import { ref, onMounted, computed, provide, watch, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getUserInfo } from '@/api/user'
import { followUser, unfollowUser, checkFollow } from '@/api/relationship'
import { useUserStore } from '@/stores/modules/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const viewUserName = ref('') // 要查看的用户名

const total = ref(0)
const userStore = useUserStore()
const viewUserInfo = ref(null) // 正在查看的用户信息
const isOwner = computed(() => !viewUserName.value || viewUserName.value === userStore.user.username)
const isLoading = ref(true)
const isFollowing = ref(false)
const followLoading = ref(false)

// 向子组件提供数据
provide('viewUserInfo', viewUserInfo)
provide('isOwner', isOwner)

// 获取要查看的用户名
// onMounted 在上面的 fetchCurrentUserInfo 函数后面处理

// 监听路由变化，当用户名改变时重新加载数据
watch(() => route.params.username, async (newUsername) => {
  viewUserName.value = newUsername || ''
  
  if (viewUserName.value && viewUserName.value !== userStore.user.username) {
    fetchUserInfo()
  } else {
    // 切换到自己的个人主页 - 主动从后端获取最新信息
    await fetchCurrentUserInfo()
  }
})

// 获取用户信息
const fetchUserInfo = async () => {
  try {
    isLoading.value = true
    const res = await getUserInfo(viewUserName.value)
    if (res.data.code === 200) {
      viewUserInfo.value = res.data.data
      // 获取关注状态
      checkFollowStatus()
    } else {
      ElMessage.error('获取用户信息失败')
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    ElMessage.error('获取用户信息失败')
  } finally {
    isLoading.value = false
  }
}

// 获取当前用户信息（自己的个人主页）
const fetchCurrentUserInfo = async () => {
  try {
    isLoading.value = true
    const currentUsername = userStore.user?.username
    
    if (!currentUsername) {
      // 如果没有用户名，显示store中的用户信息
      viewUserInfo.value = userStore.user
      isLoading.value = false
      return
    }
    
    const res = await getUserInfo(currentUsername)
    if (res.data.code === 200) {
      viewUserInfo.value = res.data.data
      // 更新store中的用户信息
      userStore.setUser(res.data.data)
    } else {
      // 获取失败时使用store中的用户信息
      viewUserInfo.value = userStore.user
    }
  } catch (error) {
    console.error('获取当前用户信息失败:', error)
    // 获取失败时使用store中的用户信息
    viewUserInfo.value = userStore.user
  } finally {
    isLoading.value = false
  }
}

// 监听用户信息更新事件
const handleUserInfoUpdate = () => {
  if (isOwner.value) {
    fetchCurrentUserInfo()
  }
}

// 在组件挂载时添加事件监听
onMounted(async () => {
  // 监听全局用户信息更新事件
  window.addEventListener('user-info-updated', handleUserInfoUpdate)
  
  // 从路由参数获取用户名
  viewUserName.value = route.params.username || ''
  
  // 加载用户信息
  if (viewUserName.value && viewUserName.value !== userStore.user.username) {
    fetchUserInfo()
  } else {
    // 查看自己的个人主页 - 主动从后端获取最新信息
    await fetchCurrentUserInfo()
  }
})

// 组件卸载时移除事件监听
onBeforeUnmount(() => {
  window.removeEventListener('user-info-updated', handleUserInfoUpdate)
})

// 检查关注状态
const checkFollowStatus = async () => {
  if (!viewUserInfo.value || !viewUserInfo.value.id) return
  
  try {
    const res = await checkFollow(viewUserInfo.value.id)
    if (res.data.code === 200) {
      isFollowing.value = res.data.data.hasFollow
    }
  } catch (error) {
    console.error('获取关注状态失败:', error)
  }
}

// 关注/取消关注用户
const handleFollowUser = async () => {
  if (!viewUserInfo.value || followLoading.value) return
  
  try {
    followLoading.value = true
    const followAction = isFollowing.value ? unfollowUser : followUser
    const res = await followAction(viewUserInfo.value.id)
    
    if (res.data.code === 200) {
      isFollowing.value = !isFollowing.value
      ElMessage.success(res.data.message || (isFollowing.value ? '关注成功' : '已取消关注'))
      
      // 更新用户粉丝数量
      if (viewUserInfo.value) {
        if (isFollowing.value) {
          viewUserInfo.value.followerCount = (viewUserInfo.value.followerCount || 0) + 1
        } else if (viewUserInfo.value.followerCount > 0) {
          viewUserInfo.value.followerCount--
        }
      }
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (error) {
    console.error('关注操作失败:', error)
    ElMessage.error('操作失败，请重试')
  } finally {
    followLoading.value = false
  }
}

const formatDate = (dateString) => {
  const now = new Date()
  const date = new Date(dateString)
  const diff = now - date
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 30) {
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
  } else if (days > 0) {
    return `${days}天前`
  } else if (hours > 0) {
    return `${hours}小时前`
  } else if (minutes > 0) {
    return `${minutes}分钟前`
  } else {
    return '刚刚'
  }
}

// 处理总数更新
const handleUpdateTotal = (newTotal) => {
  total.value = newTotal
}

// 导航到不同的子路由
const goToTopics = () => {
  const username = viewUserName.value
  if (username) {
    router.push(`/user/${username}/topics`)
  } else {
    router.push('/user/topics')
  }
}

const goToFollowers = () => {
  const username = viewUserName.value
  if (username) {
    router.push(`/user/${username}/followers`)
  } else {
    router.push('/user/followers')
  }
}

const goToFollowing = () => {
  const username = viewUserName.value
  if (username) {
    router.push(`/user/${username}/following`)
  } else {
    router.push('/user/following')
  }
}
</script>
<template>
  <div class="user-container" v-if="viewUserInfo">
    <!-- 左侧个人信息 -->
    <div class="user-info">
      <div class="user-header">
        <img :src="viewUserInfo.avatar" :alt="viewUserInfo.username" class="user-avatar">
        <div class="user-alias">@{{ viewUserInfo.alias }}</div>
        <h2 class="user-name">{{ viewUserInfo.username }}</h2>
        
        <!-- 关注按钮 - 只在查看他人页面时显示 -->
        <div v-if="!isOwner" class="follow-btn-container">
          <el-button 
            type="primary" 
            class="follow-btn" 
            :plain="!isFollowing"
            :loading="followLoading"
            @click="handleFollowUser"
          >
            <i :class="['fas', isFollowing ? 'fa-check' : 'fa-plus']" style="margin-right: 5px;"></i>
            {{ isFollowing ? '已关注' : '关注作者' }}
          </el-button>
        </div>
      </div>
      
      <div class="user-meta">
        <div class="user-stats">
          <div class="stat-group" @click="goToTopics">
            <div class="stat-value">{{ viewUserInfo.postCount || 0 }}</div>
            <div class="stat-label">文章</div>
          </div>
          <div class="stat-group" @click="goToFollowers">
            <div class="stat-value">{{ viewUserInfo.followerCount || 0 }}</div>
            <div class="stat-label">粉丝</div>
          </div>
          <div class="stat-group" @click="goToFollowing">
            <div class="stat-value">{{ viewUserInfo.followingCount || 0 }}</div>
            <div class="stat-label">关注</div>
          </div>
        </div>
        
        <div class="meta-item">
          <div class="meta-label">个人简介</div>
          <div class="meta-value">{{ viewUserInfo.bio || '这个人很懒，什么都没写~' }}</div>
        </div>
        <div class="meta-item">
          <div class="meta-label">邮箱</div>
          <div class="meta-value">{{ viewUserInfo.email || '未设置' }}</div>
        </div>
        <div class="meta-item">
          <div class="meta-label">积分</div>
          <div class="meta-value">{{ viewUserInfo.score }}</div>
        </div>
        <div class="meta-item">
          <div class="meta-label">注册时间</div>
          <div class="meta-value">{{ formatDate(viewUserInfo.createTime) }}</div>
        </div>
      </div>
    </div>

    <!-- 右侧文章列表 -->
    <div class="topics-container">
      <!-- 使用 router-view 显示子路由组件 -->
      <router-view 
        :view-user-info="viewUserInfo" 
        :is-owner="isOwner"
        @update-total="handleUpdateTotal"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.user-container {
  max-width: 1400px;
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 20px;
}

.user-info {
  background: var(--comment-bg);
  border-radius: 8px;
  padding: 20px;
  height: fit-content;
}

.user-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--comment-divider);
}

.follow-btn-container {
  margin-top: 16px;
  width: 100%;
}

.follow-btn {
  width: 100%;
}

.user-avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  object-fit: cover;
  margin-bottom: 16px;
  border: 3px solid var(--el-color-primary-light-8);
}

.user-name {
  font-size: 14px;
  color: var(--comment-text-secondary);
  margin: 0 0 4px;
}

.user-alias {
  font-size: 20px;
  font-weight: 600;
  color: var(--comment-text-primary);
}

.user-stats {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--comment-divider);
}

.stat-group {
  text-align: center;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.3s ease;

  &:hover {
    background-color: var(--comment-hover-bg);
    transform: translateY(-2px);
  }
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--comment-text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--comment-text-secondary);
  margin-top: 4px;
}

.user-meta {
  margin-top: 20px;
}

.meta-item {
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.meta-label {
  font-size: 14px;
  color: var(--comment-text-secondary);
  margin-bottom: 4px;
}

.meta-value {
  font-size: 14px;
  color: var(--comment-text-primary);
  word-break: break-all;
}

.topics-container {
  background: var(--comment-bg);
  border-radius: 8px;
  padding: 0;
  overflow: hidden;
}

@media (max-width: 768px) {
  .user-container {
    grid-template-columns: 1fr;
  }
}
</style>
