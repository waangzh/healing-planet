<template>
  <div class="comment-management">
    <div class="page-title">评论管理</div>
    
    <!-- 搜索和操作区域 -->
    <div class="admin-card">
      <div class="card-body">
        <div class="table-actions">
          <div class="search-area">
            <el-input
              v-model="searchQuery"
              placeholder="搜索评论内容、作者..."
              :prefix-icon="Search"
              class="search-box"
              clearable
              @input="handleSearch"
            />
            <el-select
              v-model="statusFilter"
              placeholder="评论状态"
              style="width: 120px; margin-left: 12px"
              @change="handleFilter"
            >
              <el-option label="全部" value="" />
              <el-option label="正常" value="normal" />
              <el-option label="待审核" value="pending" />
              <el-option label="已删除" value="deleted" />
              <el-option label="已举报" value="reported" />
            </el-select>
          </div>
          <div class="action-buttons">
            <el-button 
              type="success" 
              :icon="Check" 
              :disabled="selectedComments.length === 0"
              @click="batchApprove"
            >
              批量审核通过
            </el-button>
            <el-button 
              type="danger" 
              :icon="Delete" 
              :disabled="selectedComments.length === 0"
              @click="batchDelete"
            >
              批量删除
            </el-button>
            <el-button :icon="Refresh" @click="refreshData">
              刷新
            </el-button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 评论列表 -->
    <div class="admin-card">
      <div class="card-body">
        <el-table
          v-loading="loading"
          :data="filteredComments"
          style="width: 100%"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55" />
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="评论内容" min-width="300">
            <template #default="{ row }">
              <div class="comment-content">
                <div class="content-text">{{ row.content }}</div>
                <div v-if="row.images && row.images.length" class="content-images">
                  <el-image
                    v-for="(image, index) in row.images.slice(0, 3)"
                    :key="index"
                    :src="image"
                    fit="cover"
                    style="width: 40px; height: 40px; margin-right: 8px; border-radius: 4px;"
                  />
                  <span v-if="row.images.length > 3" class="more-images">
                    +{{ row.images.length - 3 }}
                  </span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="作者" width="150">
            <template #default="{ row }">
              <div class="author-info">
                <el-avatar :src="row.author.avatar" :size="30" />
                <span class="author-name">{{ row.author.username }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="所属帖子" width="200">
            <template #default="{ row }">
              <div class="post-info">
                <div class="post-title">{{ row.post.title }}</div>
                <div class="post-author">作者: {{ row.post.author }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="统计" width="100">
            <template #default="{ row }">
              <div class="comment-stats">
                <div>点赞: {{ row.likeCount }}</div>
                <div>回复: {{ row.replyCount }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="发布时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button
                  type="primary"
                  size="small"
                  @click="viewComment(row)"
                >
                  查看
                </el-button>
                <el-button
                  v-if="row.status === 'pending'"
                  type="success"
                  size="small"
                  @click="approveComment(row)"
                >
                  通过
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  @click="deleteComment(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>
    
    <!-- 评论详情抽屉 -->
    <el-drawer
      v-model="commentDetailDrawer"
      title="评论详情"
      size="600px"
    >
      <div v-if="selectedComment" class="comment-detail-content">
        <div class="detail-section">
          <h4>评论信息</h4>
          <div class="comment-meta">
            <div class="author-info">
              <el-avatar :src="selectedComment.author.avatar" :size="40" />
              <div class="author-details">
                <div class="username">{{ selectedComment.author.username }}</div>
                <div class="user-email">{{ selectedComment.author.email }}</div>
              </div>
            </div>
            <el-tag :type="getStatusType(selectedComment.status)">
              {{ getStatusText(selectedComment.status) }}
            </el-tag>
          </div>
        </div>
        
        <div class="detail-section">
          <h4>评论内容</h4>
          <div class="comment-content-detail">
            <p>{{ selectedComment.content }}</p>
            <div v-if="selectedComment.images && selectedComment.images.length" class="comment-images">
              <el-image
                v-for="(image, index) in selectedComment.images"
                :key="index"
                :src="image"
                fit="cover"
                style="width: 100px; height: 100px; margin-right: 8px; border-radius: 4px;"
                :preview-src-list="selectedComment.images"
                :initial-index="index"
              />
            </div>
          </div>
        </div>
        
        <div class="detail-section">
          <h4>所属帖子</h4>
          <div class="post-detail">
            <div class="post-title">{{ selectedComment.post.title }}</div>
            <div class="post-meta">
              <span>作者: {{ selectedComment.post.author }}</span>
              <span>发布时间: {{ formatDate(selectedComment.post.createdAt) }}</span>
            </div>
          </div>
        </div>
        
        <div class="detail-section">
          <h4>统计信息</h4>
          <el-row :gutter="16">
            <el-col :span="12">
              <div class="stat-item">
                <div class="stat-number">{{ selectedComment.likeCount }}</div>
                <div class="stat-label">点赞数</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="stat-item">
                <div class="stat-number">{{ selectedComment.replyCount }}</div>
                <div class="stat-label">回复数</div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <div class="detail-actions">
          <el-button
            v-if="selectedComment.status === 'pending'"
            type="success"
            @click="approveComment(selectedComment)"
          >
            审核通过
          </el-button>
          <el-button type="danger" @click="deleteComment(selectedComment)">
            删除评论
          </el-button>
          <el-button @click="viewPost(selectedComment.post.id)">
            查看原帖
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Check, Delete, Refresh } from '@element-plus/icons-vue'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const searchQuery = ref('')
const statusFilter = ref('')
const commentDetailDrawer = ref(false)
const selectedComment = ref(null)

// 分页数据
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 评论列表数据
const comments = ref([])
const selectedComments = ref([])

// 计算属性
const filteredComments = computed(() => {
  let result = comments.value
  
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(comment => 
      comment.content.toLowerCase().includes(query) ||
      comment.author.username.toLowerCase().includes(query)
    )
  }
  
  if (statusFilter.value) {
    result = result.filter(comment => comment.status === statusFilter.value)
  }
  
  return result
})

// 方法
const handleSearch = () => {
  // 搜索逻辑
}

const handleFilter = () => {
  // 筛选逻辑
}

const handleSelectionChange = (selection) => {
  selectedComments.value = selection
}

const handleSizeChange = (size) => {
  pagination.size = size
  fetchComments()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchComments()
}

const refreshData = () => {
  fetchComments()
}

const viewComment = (comment) => {
  selectedComment.value = comment
  commentDetailDrawer.value = true
}

const viewPost = (postId) => {
  router.push(`/posts/${postId}`)
}

const approveComment = async (comment) => {
  try {
    await ElMessageBox.confirm(
      `确定要审核通过该评论吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )
    
    // 模拟API调用
    comment.status = 'normal'
    ElMessage.success('审核通过')
    
  } catch {
    // 用户取消操作
  }
}

const deleteComment = async (comment) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除该评论吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    // 模拟API调用
    const index = comments.value.findIndex(c => c.id === comment.id)
    if (index !== -1) {
      comments.value.splice(index, 1)
    }
    ElMessage.success('删除成功')
    
    if (commentDetailDrawer.value) {
      commentDetailDrawer.value = false
    }
    
  } catch {
    // 用户取消操作
  }
}

const batchApprove = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要批量审核通过选中的 ${selectedComments.value.length} 个评论吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )
    
    // 模拟API调用
    selectedComments.value.forEach(comment => {
      if (comment.status === 'pending') {
        comment.status = 'normal'
      }
    })
    
    ElMessage.success(`批量审核通过 ${selectedComments.value.length} 个评论`)
    selectedComments.value = []
    
  } catch {
    // 用户取消操作
  }
}

const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要批量删除选中的 ${selectedComments.value.length} 个评论吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    // 模拟API调用
    const deleteIds = selectedComments.value.map(c => c.id)
    comments.value = comments.value.filter(c => !deleteIds.includes(c.id))
    
    ElMessage.success(`批量删除 ${selectedComments.value.length} 个评论`)
    selectedComments.value = []
    
  } catch {
    // 用户取消操作
  }
}

const getStatusType = (status) => {
  const statusMap = {
    normal: 'success',
    pending: 'warning',
    deleted: 'info',
    reported: 'danger'
  }
  return statusMap[status] || ''
}

const getStatusText = (status) => {
  const statusMap = {
    normal: '正常',
    pending: '待审核',
    deleted: '已删除',
    reported: '已举报'
  }
  return statusMap[status] || '未知'
}

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

// 模拟获取评论数据
const fetchComments = async () => {
  loading.value = true
  
  try {
    // 模拟API调用
    const mockComments = generateMockComments()
    comments.value = mockComments
    pagination.total = mockComments.length
  } catch (error) {
    ElMessage.error('获取评论列表失败')
  } finally {
    loading.value = false
  }
}

// 生成模拟评论数据
const generateMockComments = () => {
  const statuses = ['normal', 'pending', 'reported']
  
  return Array.from({ length: 50 }, (_, index) => ({
    id: index + 1,
    content: `这是评论${index + 1}的内容，包含了用户的想法和观点。可能会有一些关于植物养护的交流和讨论。`,
    images: Math.random() > 0.7 ? Array.from({ length: Math.floor(Math.random() * 3) + 1 }, (_, i) => `https://picsum.photos/200/200?random=${index * 10 + i}`) : null,
    status: statuses[index % statuses.length],
    author: {
      id: index + 1,
      username: `user${index + 1}`,
      email: `user${index + 1}@example.com`,
      avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${index}`
    },
    post: {
      id: Math.floor(index / 3) + 1,
      title: `帖子标题${Math.floor(index / 3) + 1}`,
      author: `author${Math.floor(index / 3) + 1}`,
      createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
    },
    likeCount: Math.floor(Math.random() * 50),
    replyCount: Math.floor(Math.random() * 10),
    createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
  }))
}

// 生命周期
onMounted(() => {
  fetchComments()
})
</script>

<style lang="scss" scoped>
.comment-management {
  .table-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    
    .search-area {
      display: flex;
      align-items: center;
    }
    
    .search-box {
      width: 300px;
    }
  }
  
  .comment-content {
    .content-text {
      margin-bottom: 8px;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    
    .content-images {
      display: flex;
      align-items: center;
      
      .more-images {
        font-size: 12px;
        color: #909399;
      }
    }
  }
  
  .author-info {
    display: flex;
    align-items: center;
    gap: 8px;
    
    .author-name {
      font-size: 14px;
      color: #303133;
    }
  }
  
  .post-info {
    .post-title {
      font-weight: 500;
      color: #303133;
      margin-bottom: 4px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    
    .post-author {
      font-size: 12px;
      color: #909399;
    }
  }
  
  .comment-stats {
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
  }
  
  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: center;
  }
  
  .comment-detail-content {
    .detail-section {
      margin-bottom: 24px;
      
      h4 {
        margin-bottom: 12px;
        font-size: 16px;
        font-weight: 600;
        color: #303133;
      }
      
      .comment-meta {
        display: flex;
        justify-content: space-between;
        align-items: center;
        
        .author-info {
          display: flex;
          align-items: center;
          gap: 12px;
          
          .author-details {
            .username {
              font-weight: 500;
              color: #303133;
            }
            
            .user-email {
              font-size: 12px;
              color: #909399;
            }
          }
        }
      }
      
      .comment-content-detail {
        p {
          line-height: 1.6;
          margin-bottom: 16px;
          color: #606266;
        }
        
        .comment-images {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
        }
      }
      
      .post-detail {
        padding: 16px;
        background: #f8f9fa;
        border-radius: 6px;
        
        .post-title {
          font-weight: 500;
          color: #303133;
          margin-bottom: 8px;
        }
        
        .post-meta {
          font-size: 12px;
          color: #909399;
          display: flex;
          gap: 16px;
        }
      }
    }
    
    .stat-item {
      text-align: center;
      padding: 16px;
      background: #f5f7fa;
      border-radius: 6px;
      
      .stat-number {
        font-size: 24px;
        font-weight: 600;
        color: #409eff;
        margin-bottom: 4px;
      }
      
      .stat-label {
        font-size: 12px;
        color: #909399;
      }
    }
    
    .detail-actions {
      margin-top: 24px;
      padding-top: 24px;
      border-top: 1px solid #ebeef5;
      display: flex;
      gap: 12px;
    }
  }
}
</style>
