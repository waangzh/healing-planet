<script setup>
import { ref, onMounted } from 'vue'
import { getPlantPage} from '@/api/plant'
import { ElMessage } from 'element-plus'
import { Search, Picture } from '@element-plus/icons-vue'

// 添加植物种类相关的数据
const plantTypeData = ref([])
const plantTypeDialog = ref(false)
const plantTypeTitle = ref('新增植物种类')
const searchForm = ref({
  pageNum: 1,
  pageSize: 5,  // 每页显示5条
  search: ''
})

// 植物种类表单
const plantTypeForm = ref({
  name: '',
  imgUrl: 'null',
  careInstructions: '',
  id: ''
})

// 植物种类表单规则
const plantTypeRules = {
  name: [{ required: true, message: '请输入植物名称', trigger: 'blur' }],
  careInstructions: [{ required: true, message: '请输入养护说明', trigger: 'blur' }]
}

const plantTypeFormRef = ref(null)

// 添加总数记录
const total = ref(0)

const typeLoading = ref(false)      // 植物种类表格的 loading

// 获取植物种类列表
const getPlantTypePage = async () => {
  try {
    typeLoading.value = true
    const res = await getPlantPage(searchForm.value)
    if (res.data.code === '1') {
      plantTypeData.value = res.data.data.records
      total.value = res.data.data.total
    }
  } catch (error) {
    ElMessage.error('获取植物种类列表失败')
  } finally {
    typeLoading.value = false
  }
}

// 打开新增种类对话框
// const handleAddType = () => {
//   plantTypeTitle.value = '新增植物种类'
//   plantTypeForm.value = {
//     name: '',
//     imgUrl: 'null',
//     careInstructions: ''
//   }
//   plantTypeDialog.value = true
// }

// 提交植物种类表单
// const submitTypeForm = async () => {
//   if (!plantTypeFormRef.value) return
  
//   await plantTypeFormRef.value.validate(async (valid) => {
//     if (valid) {
//       try {
//         if (plantTypeForm.value.id) {
//           // 修改
//           const res = await updatePlant(plantTypeForm.value)
//           if(res.data.code === '1'){
//             ElMessage.success('修改成功')
//           }else{
//             ElMessage.error('修改失败')
//           }
//         } else {
//           // 新增
//           const res = await addPlant(plantTypeForm.value)
//           if(res.data.code === '1'){
//             ElMessage.success('新增成功')
//           }else{
//             ElMessage.error('新增失败')
//           }
//         }
//         plantTypeDialog.value = false
//         getPlantTypePage()
//       } catch (error) {
//         ElMessage.error(plantTypeForm.value.id ? '修改失败' : '新增失败')
//       }
//     }
//   })
// }

// 搜索植物种类
const handleSearch = () => {
  searchForm.value.pageNum = 1
  getPlantTypePage()
}

// 清空搜索
const handleClear = () => {
  searchForm.value.search = ''
  handleSearch()
}

// 处理页码改变
const handleCurrentChange = (val) => {
  searchForm.value.pageNum = val
  getPlantTypePage()
}

// 处理每页条数改变
const handleSizeChange = (val) => {
  searchForm.value.pageSize = val
  searchForm.value.pageNum = 1
  getPlantTypePage()
}

onMounted(() => {
  getPlantTypePage() // 初始加载时获取所有植物种类
})
</script>

<template>
  <div class="plant-manage">
    <el-card class="plant-card mt-4">
      <template #header>
        <div class="card-header">
          <span class="title">植物百科</span>
          <div class="header-right">
            <el-input
              v-model="searchForm.search"
              placeholder="搜索植物名称"
              class="search-input"
              clearable
              @keyup.enter="handleSearch"
              @clear="handleClear"
            >
              <template #append>
                <el-button @click="handleSearch">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-input>
            <!-- <el-button type="primary" @click="handleAddType">
              <el-icon><Plus /></el-icon>新增种类
            </el-button> -->
          </div>
        </div>
      </template>

      <div class="plant-grid" v-loading="typeLoading">
        <el-empty
          v-if="!plantTypeData.length"
          description="暂无植物种类"
        />
        <div v-else class="plant-list">
          <div 
            v-for="plant in plantTypeData" 
            :key="plant.id" 
            class="plant-item"
          >
            <div class="plant-image">
              <el-image
                :src="plant.imgUrl === 'null' ? '/default-plant.jpg' : plant.imgUrl"
                fit="cover"
                :alt="plant.name"
              >
                <template #error>
                  <div class="image-placeholder">
                    <el-icon><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
            </div>
            <div class="plant-content">
              <h3 class="plant-name">{{ plant.name }}</h3>
              <p class="plant-description">{{ plant.careInstructions }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页组件 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="searchForm.pageNum"
          v-model:page-size="searchForm.pageSize"
          :page-sizes="[5, 10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加植物种类对话框 -->
    <el-dialog
      v-model="plantTypeDialog"
      :title="plantTypeTitle"
      width="500px"
      destroy-on-close
      :close-on-click-modal="false"
      class="plant-dialog"
    >
      <el-form
        ref="plantTypeFormRef"
        :model="plantTypeForm"
        :rules="plantTypeRules"
        label-width="100px"
      >
        <el-form-item label="植物名称" prop="name">
          <el-input v-model="plantTypeForm.name" placeholder="请输入植物名称" />
        </el-form-item>
        <el-form-item label="养护说明" prop="careInstructions">
          <el-input
            v-model="plantTypeForm.careInstructions"
            type="textarea"
            :rows="4"
            placeholder="请输入养护说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="plantTypeDialog = false">取消</el-button>
          <el-button type="primary" @click="submitTypeForm">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.plant-manage {
  padding: 20px;
  // background: linear-gradient(135deg, #f6f8ff 0%, #f1f8f1 100%);
  min-height: calc(100vh - 60px);

  .plant-card {
    background: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    border: none;
    border-radius: 12px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);

    .plant-grid {
      min-height: 400px;
      position: relative;

      .plant-list {
        display: flex;
        flex-direction: column;
        gap: 20px;
        padding: 10px;

        .plant-item {
          background: white;
          border-radius: 12px;
          overflow: hidden;
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
          display: flex;
          transition: all 0.3s ease;
          width: 100%;
          height: 300px;

          &:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
          }

          .plant-image {
            width: 200px;
            height: 300px;
            overflow: hidden;

            .el-image {
              width: 100%;
              height: 100%;
              object-fit: cover;
              
              .image-placeholder {
                width: 100%;
                height: 100%;
                display: flex;
                align-items: center;
                justify-content: center;
                background: #f5f7fa;
                color: #909399;
                font-size: 24px;
              }
            }
          }

          .plant-content {
            flex: 1;
            padding: 24px;
            display: flex;
            flex-direction: column;
            height: 300px;

            .plant-name {
              margin: 0 0 16px 0;
              font-size: 20px;
              font-weight: 600;
              color: var(--el-text-color-primary);
            }

            .plant-description {
              flex: 1;
              margin: 0;
              font-size: 14px;
              color: var(--el-text-color-regular);
              line-height: 1.8;
              overflow-y: auto;
              padding-right: 12px;

              &::-webkit-scrollbar {
                width: 6px;
              }

              &::-webkit-scrollbar-track {
                background: #f1f1f1;
                border-radius: 3px;
              }

              &::-webkit-scrollbar-thumb {
                background: #ccc;
                border-radius: 3px;

                &:hover {
                  background: #999;
                }
              }
            }
          }
        }
      }
    }
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
</style>
