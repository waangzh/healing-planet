<template>
  <div class="plant-recognition">
    <div class="page-header">
      <h2 class="page-title">植物识别记录</h2>
    </div>
    
    <div class="admin-card">
      <div class="card-body">
        <!-- 搜索筛选 -->
        <div class="search-section">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="6">
              <el-input
                v-model="searchForm.keyword"
                placeholder="搜索用户名或植物名"
                clearable
                @keyup.enter="searchRecords"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-select v-model="searchForm.status" placeholder="识别状态" clearable>
                <el-option label="成功" value="success" />
                <el-option label="失败" value="failed" />
                <el-option label="处理中" value="processing" />
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
              <el-button type="primary" @click="searchRecords">搜索</el-button>
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
                  <el-icon><View /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.total }}</div>
                  <div class="stat-label">总识别次数</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card success">
                <div class="stat-icon">
                  <el-icon><Check /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.success }}</div>
                  <div class="stat-label">成功识别</div>
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
                  <div class="stat-label">识别失败</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card warning">
                <div class="stat-icon">
                  <el-icon><Loading /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.processing }}</div>
                  <div class="stat-label">处理中</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <!-- 识别记录表格 -->
        <div class="table-section">
          <el-table :data="records" style="width: 100%" v-loading="loading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column label="用户" width="150">
              <template #default="{ row }">
                <div class="user-info">
                  <el-avatar :src="row.user.avatar" :size="32" />
                  <span class="username">{{ row.user.username }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="识别图片" width="120">
              <template #default="{ row }">
                <el-image
                  :src="row.image"
                  :preview-src-list="[row.image]"
                  fit="cover"
                  style="width: 60px; height: 60px; border-radius: 4px;"
                />
              </template>
            </el-table-column>
            <el-table-column prop="plantName" label="植物名称" width="150" />
            <el-table-column label="识别状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="confidence" label="置信度" width="100">
              <template #default="{ row }">
                <el-progress
                  v-if="row.confidence"
                  :percentage="Math.round(row.confidence * 100)"
                  :color="getConfidenceColor(row.confidence)"
                  :stroke-width="6"
                />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="processingTime" label="处理时间" width="120">
              <template #default="{ row }">
                <span v-if="row.processingTime">{{ row.processingTime }}ms</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="识别时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="text" size="small" @click="viewDetail(row)">
                  详情
                </el-button>
                <el-button 
                  v-if="row.status === 'failed'"
                  type="text" 
                  size="small" 
                  @click="retryRecognition(row)"
                >
                  重试
                </el-button>
                <el-button type="text" size="small" @click="deleteRecord(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <!-- 分页 -->
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="pagination.current"
              v-model:page-size="pagination.size"
              :total="pagination.total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchRecords"
              @current-change="fetchRecords"
            />
          </div>
        </div>
      </div>
    </div>
    
    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialog.visible"
      title="识别详情"
      width="600px"
      destroy-on-close
    >
      <div v-if="detailDialog.record" class="recognition-detail">
        <el-row :gutter="24">
          <el-col :span="12">
            <div class="detail-section">
              <h4>识别图片</h4>
              <el-image
                :src="detailDialog.record.image"
                :preview-src-list="[detailDialog.record.image]"
                fit="cover"
                style="width: 100%; height: 200px; border-radius: 8px;"
              />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="detail-section">
              <h4>识别结果</h4>
              <div class="result-info">
                <p><strong>植物名称：</strong>{{ detailDialog.record.plantName || '未识别' }}</p>
                <p><strong>学名：</strong>{{ detailDialog.record.scientificName || '未知' }}</p>
                <p><strong>置信度：</strong>{{ detailDialog.record.confidence ? Math.round(detailDialog.record.confidence * 100) + '%' : '-' }}</p>
                <p><strong>处理时间：</strong>{{ detailDialog.record.processingTime || '-' }}ms</p>
                <p><strong>状态：</strong>
                  <el-tag :type="getStatusType(detailDialog.record.status)">
                    {{ getStatusText(detailDialog.record.status) }}
                  </el-tag>
                </p>
              </div>
            </div>
          </el-col>
        </el-row>
        
        <div v-if="detailDialog.record.alternatives && detailDialog.record.alternatives.length" class="detail-section">
          <h4>备选结果</h4>
          <el-table :data="detailDialog.record.alternatives" size="small">
            <el-table-column prop="name" label="植物名称" />
            <el-table-column prop="confidence" label="置信度" width="100">
              <template #default="{ row }">
                {{ Math.round(row.confidence * 100) }}%
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <div v-if="detailDialog.record.errorMessage" class="detail-section">
          <h4>错误信息</h4>
          <el-alert :title="detailDialog.record.errorMessage" type="error" show-icon />
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
  View, 
  Check, 
  Close, 
  Loading 
} from '@element-plus/icons-vue'

