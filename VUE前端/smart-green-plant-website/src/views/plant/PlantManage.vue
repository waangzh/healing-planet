<script setup>
import { ref, onMounted } from 'vue'
import { getPlantInstanceId, addPlantInstance, updatePlantInstance, deletePlantInstance } from '@/api/plantinstance'
import { getPlantPage } from '@/api/plant'
import { useUserStore } from '@/stores'
import { ElMessage, ElMessageBox } from 'element-plus'
import {  Plus, Edit, Delete, Picture } from '@element-plus/icons-vue'

const userStore = useUserStore()
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增植物')
const userIp = ref('')  // 添加 IP 存储变量

// 表单数据
const formData = ref({
  userId: userStore.user.id,
  plantId: '',
  id: ''
})

// 表单规则
const rules = {
  plantId: [{ required: true, message: '请选择植物种类', trigger: 'change' }]
}

const formRef = ref(null)

// 植物种类选项
const plantOptions = ref([])

const instanceLoading = ref(false)  // 植物实例表格的 loading

// 获取植物列表
const getPlantList = async () => {
  try {
    instanceLoading.value = true
    // 先获取植物种类列表
    const plantRes = await getPlantPage({
      pageNum: 1,
      pageSize: 1000,
      search: ''
    })
    if (plantRes.data.code === '1') {
      plantOptions.value = plantRes.data.data.records.map(item => ({
        value: item.id,
        label: item.name
      }))
    }

    // 获取植物实例列表
    const instanceRes = await getPlantInstanceId(userStore.user.id)
    if (instanceRes.data.code === '1') {
      tableData.value = instanceRes.data.data.map(item => ({
        id: item.id,
        plantId: item.plantId,
        plantName: item.plantName,
        location: item.location,
        deviceName: item.deviceName,
        imgUrl: item.imgUrl,
        datePlanted: item.datePlanted
      }))
    }
  } catch (error) {
    ElMessage.error('获取植物列表失败')
  } finally {
    instanceLoading.value = false
  }
}

// 打开新增对话框
const handleAdd = () => {
  dialogTitle.value = '新增植物'
  formData.value = {
    // userId: userStore.user.id,
    plantId: '',
    // id: ''
  }
  dialogVisible.value = true
}

// 打开编辑对话框
const handleEdit = (row) => {
  dialogTitle.value = '编辑植物'
  formData.value = {
    // id: row.id,
    // ip: userIp.value,
    plantId: row.plantId, // 使用 plantId 而不是 id
    // userId: userStore.user.id
  }
  dialogVisible.value = true
}

// 删除植物
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除该植物?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deletePlantInstance(row.id)
    ElMessage.success('删除成功')
    getPlantList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (formData.value.id) {
          // 修改
          const updateData = {
            // id: formData.value.id,
            plantId: formData.value.plantId, // 使用选择的植物 id
            // ip: formData.value.ip
          }
          const res = await updatePlantInstance(updateData)
          if (res.data.code === '1') {
            ElMessage.success('修改成功')
            dialogVisible.value = false
            getPlantList() // 刷新列表
          } else {
            ElMessage.error('修改失败')
          }
        } else {
          // 新增 - 添加当前时间
          const submitData = {
            ...formData.value,
            // datePlanted: new Date().toISOString().slice(0, 19).replace('T', ' ') // 格式化为 YYYY-MM-DD HH:mm:ss
          }
          const res = await addPlantInstance(submitData)
          if (res.data.code === '1') {
            ElMessage.success('新增成功')
          } else {
            ElMessage.error('新增失败')
          }
        }
        dialogVisible.value = false
        getPlantList()
      } catch (error) {
        ElMessage.error('操作失败')
      }
    }
  })
}

// 获取用户 IP 地址
const getUserIp = async () => {
  try {
    const response = await fetch('http://ip-api.com/json')
    const data = await response.json()
    userIp.value = data.query
  } catch (error) {
    console.error('获取IP地址失败:', error)
    userIp.value = '127.0.0.1' // 设置默认IP
  }
}

