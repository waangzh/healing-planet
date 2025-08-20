<template>
  <div class="report-management">
    <div class="page-header">
      <h2 class="page-title">举报管理</h2>
    </div>
    
    <div class="admin-card">
      <div class="card-body">
        <!-- 搜索筛选 -->
        <div class="search-section">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="6">
              <el-input
                v-model="searchForm.keyword"
                placeholder="搜索举报者或被举报者"
                clearable
                @keyup.enter="searchReports"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-select v-model="searchForm.type" placeholder="举报类型" clearable>
                <el-option label="用户举报" value="user" />
                <el-option label="帖子举报" value="post" />
                <el-option label="评论举报" value="comment" />
                <el-option label="私信举报" value="message" />
              </el-select>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-select v-model="searchForm.status" placeholder="处理状态" clearable>
                <el-option label="待处理" value="pending" />
                <el-option label="已处理" value="resolved" />
                <el-option label="已驳回" value="rejected" />
                <el-option label="已忽略" value="ignored" />
              </el-select>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-button type="primary" @click="searchReports">搜索</el-button>
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
                  <el-icon><Warning /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.total }}</div>
                  <div class="stat-label">总举报数</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card warning">
                <div class="stat-icon">
                  <el-icon><Clock /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.pending }}</div>
                  <div class="stat-label">待处理</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card success">
                <div class="stat-icon">
                  <el-icon><Check /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.resolved }}</div>
                  <div class="stat-label">已处理</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card danger">
                <div class="stat-icon">
                  <el-icon><Close /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.rejected }}</div>
                  <div class="stat-label">已驳回</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <!-- 举报列表 -->
        <div class="report-table">
          <el-table :data="reports" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="type" label="举报类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getTypeTagType(row.type)" size="small">
                  {{ getTypeText(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reporter" label="举报者" width="120">
              <template #default="{ row }">
                <div class="user-info">
                  <el-avatar :src="row.reporter.avatar" :size="24" />
                  <span>{{ row.reporter.username }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="reported" label="被举报对象" min-width="150">
              <template #default="{ row }">
                <div v-if="row.type === 'user'" class="user-info">
                  <el-avatar :src="row.reported.avatar" :size="24" />
                  <span>{{ row.reported.username }}</span>
                </div>
                <div v-else class="content-preview">
                  <span class="content-text">{{ row.reported.title || row.reported.content }}</span>
                  <div class="content-meta">
                    <span>作者: {{ row.reported.author.username }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="举报原因" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.reason }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80">
              <template #default="{ row }">
                <el-tag :type="getPriorityTagType(row.priority)" size="small">
                  {{ getPriorityText(row.priority) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusTagType(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="举报时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="text" size="small" @click="viewReport(row)">
                  详情
                </el-button>
                <el-button 
                  v-if="row.status === 'pending'"
                  type="text" 
                  size="small" 
                  @click="processReport(row, 'resolved')"
                >
                  处理
                </el-button>
                <el-button 
                  v-if="row.status === 'pending'"
                  type="text" 
                  size="small" 
                  @click="processReport(row, 'rejected')"
                >
                  驳回
                </el-button>
                <el-dropdown @command="(command) => handleMoreAction(command, row)">
                  <el-button type="text" size="small">
                    更多
                    <el-icon><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="ignore">忽略</el-dropdown-item>
                      <el-dropdown-item command="ban" divided>封禁用户</el-dropdown-item>
                      <el-dropdown-item command="delete">删除内容</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
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
            @size-change="fetchReports"
            @current-change="fetchReports"
          />
        </div>
      </div>
    </div>
    
    <!-- 举报详情对话框 -->
    <el-dialog
      v-model="detailDialog.visible"
      title="举报详情"
      width="800px"
      destroy-on-close
    >
      <div v-if="detailDialog.report" class="report-detail">
        <div class="detail-header">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="举报ID">
              {{ detailDialog.report.id }}
            </el-descriptions-item>
            <el-descriptions-item label="举报类型">
              <el-tag :type="getTypeTagType(detailDialog.report.type)">
                {{ getTypeText(detailDialog.report.type) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="举报时间">
              {{ formatDateTime(detailDialog.report.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="处理状态">
              <el-tag :type="getStatusTagType(detailDialog.report.status)">
                {{ getStatusText(detailDialog.report.status) }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>
        
        <div class="participants">
          <h4>举报信息</h4>
          <el-row :gutter="20">
            <el-col :span="12">
              <div class="participant-card">
                <h5>举报者</h5>
                <div class="user-detail">
                  <el-avatar :src="detailDialog.report.reporter.avatar" :size="50" />
                  <div class="user-info">
                    <p class="username">{{ detailDialog.report.reporter.username }}</p>
                    <p class="user-id">ID: {{ detailDialog.report.reporter.id }}</p>
                  </div>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="participant-card">
                <h5>被举报对象</h5>
                <div v-if="detailDialog.report.type === 'user'" class="user-detail">
                  <el-avatar :src="detailDialog.report.reported.avatar" :size="50" />
                  <div class="user-info">
                    <p class="username">{{ detailDialog.report.reported.username }}</p>
                    <p class="user-id">ID: {{ detailDialog.report.reported.id }}</p>
                  </div>
                </div>
                <div v-else class="content-detail">
                  <h6>{{ detailDialog.report.reported.title || '内容预览' }}</h6>
                  <p class="content-text">{{ detailDialog.report.reported.content }}</p>
                  <p class="content-author">作者: {{ detailDialog.report.reported.author.username }}</p>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <div class="report-reason">
          <h4>举报原因</h4>
          <div class="reason-card">
            <el-tag>{{ detailDialog.report.reason }}</el-tag>
            <p v-if="detailDialog.report.description" class="description">
              {{ detailDialog.report.description }}
            </p>
          </div>
        </div>
        
        <div v-if="detailDialog.report.evidence && detailDialog.report.evidence.length" class="evidence-section">
          <h4>举报证据</h4>
          <div class="evidence-grid">
            <el-image
              v-for="(image, index) in detailDialog.report.evidence"
              :key="index"
              :src="image"
              :preview-src-list="detailDialog.report.evidence"
              fit="cover"
              style="width: 120px; height: 120px; border-radius: 8px;"
            />
          </div>
        </div>
        
        <div v-if="detailDialog.report.processingRecord" class="processing-record">
          <h4>处理记录</h4>
          <el-timeline>
            <el-timeline-item
              v-for="record in detailDialog.report.processingRecord"
              :key="record.id"
              :timestamp="formatDateTime(record.createdAt)"
              placement="top"
            >
              <div class="record-item">
                <p><strong>操作:</strong> {{ record.action }}</p>
                <p><strong>处理人:</strong> {{ record.operator }}</p>
                <p v-if="record.note"><strong>备注:</strong> {{ record.note }}</p>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialog.visible = false">关闭</el-button>
          <el-button 
            v-if="detailDialog.report.status === 'pending'"
            type="success" 
            @click="processReportFromDetail('resolved')"
          >
            处理完成
          </el-button>
          <el-button 
            v-if="detailDialog.report.status === 'pending'"
            type="warning" 
            @click="processReportFromDetail('rejected')"
          >
            驳回举报
          </el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 处理举报对话框 -->
    <el-dialog
      v-model="processDialog.visible"
      :title="processDialog.title"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="processFormRef"
        :model="processDialog.form"
        :rules="processFormRules"
        label-width="80px"
      >
        <el-form-item label="处理结果" prop="result">
          <el-radio-group v-model="processDialog.form.result">
            <el-radio label="resolved">处理完成</el-radio>
            <el-radio label="rejected">驳回举报</el-radio>
            <el-radio label="ignored">忽略</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item label="处理措施" prop="action" v-if="processDialog.form.result === 'resolved'">
          <el-checkbox-group v-model="processDialog.form.actions">
            <el-checkbox label="warning">警告用户</el-checkbox>
            <el-checkbox label="delete">删除内容</el-checkbox>
            <el-checkbox label="ban">封禁用户</el-checkbox>
            <el-checkbox label="restrict">限制功能</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        
        <el-form-item label="处理备注" prop="note">
          <el-input
            v-model="processDialog.form.note"
            type="textarea"
            :rows="4"
            placeholder="请输入处理备注"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="processDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="confirmProcess">确认处理</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Search,
  Warning,
  Clock,
  Check,
  Close,
  ArrowDown
} from '@element-plus/icons-vue'

// 响应式数据
const reports = ref([])
const loading = ref(false)
const processFormRef = ref()

const searchForm = reactive({
  keyword: '',
  type: '',
  status: ''
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const stats = reactive({
  total: 0,
  pending: 0,
  resolved: 0,
  rejected: 0
})

const detailDialog = reactive({
  visible: false,
  report: null
})

const processDialog = reactive({
  visible: false,
  title: '',
  report: null,
  form: {
    result: '',
    actions: [],
    note: ''
  }
})

// 表单验证规则
const processFormRules = {
  result: [
    { required: true, message: '请选择处理结果', trigger: 'change' }
  ],
  note: [
    { required: true, message: '请输入处理备注', trigger: 'blur' }
  ]
}

// 方法
const getTypeTagType = (type) => {
  const typeMap = {
    user: 'primary',
    post: 'success',
    comment: 'warning',
    message: 'info'
  }
  return typeMap[type] || 'info'
}

const getTypeText = (type) => {
  const textMap = {
    user: '用户举报',
    post: '帖子举报',
    comment: '评论举报',
    message: '私信举报'
  }
  return textMap[type] || '未知'
}

const getPriorityTagType = (priority) => {
  const typeMap = {
    low: 'info',
    medium: 'warning',
    high: 'danger'
  }
  return typeMap[priority] || 'info'
}

const getPriorityText = (priority) => {
  const textMap = {
    low: '低',
    medium: '中',
    high: '高'
  }
  return textMap[priority] || '未知'
}

const getStatusTagType = (status) => {
  const typeMap = {
    pending: 'warning',
    resolved: 'success',
    rejected: 'danger',
    ignored: 'info'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    pending: '待处理',
    resolved: '已处理',
    rejected: '已驳回',
    ignored: '已忽略'
  }
  return textMap[status] || '未知'
}

const formatDateTime = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const fetchReports = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))
    
    const mockReports = Array.from({ length: 50 }, (_, index) => {
      const types = ['user', 'post', 'comment', 'message']
      const statuses = ['pending', 'resolved', 'rejected', 'ignored']
      const priorities = ['low', 'medium', 'high']
      const reasons = ['垃圾内容', '骚扰', '不当言论', '侵权', '色情内容', '虚假信息']
      
      const type = types[index % types.length]
      const status = statuses[index % statuses.length]
      const priority = priorities[index % priorities.length]
      const reason = reasons[index % reasons.length]
      
      return {
        id: index + 1,
        type,
        reporter: {
          id: Math.floor(Math.random() * 1000) + 1,
          username: `reporter${index + 1}`,
          avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=reporter${index + 1}`
        },
        reported: type === 'user' ? {
          id: Math.floor(Math.random() * 1000) + 1000,
          username: `reported${index + 1}`,
          avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=reported${index + 1}`
        } : {
          id: Math.floor(Math.random() * 1000) + 2000,
          title: type === 'post' ? `帖子标题${index + 1}` : null,
          content: `这是${getTypeText(type)}的内容${index + 1}...`,
          author: {
            username: `author${index + 1}`,
            avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=author${index + 1}`
          }
        },
        reason,
        description: `详细举报说明${index + 1}`,
        priority,
        status,
        evidence: Math.random() > 0.7 ? [
          `https://picsum.photos/300/200?random=${index + 200}`,
          `https://picsum.photos/300/200?random=${index + 300}`
        ] : [],
        processingRecord: status !== 'pending' ? [
          {
            id: 1,
            action: getStatusText(status),
            operator: 'admin',
            note: '处理完成',
            createdAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString()
          }
        ] : null,
        createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
      }
    })
    
    // 应用搜索过滤
    let filteredReports = mockReports
    if (searchForm.keyword) {
      filteredReports = filteredReports.filter(report => 
        report.reporter.username.includes(searchForm.keyword) ||
        (report.type === 'user' && report.reported.username.includes(searchForm.keyword)) ||
        (report.type !== 'user' && report.reported.author.username.includes(searchForm.keyword))
      )
    }
    if (searchForm.type) {
      filteredReports = filteredReports.filter(report => 
        report.type === searchForm.type
      )
    }
    if (searchForm.status) {
      filteredReports = filteredReports.filter(report => 
        report.status === searchForm.status
      )
    }
    
    // 分页
    const start = (pagination.current - 1) * pagination.size
    const end = start + pagination.size
    
    reports.value = filteredReports.slice(start, end)
    pagination.total = filteredReports.length
    
    // 更新统计
    stats.total = mockReports.length
    stats.pending = mockReports.filter(r => r.status === 'pending').length
    stats.resolved = mockReports.filter(r => r.status === 'resolved').length
    stats.rejected = mockReports.filter(r => r.status === 'rejected').length
    
  } catch (error) {
    ElMessage.error('获取举报列表失败')
  } finally {
    loading.value = false
  }
}

const searchReports = () => {
  pagination.current = 1
  fetchReports()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.type = ''
  searchForm.status = ''
  pagination.current = 1
  fetchReports()
}

const viewReport = (report) => {
  detailDialog.report = report
  detailDialog.visible = true
}

const processReport = (report, result) => {
  processDialog.report = report
  processDialog.title = result === 'resolved' ? '处理举报' : '驳回举报'
  processDialog.form = {
    result,
    actions: [],
    note: ''
  }
  processDialog.visible = true
}

const processReportFromDetail = (result) => {
  processReport(detailDialog.report, result)
  detailDialog.visible = false
}

const confirmProcess = async () => {
  if (!processFormRef.value) return
  
  try {
    await processFormRef.value.validate()
    
    ElMessage.success('举报处理成功')
    processDialog.visible = false
    fetchReports()
    
  } catch {
    // 验证失败
  }
}

const handleMoreAction = async (command, report) => {
  switch (command) {
    case 'ignore':
      await processReportAction(report, '忽略举报')
      break
    case 'ban':
      await processReportAction(report, '封禁用户')
      break
    case 'delete':
      await processReportAction(report, '删除内容')
      break
  }
}

const processReportAction = async (report, action) => {
  try {
    await ElMessageBox.confirm(
      `确定要${action}吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    ElMessage.success(`${action}成功`)
    fetchReports()
    
  } catch {
    // 用户取消操作
  }
}

// 生命周期
onMounted(() => {
  fetchReports()
})
</script>

<style lang="scss" scoped>
.report-management {
  .search-section {
    margin-bottom: 24px;
  }
  
  .stats-cards {
    margin-bottom: 24px;
  }
  
  .report-table {
    margin-bottom: 24px;
    
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      
      span {
        font-size: 14px;
        color: #606266;
      }
    }
    
    .content-preview {
      .content-text {
        font-size: 14px;
        color: #303133;
        margin-bottom: 4px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 200px;
      }
      
      .content-meta {
        font-size: 12px;
        color: #909399;
      }
    }
  }
  
  .pagination-wrapper {
    display: flex;
    justify-content: center;
  }
  
  .report-detail {
    .detail-header {
      margin-bottom: 24px;
    }
    
    .participants {
      margin-bottom: 24px;
      
      h4 {
        margin-bottom: 16px;
        color: #303133;
        font-weight: 600;
      }
      
      .participant-card {
        padding: 16px;
        background: #f8f9fa;
        border-radius: 8px;
        border: 1px solid #e4e7ed;
        
        h5 {
          margin-bottom: 12px;
          color: #606266;
          font-size: 14px;
          font-weight: 500;
        }
        
        .user-detail {
          display: flex;
          align-items: center;
          gap: 12px;
          
          .user-info {
            .username {
              margin: 0 0 4px 0;
              font-weight: 500;
              color: #303133;
            }
            
            .user-id {
              margin: 0;
              font-size: 12px;
              color: #909399;
            }
          }
        }
        
        .content-detail {
          h6 {
            margin: 0 0 8px 0;
            color: #303133;
            font-weight: 500;
          }
          
          .content-text {
            color: #606266;
            line-height: 1.6;
            margin-bottom: 8px;
          }
          
          .content-author {
            margin: 0;
            font-size: 12px;
            color: #909399;
          }
        }
      }
    }
    
    .report-reason {
      margin-bottom: 24px;
      
      h4 {
        margin-bottom: 16px;
        color: #303133;
        font-weight: 600;
      }
      
      .reason-card {
        padding: 16px;
        background: #fef0f0;
        border-radius: 8px;
        border-left: 4px solid #f56c6c;
        
        .description {
          margin: 12px 0 0 0;
          color: #606266;
          line-height: 1.6;
        }
      }
    }
    
    .evidence-section {
      margin-bottom: 24px;
      
      h4 {
        margin-bottom: 16px;
        color: #303133;
        font-weight: 600;
      }
      
      .evidence-grid {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
      }
    }
    
    .processing-record {
      h4 {
        margin-bottom: 16px;
        color: #303133;
        font-weight: 600;
      }
      
      .record-item {
        background: #f0f9ff;
        padding: 12px;
        border-radius: 6px;
        border-left: 3px solid #409eff;
        
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
  .report-management {
    .search-section {
      .el-col {
        margin-bottom: 12px;
      }
    }
    
    .participants {
      .el-col {
        margin-bottom: 16px;
      }
    }
  }
}
</style>
