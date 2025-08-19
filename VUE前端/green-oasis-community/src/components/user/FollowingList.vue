<template>
  <div class="following-container">
    <div class="following-header">
      <h3>关注列表</h3>
      <div class="following-count">共关注 {{ following.length }} 人</div>
    </div>
    
    <div v-if="isLoading" class="loading-state">
      <div class="loading-spinner"></div>
      <span>加载中...</span>
    </div>
    
    <div v-else-if="following.length === 0" class="empty-following">
      暂无关注
    </div>
    
    <div v-else class="following-list">
      <div v-for="user in following" :key="user.id" class="following-item">
        <div class="following-info" @click="goToUserPage(user.username)">
          <img :src="user.avatar" :alt="user.username" class="following-avatar">
          <div class="following-details">
            <div class="following-username">{{ user.username }}</div>
            <div class="following-message">{{ user.message || '这个人很懒，什么都没写~' }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getFollowing } from '@/api/relationship'
import { ElMessage } from 'element-plus'

// 接收父组件传入的 props
const props = defineProps({
  viewUserInfo: {
    type: Object,
    required: true
  }
})

const router = useRouter()

const following = ref([])
const isLoading = ref(true)

const fetchFollowing = async () => {
  if (!props.viewUserInfo?.username) return
  
  try {
    isLoading.value = true
    const res = await getFollowing(props.viewUserInfo.username)
    
    if (res.data.code === 200) {
      following.value = res.data.data || []
    } else {
      ElMessage.error('获取关注列表失败')
    }
  } catch (error) {
    console.error('获取关注列表失败:', error)
    ElMessage.error('获取关注列表失败')
  } finally {
    isLoading.value = false
  }
}

const goToUserPage = (username) => {
  router.push(`/user/${username}`)
}

// 监听用户信息变化，重新获取数据
watch(() => props.viewUserInfo, (newVal) => {
  if (newVal?.username) {
    fetchFollowing()
  }
}, { immediate: true })

onMounted(() => {
  if (props.viewUserInfo?.username) {
    fetchFollowing()
  }
})
</script>

<style lang="scss" scoped>
.following-container {
  background: var(--comment-bg);
  border-radius: 8px;
  padding: 20px;
}

.following-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--comment-divider);

  h3 {
    font-size: 18px;
    font-weight: 600;
    color: var(--comment-text-primary);
    margin: 0;
  }

  .following-count {
    font-size: 14px;
    color: var(--comment-text-secondary);
  }
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: var(--comment-text-secondary);
}

.loading-spinner {
  width: 30px;
  height: 30px;
  border: 2px solid var(--el-border-color-lighter);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: spinner 0.8s linear infinite;
  margin-bottom: 12px;
}

@keyframes spinner {
  to {transform: rotate(360deg);}
}

.empty-following {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 150px;
  color: var(--comment-text-secondary);
}

.following-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.following-item {
  padding: 16px;
  border-bottom: 1px solid var(--comment-divider);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background-color: var(--comment-hover-bg);
    transform: translateX(4px);
  }

  &:last-child {
    border-bottom: none;
  }
}

.following-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.following-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--el-color-primary-light-8);
  flex-shrink: 0;
}

.following-details {
  flex: 1;
  min-width: 0;
}

.following-username {
  font-size: 16px;
  font-weight: 500;
  color: var(--comment-text-primary);
  margin-bottom: 4px;
}

.following-message {
  font-size: 14px;
  color: var(--comment-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