onMounted(async () => {
  await getUserIp()
  getPlantList()
})
</script>

<template>
  <div class="plant-manage">
    <el-card class="plant-card">
      <template #header>
        <div class="card-header">
          <span class="title">我的绿植</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增植物
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="instanceLoading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
        element-loading-text="加载中..."
        element-loading-background="rgba(255, 255, 255, 0.9)"
      >
        <el-empty
          v-if="!tableData.length"
          description="暂无植物数据"
        />
        <el-table-column 
          prop="plantName" 
          label="植物名称" 
          align="center"
        />
        <el-table-column 
          label="植物图片" 
          align="center"
          width="120"
        >
          <template #default="{ row }">
            <div class="plant-image-container">
              <el-image
                v-if="row.imgUrl"
                :src="row.imgUrl"
                :alt="row.plantName"
                fit="cover"
                class="plant-image"
                :preview-src-list="[row.imgUrl]"
                preview-teleported
              >
                <template #error>
                  <div class="image-error">
                    <el-icon><Picture /></el-icon>
                    <span>图片加载失败</span>
                  </div>
                </template>
              </el-image>
              <div v-else class="no-image">
                <el-icon><Picture /></el-icon>
                <span>暂无图片</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column 
          prop="deviceName" 
          label="设备" 
          align="center"
        />
        <el-table-column 
          prop="location" 
          label="位置" 
          align="center"
        />
        <el-table-column 
          prop="datePlanted" 
          label="种植日期" 
          align="center" 
          width="180"
        />
        <el-table-column 
          label="操作" 
          width="200" 
          align="center"
        >
          <template #default="{ row }">
            <el-button 
              type="primary" 
              link 
              @click="handleEdit(row)"
            >
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button 
              type="danger" 
              link 
              @click="handleDelete(row)"
            >
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

    </el-card>
    <!-- 新增/编辑对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="500px"
        destroy-on-close
        :close-on-click-modal="false"
        class="plant-dialog"
      >
        <el-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-width="100px"
          class="plant-form"
        >
          <el-form-item label="植物种类" prop="plantId">
            <el-select
              v-model="formData.plantId"
              placeholder="请选择植物种类"
              style="width: 100%"
              popper-class="plant-select-dropdown"
            >
              <el-option
                v-for="item in plantOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitForm">确定</el-button>
          </div>
        </template>
      </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.plant-manage {
  padding: 20px;

  .plant-card {
    margin-bottom: 50px;
    background: rgba(255, 255, 255, 0.9);
    border-radius: 12px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 0;

      .title {
        font-size: 18px;
        font-weight: bold;
        color: var(--el-text-color-primary);
      }
    }

    :deep(.el-table) {
      border-radius: 8px;
      overflow: hidden;

      th {
        background-color: var(--el-color-primary-light-9);
        color: var(--el-text-color-primary);
        font-weight: bold;
      }

      .el-button {
        padding: 4px 8px;
        .el-icon {
          margin-right: 4px;
        }
      }

      // 植物图片样式
      .plant-image-container {
        display: flex;
        justify-content: center;
        align-items: center;
        width: 100%;
        height: 80px;

        .plant-image {
          width: 70px;
          height: 70px;
          border-radius: 8px;
          border: 2px solid var(--el-border-color-light);
          transition: all 0.3s ease;
          cursor: pointer;

          &:hover {
            border-color: var(--el-color-primary);
            transform: scale(1.05);
          }

          :deep(.el-image__inner) {
            border-radius: 6px;
          }
        }

        .image-error, .no-image {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          width: 70px;
          height: 70px;
          background-color: var(--el-fill-color-light);
          border: 2px dashed var(--el-border-color);
          border-radius: 8px;
          color: var(--el-text-color-placeholder);
          font-size: 12px;

          .el-icon {
            font-size: 20px;
            margin-bottom: 4px;
          }

          span {
            font-size: 10px;
            text-align: center;
            line-height: 1.2;
          }
        }
      }
    }
  }
}

