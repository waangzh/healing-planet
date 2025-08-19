<template>
  <div class="plant-management">
    <div class="page-header">
      <h2 class="page-title">植物管理</h2>
    </div>
    
    <!-- 搜索和操作区域 -->
    <div class="admin-card">
      <div class="card-body">
        <div class="search-section" v-auto-submit-form="searchPlants">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="8">
              <el-input
                v-model="searchForm.key"
                placeholder="搜索植物名称、学名..."
                clearable
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-select
                v-model="searchForm.difficulty"
                placeholder="养护难度"
                clearable
                style="width: 100%"
              >
                <el-option label="简单(1级)" :value="1" />
                <el-option label="中等(2级)" :value="2" />
                <el-option label="较难(3级)" :value="3" />
                <el-option label="困难(4级)" :value="4" />
                <el-option label="地狱(5级)" :value="5" />
              </el-select>
            </el-col>
            <el-col :xs="24" :sm="24" :md="8" class="actions-col">
              <div class="left-actions">
                <el-button type="primary" @click="searchPlants">搜索</el-button>
                <el-button @click="resetSearch">重置</el-button>
              </div>
              <div class="right-action">
                <el-button type="primary" @click="showCreateDialog">
                  <el-icon><Plus /></el-icon>
                  新增植物
                </el-button>
              </div>
            </el-col>
          </el-row>
        </div>
      </div>
    </div>
    
    <!-- 植物列表 -->
    <div class="admin-card">
      <div class="card-body">
        <el-table
          v-loading="loading"
          :data="plants"
          style="width: 100%"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55" />
          <!-- <el-table-column prop="id" label="ID" width="180" /> -->
          <el-table-column label="植物信息" width="300">
            <template #default="{ row }">
              <div class="plant-info">
                <div class="plant-image">
                  <el-image
                    :src="row.coverImg"
                    fit="cover"
                    style="width: 60px; height: 60px; border-radius: 4px;"
                    :preview-src-list="[row.coverImg]"
                  />
                </div>
                <div class="plant-details">
                  <div class="plant-name">{{ row.commonName }}</div>
                  <div class="plant-scientific">{{ row.scientificName }}</div>
                  <div class="plant-create-time">{{ formatDate(row.createdAt) }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="养护难度" width="185">
            <template #default="{ row }">
              <el-tag :type="getDifficultyType(row.difficulty)">
                {{ getDifficultyText(row.difficulty) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="光照需求" width="185">
            <template #default="{ row }">
              <span>{{ row.lightRequirements }}</span>
            </template>
          </el-table-column>
          <el-table-column label="浇水频率" width="185">
            <template #default="{ row }">
              <span>{{ row.wateringFrequency }}</span>
            </template>
          </el-table-column>
          <el-table-column label="温度偏好" width="185">
            <template #default="{ row }">
              <span>{{ row.temperaturePreference }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="200" fixed="right">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button
                  type="primary"
                  size="small"
                  @click="viewPlant(row)"
                >
                  查看
                </el-button>
                <el-button
                  size="small"
                  @click="editPlant(row)"
                >
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  @click="deletePlant(row)"
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
            v-model:current-page="pagination.current"
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
    
    <!-- 植物详情抽屉 -->
    <el-drawer
      v-model="plantDetailDrawer"
      title="植物详情"
      size="600px"
    >
      <div v-if="selectedPlant" class="plant-detail-content">
        <div class="detail-section">
          <div class="plant-header">
            <el-image
              :src="selectedPlant.coverImg"
              fit="cover"
              style="width: 150px; height: 150px; border-radius: 8px;"
              :preview-src-list="[selectedPlant.coverImg]"
            />
            <div class="plant-basic">
              <h3>{{ selectedPlant.commonName }}</h3>
              <p class="scientific-name">{{ selectedPlant.scientificName }}</p>
              <div class="plant-tags">
                <el-tag :type="getDifficultyType(selectedPlant.difficulty)">
                  {{ getDifficultyText(selectedPlant.difficulty) }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
        
        <div class="detail-section">
          <h4>基本信息</h4>
          <el-descriptions :column="1" border class="plant-descriptions">
            <el-descriptions-item label="ID">{{ selectedPlant.id }}</el-descriptions-item>
            <el-descriptions-item label="中文名">{{ selectedPlant.commonName }}</el-descriptions-item>
            <el-descriptions-item label="学名">{{ selectedPlant.scientificName }}</el-descriptions-item>
            <el-descriptions-item label="养护难度">
              <el-tag :type="getDifficultyType(selectedPlant.difficulty)">
                {{ getDifficultyText(selectedPlant.difficulty) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(selectedPlant.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>
        
        <div class="detail-section">
          <h4>养护要求</h4>
          <el-descriptions :column="1" border class="plant-descriptions">
            <el-descriptions-item label="光照需求">{{ selectedPlant.lightRequirements }}</el-descriptions-item>
            <el-descriptions-item label="浇水频率">{{ selectedPlant.wateringFrequency }}</el-descriptions-item>
            <el-descriptions-item label="温度偏好">{{ selectedPlant.temperaturePreference }}</el-descriptions-item>
            <el-descriptions-item label="湿度偏好">{{ selectedPlant.humidityPreference }}</el-descriptions-item>
          </el-descriptions>
        </div>
        
        <div class="detail-section">
          <h4>施肥提示</h4>
          <div class="plant-description">
            {{ selectedPlant.fertilizingTips }}
          </div>
        </div>
        
        <div class="detail-section">
          <h4>详细建议</h4>
          <div class="plant-description">
            {{ selectedPlant.detailAdvice }}
          </div>
        </div>
      </div>
    </el-drawer>
    
    <!-- 新增/编辑植物对话框 -->
    <el-dialog
      v-model="plantFormDialog"
      :title="isEdit ? '编辑植物' : '新增植物'"
      width="800px"
      @close="resetForm"
    >
      <el-form
        ref="plantFormRef"
        :model="plantForm"
        :rules="plantRules"
        label-width="120px"
        v-auto-submit-form="handleSubmitPlant"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="中文名" prop="commonName">
              <el-input v-model="plantForm.commonName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学名" prop="scientificName">
              <el-input v-model="plantForm.scientificName" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="封面图片" prop="coverImg">
          <div class="image-upload-container">
            <div v-if="!imagePreview && !plantForm.coverImg" class="upload-area">
              <input
                type="file"
                accept="image/*"
                @change="handleImageSelect"
                class="file-input"
              >
              <el-icon class="upload-icon"><Plus /></el-icon>
              <div class="upload-text">上传植物图片</div>
              <div class="upload-tip">支持 JPG、PNG 格式，建议尺寸 300x300</div>
            </div>
            <div v-else class="image-preview">
              <img 
                :src="imagePreview || plantForm.coverImg" 
                alt="植物封面" 
                class="preview-image"
              >
              <div class="image-actions">
                <el-button 
                  type="primary" 
                  size="small" 
                  @click="triggerFileInput"
                  :loading="imageLoading"
                >
                  重新上传
                </el-button>
                <el-button 
                  type="danger" 
                  size="small" 
                  @click="removeImage"
                >
                  移除
                </el-button>
              </div>
              <input
                ref="fileInputRef"
                type="file"
                accept="image/*"
                @change="handleImageSelect"
                class="file-input hidden-input"
              >
            </div>
          </div>
        </el-form-item>
        
        <el-form-item label="养护难度" prop="difficulty">
          <el-select v-model="plantForm.difficulty" style="width: 100%">
            <!-- 改为 5 级 -->
            <el-option label="非常简单(1级)" :value="1" />
            <el-option label="简单(2级)" :value="2" />
            <el-option label="中等(3级)" :value="3" />
            <el-option label="较难(4级)" :value="4" />
            <el-option label="困难(5级)" :value="5" />
          </el-select>
        </el-form-item>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="光照需求" prop="lightRequirements">
              <el-input v-model="plantForm.lightRequirements" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="浇水频率" prop="wateringFrequency">
              <el-input v-model="plantForm.wateringFrequency" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="温度偏好" prop="temperaturePreference">
              <el-input v-model="plantForm.temperaturePreference" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="湿度偏好" prop="humidityPreference">
              <el-input v-model="plantForm.humidityPreference" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="施肥提示" prop="fertilizingTips">
          <el-input
            v-model="plantForm.fertilizingTips"
            type="textarea"
            :rows="3"
            placeholder="请输入施肥提示"
          />
        </el-form-item>
        
        <el-form-item label="详细建议" prop="detailAdvice">
          <el-input
            v-model="plantForm.detailAdvice"
            type="textarea"
            :rows="3"
            placeholder="请输入详细建议"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="plantFormDialog = false">取消</el-button>
          <el-button type="primary" :loading="saveLoading" @click="handleSubmitPlant">
            {{ isEdit ? '更新' : '创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { getPlantsList, addPlant, updatePlant, deletePlants } from '@/api/plants'
import { uploadFileService } from '@/api/common'

// 响应式数据
const loading = ref(false)
const saveLoading = ref(false)
const plantDetailDrawer = ref(false)
const plantFormDialog = ref(false)
const selectedPlant = ref(null)
const isEdit = ref(false)
const plantFormRef = ref()

// 图片上传相关
const imageLoading = ref(false)
const imagePreview = ref('')

// 搜索表单
const searchForm = reactive({
  key: '',
  difficulty: null
})

// 分页数据
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 植物列表数据
const plants = ref([])
const selectedPlants = ref([])

// 表单数据
const plantForm = reactive({
  id: null,
  scientificName: '',
  commonName: '',
  coverImg: '',
  difficulty: 1,
  lightRequirements: '',
  wateringFrequency: '',
  temperaturePreference: '',
  humidityPreference: '',
  fertilizingTips: '',
  detailAdvice: ''
})

const plantRules = {
  commonName: [
    { required: true, message: '请输入中文名', trigger: 'blur' }
  ],
  scientificName: [
    { required: true, message: '请输入学名', trigger: 'blur' }
  ],
  coverImg: [
    { required: true, message: '请输入封面图片URL', trigger: 'blur' }
  ],
  difficulty: [
    { required: true, message: '请选择养护难度', trigger: 'change' }
  ],
  lightRequirements: [
    { required: true, message: '请输入光照需求', trigger: 'blur' }
  ],
  wateringFrequency: [
    { required: true, message: '请输入浇水频率', trigger: 'blur' }
  ],
  temperaturePreference: [
    { required: true, message: '请输入温度偏好', trigger: 'blur' }
  ],
  humidityPreference: [
    { required: true, message: '请输入湿度偏好', trigger: 'blur' }
  ]
}

// 方法
const fetchPlants = async () => {
  try {
    loading.value = true
    const params = {
      pageNo: pagination.current,
      pageSize: pagination.size,
      key: searchForm.key || undefined,
      difficulty: searchForm.difficulty || undefined
    }
    
    const response = await getPlantsList(params)
    
    if (response.data.code === 200) {
      plants.value = response.data.data.records
      pagination.total = response.data.data.total
    } else {
      ElMessage.error(response.data.message || '获取植物列表失败')
    }
  } catch (error) {
    console.error('获取植物列表失败:', error)
    ElMessage.error('获取植物列表失败')
  } finally {
    loading.value = false
  }
}

const searchPlants = () => {
  pagination.current = 1
  fetchPlants()
}

const resetSearch = () => {
  searchForm.key = ''
  searchForm.difficulty = null
  pagination.current = 1
  fetchPlants()
}

const handleSelectionChange = (selection) => {
  selectedPlants.value = selection
}

const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  fetchPlants()
}

const handlePageChange = (page) => {
  pagination.current = page
  fetchPlants()
}

const viewPlant = (plant) => {
  selectedPlant.value = plant
  plantDetailDrawer.value = true
}

const showCreateDialog = () => {
  isEdit.value = false
  resetForm()
  plantFormDialog.value = true
}

const editPlant = (plant) => {
  isEdit.value = true
  Object.assign(plantForm, plant)
  // 设置图片预览
  imagePreview.value = plant.coverImg || ''
  plantFormDialog.value = true
}

const deletePlant = async (plant) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除植物 "${plant.commonName}" 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    loading.value = true
    const response = await deletePlants([plant.id])
    
    if (response.data.code === 200) {
      ElMessage.success('删除成功')
      fetchPlants()
    } else {
      ElMessage.error(response.data.message || '删除失败')
    }
    
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除植物失败:', error)
      ElMessage.error('删除失败，请重试')
    }
  } finally {
    loading.value = false
  }
}

const handleSubmitPlant = async () => {
  if (!plantFormRef.value) return
  
  await plantFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        saveLoading.value = true
        
        let response
        if (isEdit.value) {
          response = await updatePlant(plantForm)
        } else {
          const { id, createdAt, ...formData } = plantForm
          response = await addPlant(formData)
        }
        
        if (response.data.code === 200) {
          ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
          plantFormDialog.value = false
          fetchPlants()
        } else {
          ElMessage.error(response.data.message || '保存失败')
        }
        
      } catch (error) {
        console.error('保存植物失败:', error)
        ElMessage.error('保存失败，请重试')
      } finally {
        saveLoading.value = false
      }
    }
  })
}

// 图片上传相关方法
const fileInputRef = ref()

const handleImageSelect = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  // 验证文件类型和大小
  const isImage = /^image\//.test(file.type)
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB!')
    return
  }

  try {
    imageLoading.value = true
    ElMessage.info('正在上传图片...')
    
    const response = await uploadFileService(file)
    
    if (response.data && response.data.code === 200 && response.data.data) {
      plantForm.coverImg = response.data.data
      imagePreview.value = response.data.data
      ElMessage.success('图片上传成功')
    } else {
      ElMessage.error(response.data?.message || '图片上传失败')
    }
  } catch (error) {
    console.error('上传图片出错:', error)
    ElMessage.error('图片上传错误: ' + (error.message || '未知错误'))
  } finally {
    imageLoading.value = false
    // 清空input的value,以便可以重复选择同一文件
    event.target.value = ''
  }
}

