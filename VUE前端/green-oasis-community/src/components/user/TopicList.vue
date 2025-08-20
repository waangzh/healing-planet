<template>
  <div class="topics-container">
    <div class="topics-header">
      <h3>文章列表</h3>
      <div class="topics-count">共 {{ total }} 篇</div>
    </div>
    
    <div v-if="isLoading" class="loading-state">
      <div class="loading-spinner"></div>
      <span>加载中...</span>
    </div>
    
    <div v-else-if="topics.length === 0" class="empty-topics">
      暂无文章
    </div>
    
    <div v-else class="topics-list">
      <div v-for="topic in topics" :key="topic.id" class="topic-item">
        <div class="topic-content" @click="goToDetail(topic.id)">
          <div class="topic-header-info">
            <h3 class="topic-title">{{ topic.title }}</h3>
          </div>
          <div class="topic-meta">
            <span class="time">{{ formatDate(topic.createTime) }}</span>
            <div class="topic-stats">
              <span class="stat-item">
                <i class="far fa-eye"></i>
                {{ topic.view }}
              </span>
              <span class="stat-item">
                <i class="far fa-comment"></i>
                {{ topic.comments }}
              </span>
              <span class="stat-item">
                <i class="far fa-heart"></i>
                {{ topic.likes }}
              </span>
            </div>
          </div>
        </div>
        <div class="topic-cover" v-if="topic.coverImg">
          <img :src="topic.coverImg" :alt="topic.title" @click="goToDetail(topic.id)" />
        </div>
        <!-- 编辑和删除按钮只在查看自己的页面时显示 -->
        <div v-if="isOwner" class="topic-actions">
          <el-button 
            type="primary" 
            link 
            @click.stop="handleEdit(topic)"
          >
            <i class="fas fa-edit"></i> 编辑
          </el-button>
          <el-button 
            type="danger" 
            link 
            @click.stop="handleDelete(topic)"
          >
            <i class="fas fa-trash"></i> 删除
          </el-button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination-container" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[5, 10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getUserTopics } from '@/api/user'
import { deletePost } from '@/api/post'
import { ElMessage, ElMessageBox } from 'element-plus'

// 接收父组件传入的 props
const props = defineProps({
  viewUserInfo: {
    type: Object,
    required: true
  },
  isOwner: {
    type: Boolean,
    default: false
  }
})

// 定义 emits 向父组件传递事件
const emit = defineEmits(['update-total'])

const router = useRouter()

const topics = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(5)
const isLoading = ref(true)

const fetchTopics = async () => {
  if (!props.viewUserInfo?.username) return
  
  try {
    isLoading.value = true
    const res = await getUserTopics({
      username: props.viewUserInfo.username,
      pageNo: currentPage.value,
      size: pageSize.value
    })
    
    if (res.data.code === 200) {
      topics.value = res.data.data.topics.records
      total.value = res.data.data.topics.total
      // 向父组件传递总数更新
      emit('update-total', total.value)
    }
  } catch (error) {
    console.error('获取文章列表失败:', error)
    ElMessage.error('获取文章列表失败')
  } finally {
    isLoading.value = false
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

const goToDetail = (id) => {
  router.push(`/post/${id}`)
}

const handleSizeChange = (val) => {
  pageSize.value = val
  fetchTopics()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchTopics()
}

const handleEdit = (topic) => {
  router.push({
    path: '/post/create',
    query: { id: topic.id }
  })
}

const handleDelete = async (topic) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这篇文章吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const res = await deletePost(topic.id)
    if (res.data.code === 200) {
      ElMessage.success('删除成功')
      fetchTopics() // 重新获取列表
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败，请重试')
    }
  }
}

// 监听用户信息变化，重新获取数据
watch(() => props.viewUserInfo, (newVal) => {
  if (newVal?.username) {
    fetchTopics()
  }
}, { immediate: true })

onMounted(() => {
  if (props.viewUserInfo?.username) {
    fetchTopics()
  }
})
</script>

<style lang="scss" scoped>
.topics-container {
  background: var(--comment-bg);
  border-radius: 8px;
  padding: 20px;
}

.topics-header {
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

  .topics-count {
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

.empty-topics {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 150px;
  color: var(--comment-text-secondary);
}

.topics-list {
  display: flex;
  flex-direction: column;
}

.topic-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid var(--comment-divider);
  transition: background-color 0.3s ease;

  &:hover {
    background-color: var(--comment-hover-bg);
  }

  &:last-child {
    border-bottom: none;
  }
}

.topic-cover {
  width: 120px;
  height: 80px;
  flex-shrink: 0;
  border-radius: 4px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    cursor: pointer;
  }
}

.topic-content {
  flex: 1;
  min-width: 0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.topic-header-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topic-title {
  font-size: 22px;
  font-weight: 1000;
  font-family: monospace;
  color: var(--comment-text-primary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  
  &:hover {
    color: var(--el-color-primary);
  }
}

.topic-badges {
  flex-shrink: 0;
}

.topic-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--comment-text-secondary);
}

.topic-stats {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;

  i {
    font-size: 14px;
  }
}

.topic-actions {
  display: flex;
  gap: 8px;
  margin-left: auto; /* Pushes actions to the far right */
  
  .el-button {
    padding: 4px 8px;
    
    i {
      margin-right: 4px;
    }
  }
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

:deep(.el-pagination) {
  --el-pagination-bg-color: var(--comment-bg);
  --el-pagination-hover-color: var(--el-color-primary);
  --el-pagination-button-color: var(--comment-text-regular);
  --el-pagination-button-bg-color: var(--comment-bg);
  --el-pagination-button-disabled-color: var(--comment-text-secondary);
  --el-pagination-button-disabled-bg-color: var(--comment-bg);
  --el-pagination-border-radius: 4px;

  .el-pagination__total,
  .el-pagination__sizes {
    color: var(--comment-text-regular);
  }
}
</style>
