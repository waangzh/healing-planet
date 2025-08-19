<template>
  <div class="post-detail">
    <div class="page-title">
      <el-button :icon="ArrowLeft" @click="$router.go(-1)">返回</el-button>
      <span>帖子详情</span>
    </div>
    
    <div v-if="postInfo" class="post-detail-content">
      <!-- 帖子基本信息 -->
      <div class="admin-card">
        <div class="card-header">
          <h3 class="card-title">{{ postInfo.title }}</h3>
          <div class="card-actions">
            <el-button
              v-if="postInfo.status === 'pending'"
              type="success"
              @click="approvePost"
            >
              审核通过
            </el-button>
            <el-button
              v-if="postInfo.status === 'pending'"
              type="warning"
              @click="rejectPost"
            >
              拒绝发布
            </el-button>
            <el-button
              v-if="postInfo.status === 'published'"
              :type="postInfo.isPinned ? 'warning' : 'info'"
              @click="togglePin"
            >
              {{ postInfo.isPinned ? '取消置顶' : '设为置顶' }}
            </el-button>
            <el-button type="danger" @click="deletePost">删除帖子</el-button>
          </div>
        </div>
        <div class="card-body">
          <div class="post-meta">
            <div class="author-info">
              <el-avatar :src="postInfo.author.avatar" :size="40" />
              <div class="author-details">
                <div class="username">{{ postInfo.author.username }}</div>
                <div class="publish-time">{{ formatDate(postInfo.createdAt) }}</div>
              </div>
            </div>
            <div class="post-status">
              <el-tag :type="getStatusType(postInfo.status)">
                {{ getStatusText(postInfo.status) }}
              </el-tag>
              <el-tag v-if="postInfo.isPinned" type="warning">置顶</el-tag>
            </div>
          </div>
          
          <div class="post-content" v-html="postInfo.content"></div>
          
          <div v-if="postInfo.tags && postInfo.tags.length" class="post-tags">
            <span class="tags-label">标签：</span>
            <el-tag
              v-for="tag in postInfo.tags"
              :key="tag"
              type="info"
              class="tag-item"
            >
              {{ tag }}
            </el-tag>
          </div>
        </div>
      </div>
      
      <!-- 统计信息 -->
      <el-row :gutter="24" class="stats-row">
        <el-col :xs="12" :sm="6">
          <div class="stat-card primary">
            <el-icon class="stat-icon"><View /></el-icon>
            <div class="stat-number">{{ postInfo.viewCount }}</div>
            <div class="stat-label">浏览量</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card success">
            <el-icon class="stat-icon"><Star /></el-icon>
            <div class="stat-number">{{ postInfo.likeCount }}</div>
            <div class="stat-label">点赞数</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card warning">
            <el-icon class="stat-icon"><ChatLineRound /></el-icon>
            <div class="stat-number">{{ postInfo.commentCount }}</div>
            <div class="stat-label">评论数</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card danger">
            <el-icon class="stat-icon"><Share /></el-icon>
            <div class="stat-number">{{ postInfo.shareCount }}</div>
            <div class="stat-label">分享数</div>
          </div>
        </el-col>
      </el-row>
      
      <!-- 评论列表 -->
      <div class="admin-card">
        <div class="card-header">
          <h3 class="card-title">评论列表 ({{ comments.length }})</h3>
        </div>
        <div class="card-body">
          <div v-if="comments.length === 0" class="empty-state">
            <el-empty description="暂无评论" />
          </div>
          <div v-else class="comment-list">
            <div v-for="comment in comments" :key="comment.id" class="comment-item">
              <div class="comment-header">
                <div class="commenter-info">
                  <el-avatar :src="comment.author.avatar" :size="32" />
                  <div class="commenter-details">
                    <span class="username">{{ comment.author.username }}</span>
                    <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
                  </div>
                </div>
                <el-tag :type="getCommentStatusType(comment.status)">
                  {{ getCommentStatusText(comment.status) }}
                </el-tag>
              </div>
              <div class="comment-content">{{ comment.content }}</div>
              <div class="comment-actions">
                <el-button
                  v-if="comment.status === 'pending'"
                  type="success"
                  size="small"
                  @click="approveComment(comment)"
                >
                  通过
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  @click="deleteComment(comment)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <div v-else class="loading-container">
      <el-loading-spinner />
      <span>加载中...</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  ArrowLeft, 
  View, 
  Star, 
  ChatLineRound, 
  Share 
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 响应式数据
const postInfo = ref(null)
const comments = ref([])

// 方法
const getStatusType = (status) => {
  const statusMap = {
    published: 'success',
    pending: 'warning',
    rejected: 'danger',
    deleted: 'info'
  }
  return statusMap[status] || ''
}

const getStatusText = (status) => {
  const statusMap = {
    published: '已发布',
    pending: '待审核',
    rejected: '已拒绝',
    deleted: '已删除'
  }
  return statusMap[status] || '未知'
}

const getCommentStatusType = (status) => {
  const statusMap = {
    normal: 'success',
    pending: 'warning',
    deleted: 'info'
  }
  return statusMap[status] || ''
}

const getCommentStatusText = (status) => {
  const statusMap = {
    normal: '正常',
    pending: '待审核',
    deleted: '已删除'
  }
  return statusMap[status] || '未知'
}

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const approvePost = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要审核通过帖子 "${postInfo.value.title}" 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )
    
    postInfo.value.status = 'published'
    ElMessage.success('审核通过')
    
  } catch {
    // 用户取消操作
  }
}