const triggerFileInput = () => {
  if (fileInputRef.value) {
    fileInputRef.value.click()
  }
}

const removeImage = () => {
  plantForm.coverImg = ''
  imagePreview.value = ''
  ElMessage.success('图片已移除')
}

const resetForm = () => {
  Object.assign(plantForm, {
    id: null,
    scientificName: '',
    commonName: '',
    coverImg: '',
    difficulty: 1,
    lightRequirements: '',
    wateringFrequency: '',
    temperaturePreference: '',
    humidityPreference: '',
    fertilizingTips: '',
    detailAdvice: ''
  })
  
  // 重置图片预览
  imagePreview.value = ''
  
  if (plantFormRef.value) {
    plantFormRef.value.clearValidate()
  }
}

// 辅助方法
const getDifficultyType = (difficulty) => {
  // 支持 1-5 级的标签类型映射
  const difficultyMap = {
    1: 'success', // 简单
    2: 'success', // 中等
    3: 'warning', // 较难
    4: 'warning', // 困难
    5: 'danger'   // 地狱
  }
  return difficultyMap[difficulty] || ''
}

const getDifficultyText = (difficulty) => {
  // 支持 1-5 级的文本映射
  const difficultyMap = {
    1: '简单',
    2: '中等',
    3: '较难',
    4: '困难',
    5: '地狱'
  }
  return difficultyMap[difficulty] || '未知'
}

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

