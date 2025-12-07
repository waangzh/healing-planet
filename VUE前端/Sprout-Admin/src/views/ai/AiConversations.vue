<template>
  <div class="ai-conversations">
    <div class="page-header">
      <h2 class="page-title">AI对话管理</h2>
    </div>
    
    <div class="admin-card">
      <div class="card-body">
        <!-- 搜索筛选 -->
        <div class="search-section">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="6">
              <el-input
                v-model="searchForm.keyword"
                placeholder="搜索用户名或对话内容"
                clearable
                @keyup.enter="searchConversations"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-select v-model="searchForm.type" placeholder="对话类型" clearable>
                <el-option label="植物咨询" value="plant_consultation" />
                <el-option label="养护建议" value="care_advice" />
                <el-option label="病虫害诊断" value="pest_diagnosis" />
                <el-option label="通用对话" value="general" />
              </el-select>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-date-picker
                v-model="searchForm.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-button type="primary" @click="searchConversations">搜索</el-button>
              <el-button @click="resetSearch">重置</el-button>
            </el-col>
          </el-row>
        </div>
        
        <!-- 统计卡片 -->
        <div class="stats-cards">
          <el-row :gutter="16">
            <el-col :xs="12" :sm="6">
              <div class="stat-card primary">
                <div class="stat-icon">
                  <el-icon><ChatLineRound /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.total }}</div>
                  <div class="stat-label">总对话数</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card success">
                <div class="stat-icon">
                  <el-icon><Check /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.satisfied }}</div>
                  <div class="stat-label">满意对话</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card warning">
                <div class="stat-icon">
                  <el-icon><Warning /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.unsatisfied }}</div>
                  <div class="stat-label">不满意对话</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card danger">
                <div class="stat-icon">
                  <el-icon><Close /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.failed }}</div>
                  <div class="stat-label">失败对话</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <!-- 对话列表 -->
        <div class="conversation-list">
          <div v-for="conversation in conversations" :key="conversation.id" class="conversation-item">
            <div class="conversation-header">
              <div class="user-info">
                <el-avatar :src="conversation.user.avatar" :size="40" />
                <div class="user-details">
                  <div class="username">{{ conversation.user.username }}</div>
                  <div class="conversation-time">{{ formatDateTime(conversation.createdAt) }}</div>
                </div>
              </div>
              <div class="conversation-meta">
                <el-tag :type="getTypeColor(conversation.type)" size="small">
                  {{ getTypeText(conversation.type) }}
                </el-tag>
                <el-tag 
                  v-if="conversation.satisfaction"
                  :type="getSatisfactionColor(conversation.satisfaction)"
                  size="small"
                >
                  {{ getSatisfactionText(conversation.satisfaction) }}
                </el-tag>
              </div>
            </div>
            
            <div class="conversation-content">
              <div class="messages">
                <div
                  v-for="message in conversation.messages.slice(0, 3)"
                  :key="message.id"
                  class="message"
                  :class="{ 'user-message': message.sender === 'user', 'ai-message': message.sender === 'ai' }"
                >
                  <div class="message-sender">
                    {{ message.sender === 'user' ? '用户' : 'AI助手' }}
                  </div>
                  <div class="message-content">{{ message.content }}</div>
                </div>
                <div v-if="conversation.messages.length > 3" class="more-messages">
                  还有 {{ conversation.messages.length - 3 }} 条消息...
                </div>
              </div>
            </div>
            
            <div class="conversation-footer">
              <div class="conversation-stats">
                <span class="stat-item">
                  <el-icon><Clock /></el-icon>
                  {{ conversation.duration }}s
                </span>
                <span class="stat-item">
                  <el-icon><ChatLineRound /></el-icon>
                  {{ conversation.messageCount }} 条消息
                </span>
              </div>
              <div class="conversation-actions">
                <el-button type="text" size="small" @click="viewConversation(conversation)">
                  查看详情
                </el-button>
                <el-button type="text" size="small" @click="exportConversation(conversation)">
                  导出
                </el-button>
                <el-button type="text" size="small" @click="deleteConversation(conversation)">
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchConversations"
            @current-change="fetchConversations"
          />
        </div>
      </div>
    </div>
    
    <!-- 对话详情对话框 -->
    <el-dialog
      v-model="detailDialog.visible"
      title="对话详情"
      width="800px"
      destroy-on-close
    >
      <div v-if="detailDialog.conversation" class="conversation-detail">
        <div class="detail-header">
          <div class="user-info">
            <el-avatar :src="detailDialog.conversation.user.avatar" :size="50" />
            <div class="user-details">
              <h4>{{ detailDialog.conversation.user.username }}</h4>
              <p>{{ formatDateTime(detailDialog.conversation.createdAt) }}</p>
            </div>
          </div>
          <div class="conversation-tags">
            <el-tag :type="getTypeColor(detailDialog.conversation.type)">
              {{ getTypeText(detailDialog.conversation.type) }}
            </el-tag>
            <el-tag 
              v-if="detailDialog.conversation.satisfaction"
              :type="getSatisfactionColor(detailDialog.conversation.satisfaction)"
            >
              {{ getSatisfactionText(detailDialog.conversation.satisfaction) }}
            </el-tag>
          </div>
        </div>
        
        <div class="messages-container">
          <div
            v-for="message in detailDialog.conversation.messages"
            :key="message.id"
            class="message"
            :class="{ 'user-message': message.sender === 'user', 'ai-message': message.sender === 'ai' }"
          >
            <div class="message-avatar">
              <el-avatar
                v-if="message.sender === 'user'"
                :src="detailDialog.conversation.user.avatar"
                :size="32"
              />
              <el-avatar v-else :size="32">
                <el-icon><Cpu /></el-icon>
              </el-avatar>
            </div>
            <div class="message-bubble">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-time">{{ formatTime(message.timestamp) }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Search,
  ChatLineRound,
  Check,
  Warning,
  Close,
  Clock,
  Cpu
} from '@element-plus/icons-vue'

