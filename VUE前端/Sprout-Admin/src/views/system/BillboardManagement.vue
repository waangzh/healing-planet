<template>
  <div class="billboard-management">
    <!-- 操作区域 -->
    <div class="action-section">
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        新增公告
      </el-button>
      <el-button 
        type="danger" 
        :disabled="selectedBillboards.length === 0" 
        @click="handleBulkDelete"
      >
        <el-icon><Delete /></el-icon>
        批量删除 ({{ selectedBillboards.length }})
      </el-button>
    </div>

    <!-- 公告列表 -->
    <el-table
      v-loading="loading"
      :data="billboards"
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <!-- <el-table-column label="ID" prop="id" width="80" /> -->
      <el-table-column label="公告内容" min-width="300">
        <template #default="{ row }">
          <div class="content-cell">
            <span class="content-text">{{ row.content }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="显示状态" width="120">
        <template #default="{ row }">
          <el-switch
            v-model="row.show"
            @change="handleStatusChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="修改时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.modifyTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="editBillboard(row)">
            编辑
          </el-button>
          <el-button type="danger" size="small" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="billboardDialog.visible"
      :title="billboardDialog.isEdit ? '编辑公告' : '新增公告'"
      width="600px"
      @close="resetForm"
    >
      <el-form
        ref="billboardFormRef"
        :model="billboardForm"
        :rules="billboardRules"
        label-width="100px"
        v-auto-submit-form="handleSubmitBillboard"
      >
        <el-form-item label="公告内容" prop="content">
          <el-input
            v-model="billboardForm.content"
            type="textarea"
            :rows="4"
            placeholder="请输入公告内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="显示状态" prop="show">
          <el-switch
            v-model="billboardForm.show"
          />
          <div class="form-tip">
            <el-text size="small" type="info">
              设置为显示状态的公告将在用户端展示给用户
            </el-text>
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="billboardDialog.visible = false">取消</el-button>
          <el-button 
            type="primary" 
            :loading="saveLoading" 
            @click="handleSubmitBillboard"
          >
            {{ billboardDialog.isEdit ? '更新' : '创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { 
  getAllBillboards, 
  addBillboard, 
  updateBillboard, 
  deleteBillboard 
} from '@/api/billboard'

// --- 响应式数据 ---
const loading = ref(false)
const saveLoading = ref(false)
const billboards = ref([])
const selectedBillboards = ref([])
const billboardFormRef = ref()

const billboardDialog = reactive({
  visible: false,
  isEdit: false
})

const billboardForm = reactive({
  id: null,
  content: '',
  show: true
})

const billboardRules = {
  content: [
    { required: true, message: '请输入公告内容', trigger: 'blur' },
    { min: 1, max: 500, message: '公告内容长度为 1-500 个字符', trigger: 'blur' }
  ]
}

// --- API 请求 ---
const fetchBillboards = async () => {
  loading.value = true
  try {
    const response = await getAllBillboards()
    if (response.data.code === 200) {
      billboards.value = response.data.data
    } else {
      ElMessage.error(response.data.message || '获取公告列表失败')
    }
  } catch (error) {
    console.error('获取公告列表失败:', error)
    ElMessage.error('获取公告列表失败')
  } finally {
    loading.value = false
  }
}

// --- 事件处理 ---
const handleSelectionChange = (selection) => {
  selectedBillboards.value = selection
}

const handleStatusChange = async (row) => {
  try {
    const response = await updateBillboard({
      id: row.id,
      content: row.content,
      modifyTime: new Date().toISOString(),
      show: row.show
    })
    
    if (response.data.code === 200) {
      ElMessage.success('状态更新成功')
      fetchBillboards()
    } else {
      ElMessage.error(response.data.message || '状态更新失败')
      // 恢复原状态
      row.show = !row.show
    }
  } catch (error) {
    console.error('状态更新失败:', error)
    ElMessage.error('状态更新失败')
    // 恢复原状态
    row.show = !row.show
  }
}

const showCreateDialog = () => {
  billboardDialog.isEdit = false
  resetForm()
  billboardDialog.visible = true
}

const editBillboard = (row) => {
  billboardDialog.isEdit = true
  Object.assign(billboardForm, {
    id: row.id,
    content: row.content,
    show: row.show
  })
  billboardDialog.visible = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除公告"${row.content.substring(0, 20)}..."吗？此操作不可恢复！`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'error'
    }
  ).then(async () => {
    await executeDelete([row.id])
  }).catch(() => {})
}

const handleBulkDelete = () => {
  if (selectedBillboards.value.length === 0) {
    ElMessage.warning('请至少选择一条公告')
    return
  }
  
  ElMessageBox.confirm(
    `确定要删除选中的 ${selectedBillboards.value.length} 条公告吗？此操作不可恢复！`,
    '确认批量删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'error'
    }
  ).then(async () => {
    const ids = selectedBillboards.value.map(item => item.id)
    await executeDelete(ids)
  }).catch(() => {})
}

const executeDelete = async (ids) => {
  loading.value = true
  try {
    const response = await deleteBillboard(ids)
    if (response.data.code === 200) {
      ElMessage.success('删除成功')
      fetchBillboards()
    } else {
      ElMessage.error(response.data.message || '删除失败')
    }
  } catch (error) {
    console.error('删除公告失败:', error)
    ElMessage.error('删除失败，请重试')
  } finally {
    loading.value = false
  }
}

const handleSubmitBillboard = async () => {
  if (!billboardFormRef.value) return
  
  await billboardFormRef.value.validate(async (valid) => {
    if (valid) {
      saveLoading.value = true
      try {
        let response
        
        if (billboardDialog.isEdit) {
          // 编辑
          response = await updateBillboard({
            id: billboardForm.id,
            content: billboardForm.content,
            modifyTime: new Date().toISOString(),
            show: billboardForm.show
          })
        } else {
          // 新增
          response = await addBillboard({
            content: billboardForm.content,
            show: billboardForm.show
          })
        }
        
        if (response.data.code === 200) {
          ElMessage.success(billboardDialog.isEdit ? '更新成功' : '创建成功')
          billboardDialog.visible = false
          fetchBillboards()
        } else {
          ElMessage.error(response.data.message || '操作失败')
        }
        
      } catch (error) {
        console.error('保存公告失败:', error)
        ElMessage.error('保存失败，请重试')
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const resetForm = () => {
  Object.assign(billboardForm, {
    id: null,
    content: '',
    show: true
  })
  
  if (billboardFormRef.value) {
    billboardFormRef.value.clearValidate()
  }
}

// --- 辅助方法 ---
const formatDate = (dateString) => {
  if (!dateString) return 'N/A'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

// --- 生命周期 ---
onMounted(() => {
  fetchBillboards()
})
</script>

<style lang="scss" scoped>
.billboard-management {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.action-section {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.content-cell {
  .content-text {
    line-height: 1.5;
    word-break: break-word;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.form-tip {
  margin-left: 8px;
}

.dialog-footer {
  text-align: right;
}

// 表格优化
.el-table {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);

  .el-table__header {
    background: #fafafa;
  }
}

.el-table th,
.el-table td {
  padding: 16px 12px;
  vertical-align: middle;
}

.el-table .el-table__body tr:hover > td {
  background: #f5f7fa;
}

// 开关样式优化
.el-switch {
  --el-switch-on-color: #67c23a;
  --el-switch-off-color: #dcdfe6;
}

// 响应式设计
@media (max-width: 768px) {
  .billboard-management {
    padding: 12px;
  }
  
  .action-section {
    flex-direction: column;
    
    .el-button {
      width: 100%;
    }
  }
  
  .content-cell .content-text {
    -webkit-line-clamp: 2;
    line-clamp: 2;
  }
}
</style>