// 生命周期
onMounted(() => {
  fetchPlants()
})
</script>

<style lang="scss" scoped>
.plant-management {
  .search-section {
    margin-bottom: 24px;
    
    .actions-col {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: 32px;
      
      .left-actions {
        display: flex;
        gap: 8px;
      }
      
      .right-action {
        margin-left: auto;
      }
    }
    
    // 移动端适配
    @media (max-width: 768px) {
      .actions-col {
        flex-direction: column;
        height: auto;
        gap: 12px;
        
        .left-actions {
          width: 100%;
          justify-content: center;
        }
        
        .right-action {
          margin-left: 0;
          width: 100%;
          
          .el-button {
            width: 100%;
          }
        }
      }
    }
  }
  
  .plant-info {
    display: flex;
    gap: 12px;
    
    .plant-details {
      flex: 1;
      
      .plant-name {
        font-weight: 800;
        font-size: 16px;
        color: #303133;
        margin-bottom: 4px;
      }
      
      .plant-scientific {
        font-style: italic;
        font-size: 12px;
        color: #909399;
        margin-bottom: 4px;
      }
      
      .plant-create-time {
        font-size: 12px;
        color: #606266;
      }
    }
  }
  
  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: center;
  }
  
  .plant-detail-content {
    .detail-section {
      margin-bottom: 24px;
      
      .plant-header {
        display: flex;
        gap: 16px;
        align-items: flex-start;
        
        .plant-basic {
          flex: 1;
          
          h3 {
            margin: 0 0 8px 0;
            font-size: 20px;
            color: #303133;
          }
          
          .scientific-name {
            font-style: italic;
            color: #909399;
            margin: 0 0 12px 0;
          }
          
          .plant-tags {
            display: flex;
            gap: 8px;
          }
        }
      }
      
      h4 {
        margin-bottom: 12px;
        font-size: 16px;
        font-weight: 600;
        color: #303133;
      }
      
      .plant-description {
        padding: 16px;
        background: #f8f9fa;
        border-radius: 6px;
        line-height: 1.6;
        color: #606266;
      }
      
      // 统一描述组件的标签宽度
      .plant-descriptions {
        :deep(.el-descriptions__label) {
          width: 120px;
          min-width: 120px;
        }
      }
    }
  }
}

