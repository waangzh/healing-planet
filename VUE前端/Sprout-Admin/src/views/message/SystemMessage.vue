<template>
  <div class="system-message">
    <div class="page-header">
      <h2 class="page-title">系统消息</h2>
    </div>
    
    <div class="admin-card">
      <div class="card-body">
        <!-- 操作栏 -->
        <div class="action-bar">
          <el-button type="primary" @click="createMessage">
            <el-icon><Plus /></el-icon>
            发送系统消息
          </el-button>
          <div class="search-box">
            <el-input
              v-model="searchForm.keyword"
              placeholder="搜索消息标题或内容"
              clearable
              @keyup.enter="searchMessages"
              style="width: 300px"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select v-model="searchForm.type" placeholder="消息类型" clearable style="width: 150px; margin-left: 12px">
              <el-option label="通知" value="notification" />
              <el-option label="公告" value="announcement" />
              <el-option label="更新" value="update" />
              <el-option label="维护" value="maintenance" />
            </el-select>
            <el-button type="primary" @click="searchMessages" style="margin-left: 12px">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </div>
        </div>
        
        <!-- 统计卡片 -->
        <div class="stats-row">
          <el-row :gutter="16">
            <el-col :xs="12" :sm="6">
              <div class="stat-card primary">
                <div class="stat-icon">
                  <el-icon><Bell /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.total }}</div>
                  <div class="stat-label">总消息数</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card success">
                <div class="stat-icon">
                  <el-icon><Check /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.sent }}</div>
                  <div class="stat-label">已发送</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card warning">
                <div class="stat-icon">
                  <el-icon><Clock /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.scheduled }}</div>
                  <div class="stat-label">定时发送</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card info">
                <div class="stat-icon">
                  <el-icon><View /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.readRate }}%</div>
                  <div class="stat-label">阅读率</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <!-- 消息列表 -->
        <div class="message-table">
          <el-table :data="messages" v-loading="loading" stripe>
            <el-table-column prop="title" label="标题" min-width="200">
              <template #default="{ row }">
                <div class="message-title">
                  <el-icon class="type-icon" :class="getTypeIconClass(row.type)">
                    <component :is="getTypeIcon(row.type)" />
                  </el-icon>
                  <span>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getTypeTagType(row.type)" size="small">
                  {{ getTypeText(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="targetType" label="发送对象" width="120">
              <template #default="{ row }">
                <el-tag :type="getTargetTagType(row.targetType)" size="small">
                  {{ getTargetText(row.targetType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="readCount" label="阅读统计" width="120">
              <template #default="{ row }">
                <div class="read-stats">
                  <span>{{ row.readCount }}/{{ row.totalCount }}</span>
                  <el-progress 
                    :percentage="Math.round((row.readCount / row.totalCount) * 100)" 
                    size="small" 
                    :show-text="false"
                    style="margin-top: 4px"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusTagType(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sendTime" label="发送时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.sendTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="text" size="small" @click="viewMessage(row)">
                  详情
                </el-button>
                <el-button 
                  v-if="row.status === 'draft'"
                  type="text" 
                  size="small" 
                  @click="editMessage(row)"
                >
                  编辑
                </el-button>
                <el-button 
                  v-if="row.status === 'draft'"
                  type="text" 
                  size="small" 
                  @click="sendMessage(row)"
                >
                  发送
                </el-button>
                <el-button 
                  type="text" 
                  size="small" 
                  @click="deleteMessage(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchMessages"
            @current-change="fetchMessages"
          />
        </div>
      </div>
    </div>
    
    <!-- 创建/编辑消息对话框 -->
    <el-dialog
      v-model="messageDialog.visible"
      :title="messageDialog.mode === 'create' ? '发送系统消息' : '编辑系统消息'"
      width="800px"
      destroy-on-close
    >
      <el-form
        ref="messageFormRef"
        :model="messageDialog.form"
        :rules="messageFormRules"
        label-width="100px"
      >
        <el-form-item label="消息类型" prop="type">
          <el-select v-model="messageDialog.form.type" placeholder="请选择消息类型">
            <el-option label="通知" value="notification" />
            <el-option label="公告" value="announcement" />
            <el-option label="更新" value="update" />
            <el-option label="维护" value="maintenance" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="消息标题" prop="title">
          <el-input 
            v-model="messageDialog.form.title" 
            placeholder="请输入消息标题"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        
        <el-form-item label="消息内容" prop="content">
          <el-input
            v-model="messageDialog.form.content"
            type="textarea"
            :rows="8"
            placeholder="请输入消息内容"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        
        <el-form-item label="发送对象" prop="targetType">
          <el-select v-model="messageDialog.form.targetType" placeholder="请选择发送对象">
            <el-option label="全部用户" value="all" />
            <el-option label="活跃用户" value="active" />
            <el-option label="新用户" value="new" />
            <el-option label="指定用户" value="specific" />
          </el-select>
        </el-form-item>
        
        <el-form-item 
          v-if="messageDialog.form.targetType === 'specific'"
          label="指定用户" 
          prop="targetUsers"
        >
          <el-input
            v-model="messageDialog.form.targetUsers"
            placeholder="请输入用户ID，多个用户用逗号分隔"
          />
        </el-form-item>
        
        <el-form-item label="发送方式" prop="sendType">
          <el-radio-group v-model="messageDialog.form.sendType">
            <el-radio label="immediate">立即发送</el-radio>
            <el-radio label="scheduled">定时发送</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item 
          v-if="messageDialog.form.sendType === 'scheduled'"
          label="发送时间" 
          prop="scheduledTime"
        >
          <el-date-picker
            v-model="messageDialog.form.scheduledTime"
            type="datetime"
            placeholder="选择发送时间"
            :disabled-date="(time) => time.getTime() < Date.now()"
          />
        </el-form-item>
        
        <el-form-item label="重要级别" prop="priority">
          <el-select v-model="messageDialog.form.priority" placeholder="请选择重要级别">
            <el-option label="普通" value="normal" />
            <el-option label="重要" value="important" />
            <el-option label="紧急" value="urgent" />
          </el-select>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="messageDialog.visible = false">取消</el-button>
          <el-button @click="saveAsDraft">保存草稿</el-button>
          <el-button type="primary" @click="sendSystemMessage">
            {{ messageDialog.form.sendType === 'immediate' ? '立即发送' : '定时发送' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 消息详情对话框 -->
    <el-dialog
      v-model="detailDialog.visible"
      title="消息详情"
      width="700px"
      destroy-on-close
    >
      <div v-if="detailDialog.message" class="message-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="消息标题" :span="2">
            <div class="detail-title">
              <el-icon class="type-icon" :class="getTypeIconClass(detailDialog.message.type)">
                <component :is="getTypeIcon(detailDialog.message.type)" />
              </el-icon>
              <span>{{ detailDialog.message.title }}</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="消息类型">
            <el-tag :type="getTypeTagType(detailDialog.message.type)">
              {{ getTypeText(detailDialog.message.type) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发送对象">
            <el-tag :type="getTargetTagType(detailDialog.message.targetType)">
              {{ getTargetText(detailDialog.message.targetType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发送时间">
            {{ formatDateTime(detailDialog.message.sendTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(detailDialog.message.status)">
              {{ getStatusText(detailDialog.message.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="阅读统计" :span="2">
            <div class="read-detail">
              <span>{{ detailDialog.message.readCount }}/{{ detailDialog.message.totalCount }} 用户已阅读</span>
              <el-progress 
                :percentage="Math.round((detailDialog.message.readCount / detailDialog.message.totalCount) * 100)" 
                style="margin-top: 8px"
              />
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="消息内容" :span="2">
            <div class="content-box">
              {{ detailDialog.message.content }}
            </div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Plus,
  Search,
  Bell,
  Check,
  Clock,
  View,
  Notification,
  ChatDotRound,
  Refresh,
  Tools
} from '@element-plus/icons-vue'

// 响应式数据
const messages = ref([])
const loading = ref(false)
const messageFormRef = ref()

const searchForm = reactive({
  keyword: '',
  type: ''
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const stats = reactive({
  total: 0,
  sent: 0,
  scheduled: 0,
  readRate: 0
})

const messageDialog = reactive({
  visible: false,
  mode: 'create',
  form: {
    type: '',
    title: '',
    content: '',
    targetType: '',
    targetUsers: '',
    sendType: 'immediate',
    scheduledTime: null,
    priority: 'normal'
  }
})

const detailDialog = reactive({
  visible: false,
  message: null
})

// 表单验证规则
const messageFormRules = {
  type: [
    { required: true, message: '请选择消息类型', trigger: 'change' }
  ],
  title: [
    { required: true, message: '请输入消息标题', trigger: 'blur' },
    { min: 1, max: 100, message: '标题长度为1-100字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入消息内容', trigger: 'blur' },
    { min: 1, max: 2000, message: '内容长度为1-2000字符', trigger: 'blur' }
  ],
  targetType: [
    { required: true, message: '请选择发送对象', trigger: 'change' }
  ],
  targetUsers: [
    { required: true, message: '请输入目标用户ID', trigger: 'blur' }
  ],
  scheduledTime: [
    { required: true, message: '请选择发送时间', trigger: 'change' }
  ]
}

// 方法
const getTypeIcon = (type) => {
  const iconMap = {
    notification: Notification,
    announcement: ChatDotRound,
    update: Refresh,
    maintenance: Tools
  }
  return iconMap[type] || Bell
}

const getTypeIconClass = (type) => {
  const classMap = {
    notification: 'notification-icon',
    announcement: 'announcement-icon',
    update: 'update-icon',
    maintenance: 'maintenance-icon'
  }
  return classMap[type] || ''
}

const getTypeTagType = (type) => {
  const typeMap = {
    notification: 'primary',
    announcement: 'success',
    update: 'warning',
    maintenance: 'danger'
  }
  return typeMap[type] || 'info'
}

const getTypeText = (type) => {
  const textMap = {
    notification: '通知',
    announcement: '公告',
    update: '更新',
    maintenance: '维护'
  }
  return textMap[type] || '未知'
}

const getTargetTagType = (targetType) => {
  const typeMap = {
    all: 'primary',
    active: 'success',
    new: 'warning',
    specific: 'info'
  }
  return typeMap[targetType] || 'info'
}

const getTargetText = (targetType) => {
  const textMap = {
    all: '全部用户',
    active: '活跃用户',
    new: '新用户',
    specific: '指定用户'
  }
  return textMap[targetType] || '未知'
}

const getStatusTagType = (status) => {
  const typeMap = {
    draft: 'info',
    sent: 'success',
    scheduled: 'warning',
    failed: 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    draft: '草稿',
    sent: '已发送',
    scheduled: '定时发送',
    failed: '发送失败'
  }
  return textMap[status] || '未知'
}

const formatDateTime = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const fetchMessages = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))
    
    const mockMessages = Array.from({ length: 50 }, (_, index) => {
      const types = ['notification', 'announcement', 'update', 'maintenance']
      const targetTypes = ['all', 'active', 'new', 'specific']
      const statuses = ['draft', 'sent', 'scheduled', 'failed']
      
      const type = types[index % types.length]
      const targetType = targetTypes[index % targetTypes.length]
      const status = statuses[index % statuses.length]
      const totalCount = Math.floor(Math.random() * 1000) + 100
      const readCount = Math.floor(totalCount * Math.random())
      
      return {
        id: index + 1,
        type,
        title: `${getTypeText(type)}消息${index + 1}`,
        content: `这是一条${getTypeText(type)}消息的详细内容，用于向用户传达重要信息...`,
        targetType,
        targetUsers: targetType === 'specific' ? '1,2,3,4,5' : null,
        sendType: status === 'scheduled' ? 'scheduled' : 'immediate',
        scheduledTime: status === 'scheduled' ? 
          new Date(Date.now() + Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString() : null,
        priority: ['normal', 'important', 'urgent'][index % 3],
        status,
        totalCount,
        readCount,
        sendTime: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
        createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
      }
    })
    
    // 应用搜索过滤
    let filteredMessages = mockMessages
    if (searchForm.keyword) {
      filteredMessages = filteredMessages.filter(msg => 
        msg.title.includes(searchForm.keyword) ||
        msg.content.includes(searchForm.keyword)
      )
    }
    if (searchForm.type) {
      filteredMessages = filteredMessages.filter(msg => 
        msg.type === searchForm.type
      )
    }
    
    // 分页
    const start = (pagination.current - 1) * pagination.size
    const end = start + pagination.size
    
    messages.value = filteredMessages.slice(start, end)
    pagination.total = filteredMessages.length
    
    // 更新统计
    stats.total = mockMessages.length
    stats.sent = mockMessages.filter(m => m.status === 'sent').length
    stats.scheduled = mockMessages.filter(m => m.status === 'scheduled').length
    const totalRead = mockMessages.reduce((sum, m) => sum + m.readCount, 0)
    const totalUsers = mockMessages.reduce((sum, m) => sum + m.totalCount, 0)
    stats.readRate = totalUsers > 0 ? Math.round((totalRead / totalUsers) * 100) : 0
    
  } catch (error) {
    ElMessage.error('获取系统消息失败')
  } finally {
    loading.value = false
  }
}

const searchMessages = () => {
  pagination.current = 1
  fetchMessages()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.type = ''
  pagination.current = 1
  fetchMessages()
}

const createMessage = () => {
  messageDialog.mode = 'create'
  messageDialog.form = {
    type: '',
    title: '',
    content: '',
    targetType: '',
    targetUsers: '',
    sendType: 'immediate',
    scheduledTime: null,
    priority: 'normal'
  }
  messageDialog.visible = true
}

const editMessage = (message) => {
  messageDialog.mode = 'edit'
  messageDialog.form = {
    id: message.id,
    type: message.type,
    title: message.title,
    content: message.content,
    targetType: message.targetType,
    targetUsers: message.targetUsers || '',
    sendType: message.sendType,
    scheduledTime: message.scheduledTime ? new Date(message.scheduledTime) : null,
    priority: message.priority
  }
  messageDialog.visible = true
}

const viewMessage = (message) => {
  detailDialog.message = message
  detailDialog.visible = true
}

const saveAsDraft = async () => {
  if (!messageFormRef.value) return
  
  try {
    await messageFormRef.value.validate()
    ElMessage.success('草稿保存成功')
    messageDialog.visible = false
    fetchMessages()
  } catch {
    // 验证失败
  }
}

const sendSystemMessage = async () => {
  if (!messageFormRef.value) return
  
  try {
    await messageFormRef.value.validate()
    
    if (messageDialog.form.sendType === 'scheduled' && !messageDialog.form.scheduledTime) {
      ElMessage.error('请选择发送时间')
      return
    }
    
    if (messageDialog.form.targetType === 'specific' && !messageDialog.form.targetUsers) {
      ElMessage.error('请输入目标用户ID')
      return
    }
    
    ElMessage.success('系统消息发送成功')
    messageDialog.visible = false
    fetchMessages()
    
  } catch {
    // 验证失败
  }
}

const sendMessage = async (message) => {
  try {
    await ElMessageBox.confirm(
      '确定要发送这条系统消息吗？',
      '确认发送',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )
    
    ElMessage.success('消息发送成功')
    fetchMessages()
    
  } catch {
    // 用户取消操作
  }
}

const deleteMessage = async (message) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条系统消息吗？此操作不可恢复！',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    ElMessage.success('消息删除成功')
    fetchMessages()
    
  } catch {
    // 用户取消操作
  }
}

// 生命周期
onMounted(() => {
  fetchMessages()
})
</script>

<style lang="scss" scoped>
.system-message {
  .action-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    .search-box {
      display: flex;
      align-items: center;
    }
  }
  
  .stats-row {
    margin-bottom: 24px;
  }
  
  .message-table {
    margin-bottom: 24px;
    
    .message-title {
      display: flex;
      align-items: center;
      gap: 8px;
      
      .type-icon {
        &.notification-icon {
          color: #409eff;
        }
        &.announcement-icon {
          color: #67c23a;
        }
        &.update-icon {
          color: #e6a23c;
        }
        &.maintenance-icon {
          color: #f56c6c;
        }
      }
    }
    
    .read-stats {
      font-size: 12px;
      color: #606266;
    }
  }
  
  .pagination-wrapper {
    display: flex;
    justify-content: center;
  }
  
  .message-detail {
    .detail-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
      
      .type-icon {
        &.notification-icon {
          color: #409eff;
        }
        &.announcement-icon {
          color: #67c23a;
        }
        &.update-icon {
          color: #e6a23c;
        }
        &.maintenance-icon {
          color: #f56c6c;
        }
      }
    }
    
    .read-detail {
      font-size: 14px;
      color: #606266;
    }
    
    .content-box {
      background: #f8f9fa;
      padding: 16px;
      border-radius: 8px;
      line-height: 1.6;
      color: #606266;
      border-left: 4px solid #409eff;
    }
  }
}

@media (max-width: 768px) {
  .system-message {
    .action-bar {
      flex-direction: column;
      gap: 16px;
      align-items: stretch;
      
      .search-box {
        flex-direction: column;
        gap: 12px;
        
        .el-input,
        .el-select {
          width: 100% !important;
        }
      }
    }
  }
}
</style>