:deep(.plant-dialog) {
  border-radius: 12px;
  overflow: hidden;

  .el-dialog__header {
    margin: 0;
    padding: 20px;
    background-color: var(--el-color-primary-light-9);
    border-bottom: 1px solid var(--el-border-color-light);
    
    .el-dialog__title {
      font-size: 18px;
      font-weight: bold;
      color: var(--el-color-primary);
    }
  }

  .el-dialog__body {
    padding: 30px 20px;
    max-height: 60vh;
    overflow-y: auto;
  }

  .plant-form {
    .el-form-item {
      margin-bottom: 25px;
      
      &:last-child {
        margin-bottom: 0;
      }

      .el-form-item__label {
        font-weight: 500;
        color: var(--el-text-color-primary);
      }

      .el-select, .el-date-picker {
        width: 100%;
        
        .el-input__wrapper {
          background-color: var(--el-fill-color-blank);
          box-shadow: 0 0 0 1px var(--el-border-color) inset;
          
          &:hover {
            box-shadow: 0 0 0 1px var(--el-color-primary) inset;
          }
        }
      }
    }
  }

  .dialog-footer {
    padding: 10px 0;
    display: flex;
    justify-content: flex-end;
    gap: 12px;

    .el-button {
      padding: 8px 20px;
      font-size: 14px;
      border-radius: 4px;
      
      &--primary {
        background: var(--el-color-primary);
        border-color: var(--el-color-primary);
        
        &:hover {
          background: var(--el-color-primary-light-3);
          border-color: var(--el-color-primary-light-3);
        }
      }
    }
  }
}

// 下拉菜单样式
:deep(.plant-select-dropdown) {
  border-radius: 8px;
  padding: 6px;
  
  .el-select-dropdown__item {
    padding: 8px 12px;
    border-radius: 4px;
    
    &.selected {
      background-color: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
      font-weight: bold;
    }
    
    &:hover {
      background-color: var(--el-color-primary-light-9);
    }
  }
}

// 日期选择器下拉样式
:deep(.plant-date-dropdown) {
  border-radius: 8px;
  padding: 8px;
  
  .el-date-picker__header {
    margin: 8px 0;
  }
  
  .el-picker-panel__content {
    margin: 0;
    padding: 8px;
  }
}

.mt-4 {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;

  .title {
    font-size: 18px;
    font-weight: bold;
    color: var(--el-text-color-primary);
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .search-input {
      width: 300px;
      
      :deep(.el-input-group__append) {
        padding: 0;
        
        .el-button {
          margin: 0;
          border: none;
          height: 32px;
          padding: 0 16px;
          border-radius: 0 4px 4px 0;
          
          &:hover {
            background-color: var(--el-color-primary);
            color: white;
          }
          
          .el-icon {
            margin: 0;
          }
        }
      }
    }
  }
}

:deep(.el-empty) {
  padding: 40px 0;
  
  .el-empty__description {
    margin-top: 10px;
    color: var(--el-text-color-secondary);
  }
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  padding: 10px 0;
  
  :deep(.el-pagination) {
    padding: 0;
    margin: 0;
    font-weight: normal;
    
    .el-pagination__total {
      margin-right: 16px;
    }
    
    .el-pagination__sizes {
      margin-right: 16px;
    }
  }
}

:deep(.el-loading-mask) {
  border-radius: 8px;  // 保持与表格相同的圆角
  
  .el-loading-spinner {
    .el-loading-text {
      color: var(--el-color-primary);
      font-size: 14px;
      margin-top: 8px;
    }
    
    .circular {
      .path {
        stroke: var(--el-color-primary);  // 使用主题色
      }
    }
  }
}

// 图片预览样式
:deep(.el-image-viewer__wrapper) {
  .el-image-viewer__mask {
    background-color: rgba(0, 0, 0, 0.8);
  }
  
  .el-image-viewer__btn {
    background-color: rgba(255, 255, 255, 0.8);
    color: var(--el-text-color-primary);
    
    &:hover {
      background-color: rgba(255, 255, 255, 0.9);
    }
  }
  
  .el-image-viewer__canvas {
    img {
      border-radius: 8px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
    }
  }
}
</style>
