<template>
  <div class="user-detail">
    <div class="page-title">
      <el-button :icon="ArrowLeft" @click="$router.go(-1)">返回</el-button>
      <span>用户详情</span>
    </div>
    
    <div v-if="userInfo" class="user-detail-content">
      <!-- 用户基本信息 -->
      <div class="admin-card">
        <div class="card-header">
          <h3 class="card-title">基本信息</h3>
          <div class="card-actions">
            <el-button type="primary" size="small" @click="editUser">编辑用户</el-button>
            <el-button 
              :type="userInfo.status === 'active' ? 'warning' : 'success'" 
              size="small" 
              @click="toggleUserStatus"
            >
              {{ userInfo.status === 'active' ? '禁用用户' : '启用用户' }}
            </el-button>
          </div>
        </div>
        <div class="card-body">
          <el-row :gutter="24">
            <el-col :xs="24" :md="8">
              <div class="user-avatar-section">
                <el-avatar :src="userInfo.avatar" :size="120" />
                <div class="user-basic">
                  <h2>{{ userInfo.username }}</h2>
                  <p>{{ userInfo.email }}</p>
                  <el-tag :type="getRoleType(userInfo.role)">
                    {{ getRoleText(userInfo.role) }}
                  </el-tag>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :md="16">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="用户ID">{{ userInfo.id }}</el-descriptions-item>
                <el-descriptions-item label="真实姓名">{{ userInfo.realName || '未设置' }}</el-descriptions-item>
                <el-descriptions-item label="手机号">{{ userInfo.phone || '未设置' }}</el-descriptions-item>
                <el-descriptions-item label="注册时间">{{ formatDate(userInfo.createdAt) }}</el-descriptions-item>
                <el-descriptions-item label="最后登录">{{ formatDate(userInfo.lastLoginAt) }}</el-descriptions-item>
                <el-descriptions-item label="登录次数">{{ userInfo.loginCount }}</el-descriptions-item>
                <el-descriptions-item label="账号状态">
                  <el-tag :type="userInfo.status === 'active' ? 'success' : 'danger'">
                    {{ userInfo.status === 'active' ? '正常' : '禁用' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="违规次数">{{ userInfo.violationCount }}</el-descriptions-item>
              </el-descriptions>
            </el-col>
          </el-row>
        </div>
      </div>
      
      <!-- 统计数据 -->
      <el-row :gutter="24" class="stats-row">
        <el-col :xs="12" :sm="6">
          <div class="stat-card primary">
            <el-icon class="stat-icon"><Document /></el-icon>
            <div class="stat-number">{{ userInfo.postCount }}</div>
            <div class="stat-label">发布帖子</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card success">
            <el-icon class="stat-icon"><User /></el-icon>
            <div class="stat-number">{{ userInfo.followerCount }}</div>
            <div class="stat-label">粉丝数量</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card warning">
            <el-icon class="stat-icon"><UserFilled /></el-icon>
            <div class="stat-number">{{ userInfo.followingCount }}</div>
            <div class="stat-label">关注数量</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card danger">
            <el-icon class="stat-icon"><Star /></el-icon>
            <div class="stat-number">{{ userInfo.likeCount }}</div>
            <div class="stat-label">获赞数量</div>
          </div>
        </el-col>
      </el-row>
      
      <!-- 用户活动 -->
      <el-row :gutter="24">
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3 class="card-title">最近发布的帖子</h3>
            </div>
            <div class="card-body">
              <div v-if="userPosts.length === 0" class="empty-state">
                <el-empty description="暂无帖子" />
              </div>
              <div v-else class="post-list">
                <div v-for="post in userPosts" :key="post.id" class="post-item">
                  <div class="post-title">{{ post.title }}</div>
                  <div class="post-meta">
                    <span>{{ formatDate(post.createdAt) }}</span>
                    <span>{{ post.viewCount }} 浏览</span>
                    <span>{{ post.likeCount }} 点赞</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-col>
        
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3 class="card-title">最近评论</h3>
            </div>
            <div class="card-body">
              <div v-if="userComments.length === 0" class="empty-state">
                <el-empty description="暂无评论" />
              </div>
              <div v-else class="comment-list">
                <div v-for="comment in userComments" :key="comment.id" class="comment-item">
                  <div class="comment-content">{{ comment.content }}</div>
                  <div class="comment-meta">
                    <span>{{ formatDate(comment.createdAt) }}</span>
                    <span>回复: {{ comment.postTitle }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
      
      <!-- 操作记录 -->
      <div class="admin-card">
        <div class="card-header">
          <h3 class="card-title">操作记录</h3>
        </div>
        <div class="card-body">
          <el-table :data="operationLogs" style="width: 100%">
            <el-table-column prop="action" label="操作" width="150" />
            <el-table-column prop="description" label="描述" />
            <el-table-column prop="ip" label="IP地址" width="140" />
            <el-table-column prop="userAgent" label="设备信息" width="200" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
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
  Document, 
  User, 
  UserFilled, 
  Star 
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 响应式数据
const userInfo = ref(null)
const userPosts = ref([])
const userComments = ref([])
const operationLogs = ref([])

// 方法
const getRoleType = (role) => {
  const roleMap = {
    user: '',
    vip: 'warning',
    admin: 'danger'
  }
  return roleMap[role] || ''
}

const getRoleText = (role) => {
  const roleMap = {
    user: '普通用户',
    vip: 'VIP用户',
    admin: '管理员'
  }
  return roleMap[role] || '未知'
}

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const editUser = () => {
  // 跳转到编辑页面或打开编辑对话框
  router.push(`/users/${userInfo.value.id}/edit`)
}

const toggleUserStatus = async () => {
  const action = userInfo.value.status === 'active' ? '禁用' : '启用'
  
  try {
    await ElMessageBox.confirm(
      `确定要${action}用户 "${userInfo.value.username}" 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 模拟API调用
    userInfo.value.status = userInfo.value.status === 'active' ? 'inactive' : 'active'
    ElMessage.success(`${action}成功`)
    
  } catch {
    // 用户取消操作
  }
}

// 获取用户详情
const fetchUserDetail = async () => {
  try {
    const userId = route.params.id
    
    // 模拟API调用
    userInfo.value = generateMockUserDetail(userId)
    userPosts.value = generateMockUserPosts(userId)
    userComments.value = generateMockUserComments(userId)
    operationLogs.value = generateMockOperationLogs(userId)
    
  } catch (error) {
    ElMessage.error('获取用户详情失败')
    router.go(-1)
  }
}

// 生成模拟用户详情数据
const generateMockUserDetail = (userId) => {
  return {
    id: userId,
    username: `user${userId}`,
    email: `user${userId}@example.com`,
    realName: `用户${userId}`,
    phone: `1380000${String(userId).padStart(4, '0')}`,
    avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${userId}`,
    role: 'user',
    status: 'active',
    postCount: Math.floor(Math.random() * 100),
    followerCount: Math.floor(Math.random() * 1000),
    followingCount: Math.floor(Math.random() * 500),
    likeCount: Math.floor(Math.random() * 2000),
    loginCount: Math.floor(Math.random() * 1000),
    violationCount: Math.floor(Math.random() * 5),
    createdAt: new Date(Date.now() - Math.random() * 365 * 24 * 60 * 60 * 1000).toISOString(),
    lastLoginAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString()
  }
}

// 生成模拟用户帖子数据
const generateMockUserPosts = (userId) => {
  return Array.from({ length: 5 }, (_, index) => ({
    id: index + 1,
    title: `用户${userId}的帖子${index + 1}`,
    viewCount: Math.floor(Math.random() * 1000),
    likeCount: Math.floor(Math.random() * 100),
    createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
  }))
}

// 生成模拟用户评论数据
const generateMockUserComments = (userId) => {
  return Array.from({ length: 5 }, (_, index) => ({
    id: index + 1,
    content: `这是用户${userId}的评论内容${index + 1}`,
    postTitle: `被评论的帖子${index + 1}`,
    createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
  }))
}

// 生成模拟操作记录数据
const generateMockOperationLogs = (userId) => {
  const actions = ['登录', '发布帖子', '评论', '点赞', '关注用户']
  
  return Array.from({ length: 10 }, (_, index) => ({
    id: index + 1,
    action: actions[index % actions.length],
    description: `用户${userId}执行了${actions[index % actions.length]}操作`,
    ip: `192.168.1.${Math.floor(Math.random() * 255)}`,
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    createdAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString()
  }))
}

// 生命周期
onMounted(() => {
  fetchUserDetail()
})
</script>

<style lang="scss" scoped>
.user-detail {
  .page-title {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    font-size: 24px;
    font-weight: 600;
    color: #303133;
  }
  
  .user-detail-content {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    
    .user-avatar-section {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      
      .user-basic {
        margin-top: 16px;
        
        h2 {
          margin: 0 0 8px 0;
          font-size: 20px;
          color: #303133;
        }
        
        p {
          margin: 0 0 12px 0;
          color: #909399;
        }
      }
    }
    
    .stats-row {
      margin: 24px 0;
    }
    
    .post-list,
    .comment-list {
      .post-item,
      .comment-item {
        padding: 12px 0;
        border-bottom: 1px solid #f0f0f0;
        
        &:last-child {
          border-bottom: none;
        }
        
        .post-title,
        .comment-content {
          font-size: 14px;
          color: #303133;
          margin-bottom: 8px;
          line-height: 1.4;
        }
        
        .comment-content {
          display: -webkit-box;
          -webkit-line-clamp: 2;
          line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }
        
        .post-meta,
        .comment-meta {
          font-size: 12px;
          color: #909399;
          display: flex;
          gap: 12px;
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
  .user-detail {
    .card-header {
      flex-direction: column;
      gap: 12px;
      align-items: flex-start;
    }
  }
}
</style>