// 响应式数据
const records = ref([])
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
  success: 0,
  failed: 0,
  processing: 0
})

const detailDialog = reactive({
  visible: false,
  record: null
})

// 方法
const getStatusType = (status) => {
  const typeMap = {
    success: 'success',
    failed: 'danger',
    processing: 'warning'
  }
  return typeMap[status] || ''
}

const getStatusText = (status) => {
  const textMap = {
    success: '成功',
    failed: '失败',
    processing: '处理中'
  }
  return textMap[status] || '未知'
}

const getConfidenceColor = (confidence) => {
  if (confidence >= 0.8) return '#67c23a'
  if (confidence >= 0.6) return '#e6a23c'
  return '#f56c6c'
}

const formatDateTime = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const fetchRecords = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))
    
    const mockRecords = Array.from({ length: 50 }, (_, index) => {
      const statuses = ['success', 'failed', 'processing']
      const plants = ['绿萝', '发财树', '多肉植物', '吊兰', '虎皮兰', '仙人掌']
      const status = statuses[index % statuses.length]
      
      return {
        id: index + 1,
        user: {
          id: Math.floor(Math.random() * 100) + 1,
          username: `user${index + 1}`,
          avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${index + 1}`
        },
        image: `https://picsum.photos/300/300?random=${index + 100}`,
        plantName: status === 'success' ? plants[index % plants.length] : null,
        scientificName: status === 'success' ? `Scientific Name ${index}` : null,
        status,
        confidence: status === 'success' ? Math.random() : null,
        processingTime: status !== 'processing' ? Math.floor(Math.random() * 3000) + 500 : null,
        alternatives: status === 'success' ? [
          { name: '备选植物1', confidence: Math.random() * 0.5 },
          { name: '备选植物2', confidence: Math.random() * 0.3 }
        ] : [],
        errorMessage: status === 'failed' ? '图片质量不佳，无法识别' : null,
        createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
      }
    })
    
    // 应用搜索过滤
    let filteredRecords = mockRecords
    if (searchForm.keyword) {
      filteredRecords = filteredRecords.filter(record => 
        record.user.username.includes(searchForm.keyword) ||
        (record.plantName && record.plantName.includes(searchForm.keyword))
      )
    }
    if (searchForm.status) {
      filteredRecords = filteredRecords.filter(record => 
        record.status === searchForm.status
      )
    }
    
    // 分页
    const start = (pagination.current - 1) * pagination.size
    const end = start + pagination.size
    
    records.value = filteredRecords.slice(start, end)
    pagination.total = filteredRecords.length
    
    // 更新统计
    stats.total = mockRecords.length
    stats.success = mockRecords.filter(r => r.status === 'success').length
    stats.failed = mockRecords.filter(r => r.status === 'failed').length
    stats.processing = mockRecords.filter(r => r.status === 'processing').length
    
  } catch (error) {
    ElMessage.error('获取识别记录失败')
  } finally {
    loading.value = false
  }
}

const searchRecords = () => {
  pagination.current = 1
  fetchRecords()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.dateRange = []
  pagination.current = 1
  fetchRecords()
}

const viewDetail = (record) => {
  detailDialog.record = record
  detailDialog.visible = true
}

const retryRecognition = async (record) => {
  try {
    await ElMessageBox.confirm(
      '确定要重新识别这张图片吗？',
      '确认重试',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    // 模拟重新识别
    record.status = 'processing'
    ElMessage.success('已开始重新识别')
    
    // 模拟处理完成
    setTimeout(() => {
      record.status = 'success'
      record.plantName = '重新识别的植物'
      record.confidence = 0.85
      record.processingTime = 1200
      ElMessage.success('重新识别完成')
      fetchRecords()
    }, 3000)
    
  } catch {
    // 用户取消操作
  }
}

const deleteRecord = async (record) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条识别记录吗？',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    ElMessage.success('识别记录删除成功')
    fetchRecords()
    
  } catch {
    // 用户取消操作
  }
}

// 生命周期
onMounted(() => {
  fetchRecords()
})
</script>

<style lang="scss" scoped>
.plant-recognition {
  .search-section {
    margin-bottom: 24px;
  }
  
  .stats-cards {
    margin-bottom: 24px;
  }
  
  .table-section {
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      
      .username {
        font-weight: 500;
      }
    }
    
    .pagination-wrapper {
      margin-top: 24px;
      display: flex;
      justify-content: center;
    }
  }
  
  .recognition-detail {
    .detail-section {
      margin-bottom: 24px;
      
      h4 {
        margin-bottom: 12px;
        color: #303133;
        font-weight: 600;
      }
      
      .result-info {
        p {
          margin-bottom: 8px;
          line-height: 1.6;
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .plant-recognition {
    .search-section {
      .el-col {
        margin-bottom: 12px;
      }
    }
    
    .stats-cards {
      .el-col {
        margin-bottom: 12px;
      }
    }
  }
}
</style>