// 图片上传组件样式
.image-upload-container {
  .upload-area {
    position: relative;
    width: 200px;
    height: 200px;
    border: 2px dashed #dcdfe6;
    border-radius: 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.3s;
    background-color: #fafbfc;
    
    &:hover {
      border-color: #409eff;
      color: #409eff;
    }
    
    .file-input {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      opacity: 0;
      cursor: pointer;
    }
    
    .upload-icon {
      font-size: 32px;
      margin-bottom: 12px;
    }
    
    .upload-text {
      font-size: 14px;
      margin-bottom: 8px;
      color: #606266;
    }
    
    .upload-tip {
      font-size: 12px;
      color: #909399;
      text-align: center;
      line-height: 1.4;
    }
  }
  
  .image-preview {
    position: relative;
    width: 200px;
    height: 200px;
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid #dcdfe6;
    
    .preview-image {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    
    .image-actions {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      opacity: 0;
      transition: opacity 0.3s;
    }
    
    &:hover .image-actions {
      opacity: 1;
    }
    
    .hidden-input {
      display: none;
    }
  }
}

@media (max-width: 768px) {
  .plant-management {
    .search-section {
      .el-col {
        margin-bottom: 12px;
      }
    }
    
    .plant-detail-content {
      .plant-header {
        flex-direction: column;
        text-align: center;
      }
    }
  }
}
</style>
