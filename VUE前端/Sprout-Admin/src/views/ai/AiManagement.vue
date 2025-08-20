<template>
  <div class="ai-management">
    <div class="page-header">
      <h2 class="page-title">AI管理</h2>
    </div>
    
    <!-- AI服务状态 -->
    <div class="admin-card">
      <div class="card-header">
        <h3 class="card-title">AI服务状态</h3>
      </div>
      <div class="card-body">
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12" :md="6">
            <div class="service-card">
              <div class="service-header">
                <el-icon size="24" color="#67c23a"><ChatLineRound /></el-icon>
                <div class="service-status">
                  <el-tag type="success">正常</el-tag>
                </div>
              </div>
              <div class="service-name">AI对话服务</div>
              <div class="service-metrics">
                <p>今日调用: {{ aiStats.chatCalls }}</p>
                <p>响应时间: {{ aiStats.chatResponseTime }}ms</p>
              </div>
            </div>
          </el-col>
          
          <el-col :xs="24" :sm="12" :md="6">
            <div class="service-card">
              <div class="service-header">
                <el-icon size="24" color="#409eff"><View /></el-icon>
                <div class="service-status">
                  <el-tag type="success">正常</el-tag>
                </div>
              </div>
              <div class="service-name">植物识别服务</div>
              <div class="service-metrics">
                <p>今日识别: {{ aiStats.recognitionCalls }}</p>
                <p>成功率: {{ aiStats.recognitionSuccess }}%</p>
              </div>
            </div>
          </el-col>
          
          <el-col :xs="24" :sm="12" :md="6">
            <div class="service-card">
              <div class="service-header">
                <el-icon size="24" color="#e6a23c"><TrendCharts /></el-icon>
                <div class="service-status">
                  <el-tag type="warning">负载高</el-tag>
                </div>
              </div>
              <div class="service-name">推荐服务</div>
              <div class="service-metrics">
                <p>今日推荐: {{ aiStats.recommendCalls }}</p>
                <p>准确率: {{ aiStats.recommendAccuracy }}%</p>
              </div>
            </div>
          </el-col>
          
          <el-col :xs="24" :sm="12" :md="6">
            <div class="service-card">
              <div class="service-header">
                <el-icon size="24" color="#f56c6c"><DocumentRemove /></el-icon>
                <div class="service-status">
                  <el-tag type="danger">异常</el-tag>
                </div>
              </div>
              <div class="service-name">内容审核服务</div>
              <div class="service-metrics">
                <p>今日审核: {{ aiStats.moderationCalls }}</p>
                <p>误报率: {{ aiStats.moderationError }}%</p>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>
    
    <!-- AI模型管理 -->
    <div class="admin-card">
      <div class="card-header">
        <h3 class="card-title">AI模型管理</h3>
        <el-button type="primary" @click="addModel">
          <el-icon><Plus /></el-icon>
          添加模型
        </el-button>
      </div>
      <div class="card-body">
        <el-table :data="models" style="width: 100%">
          <el-table-column prop="name" label="模型名称" width="200" />
          <el-table-column prop="type" label="模型类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getModelTypeTag(row.type)">
                {{ getModelTypeText(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '激活' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="accuracy" label="准确率" width="100">
            <template #default="{ row }">
              {{ row.accuracy }}%
            </template>
          </el-table-column>
          <el-table-column prop="lastTrained" label="最后训练" width="160">
            <template #default="{ row }">
              {{ formatDate(row.lastTrained) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="200">
            <template #default="{ row }">
              <el-button
                type="text"
                size="small"
                @click="toggleModelStatus(row)"
              >
                {{ row.status === 'active' ? '停用' : '激活' }}
              </el-button>
              <el-button type="text" size="small" @click="trainModel(row)">
                重新训练
              </el-button>
              <el-button type="text" size="small" @click="viewModelDetail(row)">
                详情
              </el-button>
              <el-button type="text" size="small" @click="deleteModel(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    
    <!-- AI配置 -->
    <div class="admin-card">
      <div class="card-header">
        <h3 class="card-title">AI配置</h3>
      </div>
      <div class="card-body">
        <el-form :model="aiConfig" label-width="150px">
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="对话超时时间">
                <el-input-number
                  v-model="aiConfig.chatTimeout"
                  :min="1000"
                  :max="30000"
                  :step="1000"
                  controls-position="right"
                />
                <span style="margin-left: 8px; color: #909399;">毫秒</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="识别超时时间">
                <el-input-number
                  v-model="aiConfig.recognitionTimeout"
                  :min="1000"
                  :max="60000"
                  :step="1000"
                  controls-position="right"
                />
                <span style="margin-left: 8px; color: #909399;">毫秒</span>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="最大并发请求">
                <el-input-number
                  v-model="aiConfig.maxConcurrent"
                  :min="1"
                  :max="100"
                  controls-position="right"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="缓存过期时间">
                <el-input-number
                  v-model="aiConfig.cacheExpiry"
                  :min="60"
                  :max="86400"
                  :step="60"
                  controls-position="right"
                />
                <span style="margin-left: 8px; color: #909399;">秒</span>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="24">
            <el-col :span="24">
              <el-form-item label="敏感词过滤">
                <el-switch v-model="aiConfig.enableSensitiveFilter" />
                <span style="margin-left: 8px; color: #909399;">
                  启用后将自动过滤敏感内容
                </span>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="24">
            <el-col :span="24">
              <el-form-item label="API密钥">
                <el-input
                  v-model="aiConfig.apiKey"
                  type="password"
                  placeholder="请输入AI服务API密钥"
                  show-password
                />
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-form-item>
            <el-button type="primary" @click="saveConfig">保存配置</el-button>
            <el-button @click="resetConfig">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
    
    <!-- 添加模型对话框 -->
    <el-dialog
      v-model="modelDialog.visible"
      title="添加AI模型"
      width="600px"
      destroy-on-close
    >
      <el-form :model="modelDialog.form" label-width="120px">
        <el-form-item label="模型名称" required>
          <el-input v-model="modelDialog.form.name" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="模型类型" required>
          <el-select v-model="modelDialog.form.type" placeholder="请选择模型类型">
            <el-option label="对话模型" value="chat" />
            <el-option label="识别模型" value="recognition" />
            <el-option label="推荐模型" value="recommendation" />
            <el-option label="审核模型" value="moderation" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" required>
          <el-input v-model="modelDialog.form.version" placeholder="如: v1.0.0" />
        </el-form-item>
        <el-form-item label="模型文件">
          <el-upload
            class="upload-demo"
            drag
            action=""
            :auto-upload="false"
            :on-change="handleFileChange"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              将模型文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 .pkl, .pth, .h5 等格式的模型文件
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="modelDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Plus,
  ChatLineRound,
  View,
  TrendCharts,
  DocumentRemove,
  UploadFilled
} from '@element-plus/icons-vue'

// 响应式数据
const aiStats = reactive({
  chatCalls: 1247,
  chatResponseTime: 850,
  recognitionCalls: 892,
  recognitionSuccess: 94.5,
  recommendCalls: 3456,
  recommendAccuracy: 87.2,
  moderationCalls: 567,
  moderationError: 2.3
})

const models = ref([])

const aiConfig = reactive({
  chatTimeout: 15000,
  recognitionTimeout: 30000,
  maxConcurrent: 20,
  cacheExpiry: 3600,
  enableSensitiveFilter: true,
  apiKey: ''
})

const modelDialog = reactive({
  visible: false,
  form: {
    name: '',
    type: '',
    version: '',
    file: null
  }
})

// 方法
const getModelTypeTag = (type) => {
  const tagMap = {
    chat: 'primary',
    recognition: 'success',
    recommendation: 'warning',
    moderation: 'danger'
  }
  return tagMap[type] || 'info'
}

const getModelTypeText = (type) => {
  const textMap = {
    chat: '对话',
    recognition: '识别',
    recommendation: '推荐',
    moderation: '审核'
  }
  return textMap[type] || '未知'
}

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const fetchModels = async () => {
  try {
    // 模拟API调用
    const mockModels = [
      {
        id: 1,
        name: 'ChatBot-Pro',
        type: 'chat',
        version: 'v2.1.0',
        status: 'active',
        accuracy: 94.5,
        lastTrained: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString()
      },
      {
        id: 2,
        name: 'PlantRecognition-CNN',
        type: 'recognition',
        version: 'v1.8.2',
        status: 'active',
        accuracy: 92.3,
        lastTrained: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString()
      },
      {
        id: 3,
        name: 'ContentFilter-BERT',
        type: 'moderation',
        version: 'v1.5.1',
        status: 'inactive',
        accuracy: 96.8,
        lastTrained: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString()
      }
    ]
    
    models.value = mockModels
    
  } catch (error) {
    ElMessage.error('获取模型列表失败')
  }
}

const addModel = () => {
  modelDialog.form = {
    name: '',
    type: '',
    version: '',
    file: null
  }
  modelDialog.visible = true
}

const saveModel = async () => {
  try {
    // 验证表单
    if (!modelDialog.form.name || !modelDialog.form.type || !modelDialog.form.version) {
      ElMessage.warning('请填写完整信息')
      return
    }
    
    ElMessage.success('模型添加成功')
    modelDialog.visible = false
    fetchModels()
    
  } catch (error) {
    ElMessage.error('模型添加失败')
  }
}

const handleFileChange = (file) => {
  modelDialog.form.file = file
}

const toggleModelStatus = async (model) => {
  try {
    const action = model.status === 'active' ? '停用' : '激活'
    await ElMessageBox.confirm(
      `确定要${action}模型 "${model.name}" 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    model.status = model.status === 'active' ? 'inactive' : 'active'
    ElMessage.success(`模型${action}成功`)
    
  } catch {
    // 用户取消操作
  }
}

const trainModel = async (model) => {
  try {
    await ElMessageBox.confirm(
      `确定要重新训练模型 "${model.name}" 吗？这可能需要较长时间。`,
      '确认训练',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    ElMessage.success('模型训练已开始，请稍后查看训练进度')
    
  } catch {
    // 用户取消操作
  }
}

const viewModelDetail = (model) => {
  ElMessage.info('查看模型详情功能开发中...')
}

const deleteModel = async (model) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除模型 "${model.name}" 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    ElMessage.success('模型删除成功')
    fetchModels()
    
  } catch {
    // 用户取消操作
  }
}

const saveConfig = async () => {
  try {
    ElMessage.success('AI配置保存成功')
  } catch (error) {
    ElMessage.error('配置保存失败')
  }
}

const resetConfig = () => {
  Object.assign(aiConfig, {
    chatTimeout: 15000,
    recognitionTimeout: 30000,
    maxConcurrent: 20,
    cacheExpiry: 3600,
    enableSensitiveFilter: true,
    apiKey: ''
  })
  ElMessage.success('配置已重置')
}

// 生命周期
onMounted(() => {
  fetchModels()
})
</script>

<style lang="scss" scoped>
.ai-management {
  .service-card {
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
    
    .service-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }
    
    .service-name {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 12px;
    }
    
    .service-metrics {
      p {
        margin: 4px 0;
        font-size: 14px;
        color: #606266;
      }
    }
  }
  
  .admin-card {
    margin-bottom: 24px;
  }
}

@media (max-width: 768px) {
  .ai-management {
    .service-card {
      margin-bottom: 12px;
    }
    
    .el-col {
      margin-bottom: 12px;
    }
  }
}
</style>