const rejectPost = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要拒绝帖子 "${postInfo.value.title}" 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    postInfo.value.status = 'rejected'
    ElMessage.success('已拒绝')
    
  } catch {
    // 用户取消操作
  }
}

const togglePin = async () => {
  const action = postInfo.value.isPinned ? '取消置顶' : '置顶'
  
  try {
    await ElMessageBox.confirm(
      `确定要${action}帖子 "${postInfo.value.title}" 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    postInfo.value.isPinned = !postInfo.value.isPinned
    ElMessage.success(`${action}成功`)
    
  } catch {
    // 用户取消操作
  }
}

const deletePost = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除帖子 "${postInfo.value.title}" 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    ElMessage.success('删除成功')
    router.push('/posts')
    
  } catch {
    // 用户取消操作
  }
}

const approveComment = async (comment) => {
  try {
    await ElMessageBox.confirm(
      '确定要审核通过该评论吗？',
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )
    
    comment.status = 'normal'
    ElMessage.success('评论审核通过')
    
  } catch {
    // 用户取消操作
  }
}

const deleteComment = async (comment) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除该评论吗？此操作不可恢复！',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    const index = comments.value.findIndex(c => c.id === comment.id)
    if (index !== -1) {
      comments.value.splice(index, 1)
    }
    ElMessage.success('评论删除成功')
    
  } catch {
    // 用户取消操作
  }
}

// 获取帖子详情
const fetchPostDetail = async () => {
  try {
    const postId = route.params.id
    
    // 模拟API调用
    postInfo.value = {
      id: postId,
      title: `帖子标题${postId}`,
      content: `<p>这是帖子${postId}的详细内容，包含了丰富的植物养护知识和经验分享。</p><p>内容可以包含图片、文字等多种形式。</p>`,
      status: 'published',
      isPinned: false,
      author: {
        id: 1,
        username: `author${postId}`,
        avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${postId}`
      },
      tags: ['多肉植物', '养护经验'],
      viewCount: Math.floor(Math.random() * 1000),
      likeCount: Math.floor(Math.random() * 100),
      commentCount: Math.floor(Math.random() * 50),
      shareCount: Math.floor(Math.random() * 20),
      createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
    }
    
    // 模拟评论数据
    comments.value = Array.from({ length: 5 }, (_, index) => ({
      id: index + 1,
      content: `这是评论${index + 1}的内容，用户对帖子的看法和交流。`,
      status: index % 3 === 0 ? 'pending' : 'normal',
      author: {
        id: index + 1,
        username: `commenter${index + 1}`,
        avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${index + 100}`
      },
      createdAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString()
    }))
    
  } catch (error) {
    ElMessage.error('获取帖子详情失败')
    router.go(-1)
  }
}

// 生命周期
onMounted(() => {
  fetchPostDetail()
})
</script>

<style lang="scss" scoped>
.post-detail {
  .page-title {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    font-size: 24px;
    font-weight: 600;
    color: #303133;
  }
  
  .post-detail-content {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    
    .post-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      padding-bottom: 20px;
      border-bottom: 1px solid #ebeef5;
      
      .author-info {
        display: flex;
        align-items: center;
        gap: 12px;
        
        .author-details {
          .username {
            font-weight: 500;
            color: #303133;
            margin-bottom: 4px;
          }
          
          .publish-time {
            font-size: 12px;
            color: #909399;
          }
        }
      }
      
      .post-status {
        display: flex;
        gap: 8px;
      }
    }
    
    .post-content {
      margin-bottom: 20px;
      line-height: 1.8;
      color: #606266;
      
      :deep(p) {
        margin-bottom: 16px;
      }
      
      :deep(img) {
        max-width: 100%;
        border-radius: 6px;
      }
    }
    
    .post-tags {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      
      .tags-label {
        font-weight: 500;
        color: #303133;
      }
      
      .tag-item {
        margin-right: 8px;
      }
    }
    
    .stats-row {
      margin: 24px 0;
    }
    
    .comment-list {
      .comment-item {
        padding: 16px 0;
        border-bottom: 1px solid #f0f0f0;
        
        &:last-child {
          border-bottom: none;
        }
        
        .comment-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 12px;
          
          .commenter-info {
            display: flex;
            align-items: center;
            gap: 8px;
            
            .commenter-details {
              .username {
                font-weight: 500;
                color: #303133;
                margin-right: 12px;
              }
              
              .comment-time {
                font-size: 12px;
                color: #909399;
              }
            }
          }
        }
        
        .comment-content {
          margin-bottom: 12px;
          line-height: 1.6;
          color: #606266;
        }
        
        .comment-actions {
          display: flex;
          gap: 8px;
        }
      }
    }
    
    .empty-state {
      padding: 40px 0;
    }
  }
  
  .loading-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 200px;
    gap: 16px;
    color: #909399;
  }
}

@media (max-width: 768px) {
  .post-detail {
    .card-header {
      flex-direction: column;
      gap: 12px;
      align-items: flex-start;
    }
    
    .post-meta {
      flex-direction: column;
      gap: 12px;
      align-items: flex-start;
    }
  }
}
</style>