// 响应式数据
const conversations = ref([])
const loading = ref(false)

const searchForm = reactive({
  keyword: '',
  type: '',
  dateRange: []
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const stats = reactive({
  total: 0,
  satisfied: 0,
  unsatisfied: 0,
  failed: 0
})

const detailDialog = reactive({
  visible: false,
  conversation: null
})

// 方法
const getTypeColor = (type) => {
  const colorMap = {
    plant_consultation: 'success',
    care_advice: 'primary',
    pest_diagnosis: 'warning',
    general: 'info'
  }
  return colorMap[type] || 'info'
}

const getTypeText = (type) => {
  const textMap = {
    plant_consultation: '植物咨询',
    care_advice: '养护建议',
    pest_diagnosis: '病虫害诊断',
    general: '通用对话'
  }
  return textMap[type] || '未知'
}

const getSatisfactionColor = (satisfaction) => {
  const colorMap = {
    satisfied: 'success',
    unsatisfied: 'warning',
    very_unsatisfied: 'danger'
  }
  return colorMap[satisfaction] || 'info'
}

const getSatisfactionText = (satisfaction) => {
  const textMap = {
    satisfied: '满意',
    unsatisfied: '不满意',
    very_unsatisfied: '非常不满意'
  }
  return textMap[satisfaction] || '未评价'
}

const formatDateTime = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const formatTime = (timestamp) => {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

const fetchConversations = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))
    
    const mockConversations = Array.from({ length: 50 }, (_, index) => {
      const types = ['plant_consultation', 'care_advice', 'pest_diagnosis', 'general']
      const satisfactions = ['satisfied', 'unsatisfied', 'very_unsatisfied', null]
      const type = types[index % types.length]
      const satisfaction = satisfactions[index % satisfactions.length]
      
      const messages = Array.from({ length: Math.floor(Math.random() * 8) + 3 }, (_, msgIndex) => ({
        id: msgIndex + 1,
        sender: msgIndex % 2 === 0 ? 'user' : 'ai',
        content: msgIndex % 2 === 0 
          ? `用户消息${msgIndex + 1}：我想了解关于植物养护的问题...` 
          : `AI回复${msgIndex + 1}：根据您的描述，我建议您...`,
        timestamp: new Date(Date.now() - (8 - msgIndex) * 2 * 60 * 1000).toISOString()
      }))
      
      return {
        id: index + 1,
        user: {
          id: Math.floor(Math.random() * 100) + 1,
          username: `user${index + 1}`,
          avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${index + 1}`
        },
        type,
        satisfaction,
        messages,
        messageCount: messages.length,
        duration: Math.floor(Math.random() * 300) + 60,
        createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
      }
    })
    
    // 应用搜索过滤
    let filteredConversations = mockConversations
    if (searchForm.keyword) {
      filteredConversations = filteredConversations.filter(conv => 
        conv.user.username.includes(searchForm.keyword) ||
        conv.messages.some(msg => msg.content.includes(searchForm.keyword))
      )
    }
    if (searchForm.type) {
      filteredConversations = filteredConversations.filter(conv => 
        conv.type === searchForm.type
      )
    }
    
    // 分页
    const start = (pagination.current - 1) * pagination.size
    const end = start + pagination.size
    
    conversations.value = filteredConversations.slice(start, end)
    pagination.total = filteredConversations.length
    
    // 更新统计
    stats.total = mockConversations.length
    stats.satisfied = mockConversations.filter(c => c.satisfaction === 'satisfied').length
    stats.unsatisfied = mockConversations.filter(c => c.satisfaction === 'unsatisfied').length
    stats.failed = mockConversations.filter(c => c.satisfaction === 'very_unsatisfied').length
    
  } catch (error) {
    ElMessage.error('获取对话记录失败')
  } finally {
    loading.value = false
  }
}

const searchConversations = () => {
  pagination.current = 1
  fetchConversations()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.type = ''
  searchForm.dateRange = []
  pagination.current = 1
  fetchConversations()
}

const viewConversation = (conversation) => {
  detailDialog.conversation = conversation
  detailDialog.visible = true
}

const exportConversation = (conversation) => {
  // 模拟导出功能
  const content = conversation.messages.map(msg => 
    `${msg.sender === 'user' ? '用户' : 'AI助手'}: ${msg.content}`
  ).join('\n\n')
  
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `conversation_${conversation.id}.txt`
  a.click()
  URL.revokeObjectURL(url)
  
  ElMessage.success('对话导出成功')
}

const deleteConversation = async (conversation) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条对话记录吗？',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    ElMessage.success('对话记录删除成功')
    fetchConversations()
    
  } catch {
    // 用户取消操作
  }
}

// 生命周期
onMounted(() => {
  fetchConversations()
})
</script>

<style lang="scss" scoped>
.ai-conversations {
  .search-section {
    margin-bottom: 24px;
  }
  
  .stats-cards {
    margin-bottom: 24px;
  }
  
  .conversation-list {
    margin-bottom: 24px;
    
    .conversation-item {
      background: white;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      padding: 20px;
      margin-bottom: 16px;
      transition: all 0.3s ease;
      
      &:hover {
        border-color: #409eff;
        box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
      }
      
      .conversation-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
        
        .user-info {
          display: flex;
          align-items: center;
          gap: 12px;
          
          .user-details {
            .username {
              font-weight: 600;
              color: #303133;
              margin-bottom: 4px;
            }
            
            .conversation-time {
              font-size: 12px;
              color: #909399;
            }
          }
        }
        
        .conversation-meta {
          display: flex;
          gap: 8px;
        }
      }
      
      .conversation-content {
        margin-bottom: 16px;
        
        .messages {
          .message {
            margin-bottom: 12px;
            padding: 12px;
            border-radius: 8px;
            
            &.user-message {
              background: #f0f9ff;
              border-left: 3px solid #409eff;
            }
            
            &.ai-message {
              background: #f0f9f0;
              border-left: 3px solid #67c23a;
            }
            
            .message-sender {
              font-size: 12px;
              font-weight: 600;
              color: #606266;
              margin-bottom: 4px;
            }
            
            .message-content {
              color: #303133;
              line-height: 1.6;
              overflow: hidden;
              text-overflow: ellipsis;
              display: -webkit-box;
              -webkit-line-clamp: 2;
              line-clamp: 2;
              -webkit-box-orient: vertical;
            }
          }
          
          .more-messages {
            text-align: center;
            color: #909399;
            font-size: 14px;
            padding: 8px;
          }
        }
      }
      
      .conversation-footer {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding-top: 16px;
        border-top: 1px solid #f0f0f0;
        
        .conversation-stats {
          display: flex;
          gap: 16px;
          
          .stat-item {
            display: flex;
            align-items: center;
            gap: 4px;
            font-size: 14px;
            color: #909399;
          }
        }
        
        .conversation-actions {
          display: flex;
          gap: 8px;
        }
      }
    }
  }
  
  .pagination-wrapper {
    display: flex;
    justify-content: center;
  }
  
  .conversation-detail {
    .detail-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 20px;
      border-bottom: 1px solid #f0f0f0;
      margin-bottom: 20px;
      
      .user-info {
        display: flex;
        align-items: center;
        gap: 16px;
        
        .user-details {
          h4 {
            margin: 0 0 4px 0;
            color: #303133;
          }
          
          p {
            margin: 0;
            font-size: 14px;
            color: #909399;
          }
        }
      }
      
      .conversation-tags {
        display: flex;
        gap: 8px;
      }
    }
    
    .messages-container {
      max-height: 400px;
      overflow-y: auto;
      
      .message {
        display: flex;
        gap: 12px;
        margin-bottom: 16px;
        
        &.ai-message {
          flex-direction: row-reverse;
          
          .message-bubble {
            background: #f0f9ff;
            border: 1px solid #409eff;
          }
        }
        
        .message-avatar {
          flex-shrink: 0;
        }
        
        .message-bubble {
          max-width: 60%;
          padding: 12px 16px;
          border-radius: 12px;
          background: #f8f9fa;
          border: 1px solid #e9ecef;
          
          .message-content {
            color: #303133;
            line-height: 1.6;
            margin-bottom: 4px;
          }
          
          .message-time {
            font-size: 12px;
            color: #909399;
          }
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .ai-conversations {
    .search-section {
      .el-col {
        margin-bottom: 12px;
      }
    }
    
    .conversation-item {
      .conversation-header {
        flex-direction: column;
        gap: 12px;
        align-items: flex-start;
      }
      
      .conversation-footer {
        flex-direction: column;
        gap: 12px;
        align-items: flex-start;
      }
    }
    
    .conversation-detail {
      .detail-header {
        flex-direction: column;
        gap: 16px;
        align-items: flex-start;
      }
    }
  }
}
</style>
