<template>
  <div class="message-management">
    <div class="page-header">
      <h2 class="page-title">私信管理</h2>
    </div>
    
    <div class="admin-card">
      <div class="card-body">
        <!-- 搜索筛选 -->
        <div class="search-section">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="6">
              <el-input
                v-model="searchForm.keyword"
                placeholder="搜索发送者或接收者"
                clearable
                @keyup.enter="searchMessages"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-select v-model="searchForm.status" placeholder="消息状态" clearable>
                <el-option label="正常" value="normal" />
                <el-option label="已举报" value="reported" />
                <el-option label="已删除" value="deleted" />
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
              <el-button type="primary" @click="searchMessages">搜索</el-button>
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
                  <el-icon><Message /></el-icon>
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
                  <div class="stat-number">{{ stats.normal }}</div>
                  <div class="stat-label">正常消息</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card warning">
                <div class="stat-icon">
                  <el-icon><Warning /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.reported }}</div>
                  <div class="stat-label">被举报</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card danger">
                <div class="stat-icon">
                  <el-icon><Delete /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.deleted }}</div>
                  <div class="stat-label">已删除</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <!-- 消息列表 -->
        <div class="message-list">
          <div v-for="message in messages" :key="message.id" class="message-item">
            <div class="message-header">
              <div class="participants">
                <div class="sender">
                  <el-avatar :src="message.sender.avatar" :size="32" />
                  <span class="username">{{ message.sender.username }}</span>
                </div>
                <el-icon class="arrow-icon"><Right /></el-icon>
                <div class="receiver">
                  <el-avatar :src="message.receiver.avatar" :size="32" />
                  <span class="username">{{ message.receiver.username }}</span>
                </div>
              </div>
              <div class="message-meta">
                <el-tag :type="getStatusType(message.status)" size="small">
                  {{ getStatusText(message.status) }}
                </el-tag>
                <span class="message-time">{{ formatDateTime(message.createdAt) }}</span>
              </div>
            </div>
            
            <div class="message-content">
              <div class="content-text">{{ message.content }}</div>
              <div v-if="message.images && message.images.length" class="content-images">
                <el-image
                  v-for="(image, index) in message.images"
                  :key="index"
                  :src="image"
                  :preview-src-list="message.images"
                  fit="cover"
                  style="width: 60px; height: 60px; border-radius: 4px; margin-right: 8px;"
                />
              </div>
            </div>
            
            <div class="message-footer">
              <div class="message-stats">
                <span v-if="message.isRead" class="read-status">
                  <el-icon><View /></el-icon>
                  已读
                </span>
                <span v-else class="read-status unread">
                  <el-icon><Hide /></el-icon>
                  未读
                </span>
                <span v-if="message.reportCount > 0" class="report-count">
                  <el-icon><Warning /></el-icon>
                  {{ message.reportCount }} 次举报
                </span>
              </div>
              <div class="message-actions">
                <el-button type="text" size="small" @click="viewMessageDetail(message)">
                  详情
                </el-button>
                <el-button 
                  v-if="message.status === 'reported'"
                  type="text" 
                  size="small" 
                  @click="approveMessage(message)"
                >
                  恢复
                </el-button>
                <el-button type="text" size="small" @click="deleteMessage(message)">
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
            @size-change="fetchMessages"
            @current-change="fetchMessages"
          />
        </div>
      </div>
    </div>
    
    <!-- 消息详情对话框 -->
    <el-dialog
      v-model="detailDialog.visible"
      title="私信详情"
      width="600px"
      destroy-on-close
    >
      <div v-if="detailDialog.message" class="message-detail">
        <div class="detail-header">
          <div class="participants-detail">
            <div class="participant">
              <el-avatar :src="detailDialog.message.sender.avatar" :size="50" />
              <div class="participant-info">
                <h4>{{ detailDialog.message.sender.username }}</h4>
                <p>发送者</p>
              </div>
            </div>
            <el-icon class="arrow-icon" size="24"><Right /></el-icon>
            <div class="participant">
              <el-avatar :src="detailDialog.message.receiver.avatar" :size="50" />
              <div class="participant-info">
                <h4>{{ detailDialog.message.receiver.username }}</h4>
                <p>接收者</p>
              </div>
            </div>
          </div>
        </div>
        
        <div class="detail-content">
          <h5>消息内容</h5>
          <div class="content-box">{{ detailDialog.message.content }}</div>
          
          <div v-if="detailDialog.message.images && detailDialog.message.images.length" class="images-section">
            <h5>图片附件</h5>
            <div class="image-grid">
              <el-image
                v-for="(image, index) in detailDialog.message.images"
                :key="index"
                :src="image"
                :preview-src-list="detailDialog.message.images"
                fit="cover"
                style="width: 100px; height: 100px; border-radius: 8px;"
              />
            </div>
          </div>
        </div>
        
        <div class="detail-meta">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="发送时间">
              {{ formatDateTime(detailDialog.message.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="阅读状态">
              <el-tag :type="detailDialog.message.isRead ? 'success' : 'warning'">
                {{ detailDialog.message.isRead ? '已读' : '未读' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="举报次数">
              {{ detailDialog.message.reportCount || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="消息状态">
              <el-tag :type="getStatusType(detailDialog.message.status)">
                {{ getStatusText(detailDialog.message.status) }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>
        
        <div v-if="detailDialog.message.reports && detailDialog.message.reports.length" class="reports-section">
          <h5>举报记录</h5>
          <el-timeline>
            <el-timeline-item
              v-for="report in detailDialog.message.reports"
              :key="report.id"
              :timestamp="formatDateTime(report.createdAt)"
              placement="top"
            >
              <div class="report-item">
                <p><strong>举报者：</strong>{{ report.reporter.username }}</p>
                <p><strong>举报原因：</strong>{{ report.reason }}</p>
                <p v-if="report.description"><strong>详细说明：</strong>{{ report.description }}</p>
              </div>
            </el-timeline-item>
          </el-timeline>
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
  Message,
  Check,
  Warning,
  Delete,
  Right,
  View,
  Hide
} from '@element-plus/icons-vue'

// 响应式数据
const messages = ref([])
const loading = ref(false)

const searchForm = reactive({
  keyword: '',
  status: '',
  dateRange: []
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const stats = reactive({
  total: 0,
  normal: 0,
  reported: 0,
  deleted: 0
})

const detailDialog = reactive({
  visible: false,
  message: null
})

// 方法
const getStatusType = (status) => {
  const typeMap = {
    normal: 'success',
    reported: 'warning',
    deleted: 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    normal: '正常',
    reported: '已举报',
    deleted: '已删除'
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
      const statuses = ['normal', 'reported', 'deleted']
      const status = statuses[index % statuses.length]
      
      return {
        id: index + 1,
        sender: {
          id: Math.floor(Math.random() * 100) + 1,
          username: `sender${index + 1}`,
          avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=sender${index + 1}`
        },
        receiver: {
          id: Math.floor(Math.random() * 100) + 100,
          username: `receiver${index + 1}`,
          avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=receiver${index + 1}`
        },
        content: `这是私信内容${index + 1}，用户之间的私密交流信息...`,
        images: Math.random() > 0.7 ? [`https://picsum.photos/200/200?random=${index + 100}`] : [],
        status,
        isRead: Math.random() > 0.3,
        reportCount: status === 'reported' ? Math.floor(Math.random() * 5) + 1 : 0,
        reports: status === 'reported' ? [
          {
            id: 1,
            reporter: {
              username: `reporter${index}`
            },
            reason: '不当内容',
            description: '包含不适当的言论',
            createdAt: new Date(Date.now() - Math.random() * 24 * 60 * 60 * 1000).toISOString()
          }
        ] : [],
        createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
      }
    })
    
    // 应用搜索过滤
    let filteredMessages = mockMessages
    if (searchForm.keyword) {
      filteredMessages = filteredMessages.filter(msg => 
        msg.sender.username.includes(searchForm.keyword) ||
        msg.receiver.username.includes(searchForm.keyword) ||
        msg.content.includes(searchForm.keyword)
      )
    }
    if (searchForm.status) {
      filteredMessages = filteredMessages.filter(msg => 
        msg.status === searchForm.status
      )
    }
    
    // 分页
    const start = (pagination.current - 1) * pagination.size
    const end = start + pagination.size
    
    messages.value = filteredMessages.slice(start, end)
    pagination.total = filteredMessages.length
    
    // 更新统计
    stats.total = mockMessages.length
    stats.normal = mockMessages.filter(m => m.status === 'normal').length
    stats.reported = mockMessages.filter(m => m.status === 'reported').length
    stats.deleted = mockMessages.filter(m => m.status === 'deleted').length
    
  } catch (error) {
    ElMessage.error('获取私信列表失败')
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
  searchForm.status = ''
  searchForm.dateRange = []
  pagination.current = 1
  fetchMessages()
}

const viewMessageDetail = (message) => {
  detailDialog.message = message
  detailDialog.visible = true
}

const approveMessage = async (message) => {
  try {
    await ElMessageBox.confirm(
      '确定要恢复这条私信吗？',
      '确认恢复',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )
    
    message.status = 'normal'
    ElMessage.success('私信已恢复')
    fetchMessages()
    
  } catch {
    // 用户取消操作
  }
}

const deleteMessage = async (message) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条私信吗？此操作不可恢复！',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    ElMessage.success('私信删除成功')
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
.message-management {
  .search-section {
    margin-bottom: 24px;
  }
  
  .stats-cards {
    margin-bottom: 24px;
  }
  
  .message-list {
    margin-bottom: 24px;
    
    .message-item {
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
      
      .message-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
        
        .participants {
          display: flex;
          align-items: center;
          gap: 16px;
          
          .sender, .receiver {
            display: flex;
            align-items: center;
            gap: 8px;
            
            .username {
              font-weight: 500;
              color: #303133;
            }
          }
          
          .arrow-icon {
            color: #909399;
          }
        }
        
        .message-meta {
          display: flex;
          align-items: center;
          gap: 12px;
          
          .message-time {
            font-size: 12px;
            color: #909399;
          }
        }
      }
      
      .message-content {
        margin-bottom: 16px;
        
        .content-text {
          color: #606266;
          line-height: 1.6;
          margin-bottom: 12px;
        }
        
        .content-images {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
        }
      }
      
      .message-footer {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding-top: 16px;
        border-top: 1px solid #f0f0f0;
        
        .message-stats {
          display: flex;
          gap: 16px;
          
          .read-status, .report-count {
            display: flex;
            align-items: center;
            gap: 4px;
            font-size: 14px;
            color: #909399;
            
            &.unread {
              color: #e6a23c;
            }
          }
          
          .report-count {
            color: #f56c6c;
          }
        }
        
        .message-actions {
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
  
  .message-detail {
    .detail-header {
      margin-bottom: 24px;
      
      .participants-detail {
        display: flex;
        align-items: center;
        justify-content: space-between;
        
        .participant {
          display: flex;
          align-items: center;
          gap: 12px;
          
          .participant-info {
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
        
        .arrow-icon {
          color: #409eff;
        }
      }
    }
    
    .detail-content {
      margin-bottom: 24px;
      
      h5 {
        margin-bottom: 12px;
        color: #303133;
        font-weight: 600;
      }
      
      .content-box {
        background: #f8f9fa;
        padding: 16px;
        border-radius: 8px;
        line-height: 1.6;
        color: #606266;
        margin-bottom: 16px;
      }
      
      .images-section {
        .image-grid {
          display: flex;
          flex-wrap: wrap;
          gap: 12px;
        }
      }
    }
    
    .detail-meta {
      margin-bottom: 24px;
    }
    
    .reports-section {
      h5 {
        margin-bottom: 16px;
        color: #303133;
        font-weight: 600;
      }
      
      .report-item {
        background: #fef0f0;
        padding: 12px;
        border-radius: 6px;
        border-left: 3px solid #f56c6c;
        
        p {
          margin: 4px 0;
          font-size: 14px;
          line-height: 1.5;
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .message-management {
    .search-section {
      .el-col {
        margin-bottom: 12px;
      }
    }
    
    .message-item {
      .message-header {
        flex-direction: column;
        gap: 12px;
        align-items: flex-start;
      }
      
      .message-footer {
        flex-direction: column;
        gap: 12px;
        align-items: flex-start;
      }
    }
    
    .message-detail {
      .detail-header {
        .participants-detail {
          flex-direction: column;
          gap: 16px;
        }
      }
    }
  }
}
</style>
