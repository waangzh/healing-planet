<template>
  <div class="tag-management">
    <div class="page-header">
      <h2 class="page-title">标签管理</h2>
    </div>
    
    <div class="admin-card">
      <div class="card-body">
        <!-- 搜索筛选 -->
        <div class="search-section" v-auto-submit-form="searchTags">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="8">
              <el-input
                v-model="searchForm.keyword"
                placeholder="搜索标签名称"
                clearable
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-col>
            <el-col :xs="24" :sm="24" :md="16">
              <div class="actions-container">
                <div class="left-actions">
                  <el-button type="primary" @click="searchTags">搜索</el-button>
                  <el-button @click="resetSearch">重置</el-button>
                </div>
                <div class="right-actions">
                  <el-button type="primary" @click="addTag">
                    <el-icon><Plus /></el-icon>
                    添加标签
                  </el-button>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <!-- 标签列表 -->
        <div class="tag-list">
          <div v-loading="loading" class="tag-grid">
            <div
              v-for="tag in filteredTags"
              :key="tag.id"
              class="tag-card"
            >
              <div class="tag-content">
                <div class="tag-info">
                  <el-tag type="primary" size="large" class="tag-display">
                    # {{ tag.name }}
                  </el-tag>
                  <div class="tag-id">ID: {{ tag.id }}</div>
                </div>
                <div class="tag-actions">
                  <el-button
                    type="primary"
                    size="small"
                    circle
                    @click="editTag(tag)"
                    title="编辑"
                  >
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button
                    type="danger"
                    size="small"
                    circle
                    @click="deleteTag(tag)"
                    title="删除"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
            
            <!-- 空状态 -->
            <div v-if="filteredTags.length === 0" class="empty-state">
              <el-empty description="暂无标签数据" />
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 添加/编辑标签对话框 -->
    <el-dialog
      v-model="tagDialog.visible"
      :title="tagDialog.isEdit ? '编辑标签' : '添加标签'"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="tagFormRef"
        :model="tagDialog.form"
        :rules="tagFormRules"
        label-width="80px"
        v-auto-submit-form="saveTag"
      >
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="tagDialog.form.name" placeholder="请输入标签名称" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="tagDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Plus, 
  Search,
  Edit,
  Delete
} from '@element-plus/icons-vue'
import { getTagsList, addTag as addTagApi, updateTag as updateTagApi, deleteTag as deleteTagApi } from '@/api/tag'

// 响应式数据
const tags = ref([])
const loading = ref(false)
const saveLoading = ref(false)

const searchForm = reactive({
  keyword: ''
})

const tagDialog = reactive({
  visible: false,
  isEdit: false,
  form: {
    id: null,
    name: '',
    category: 1 // 类别固定为1
  }
})

const tagFormRef = ref(null)

// 表单验证规则
const tagFormRules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { min: 1, max: 20, message: '标签名称长度在 1 到 20 个字符', trigger: 'blur' }
  ]
}

// 获取过滤后的标签列表
const filteredTags = computed(() => {
  if (!searchForm.keyword) {
    return tags.value
  }
  return tags.value.filter(tag => 
    tag.name.toLowerCase().includes(searchForm.keyword.toLowerCase())
  )
})

// 方法
const fetchTags = async () => {
  try {
    loading.value = true
    const response = await getTagsList()
    if (response.data.code === 200) {
      tags.value = response.data.data
    } else {
      ElMessage.error(response.data.message || '获取标签列表失败')
    }
  } catch (error) {
    console.error('获取标签列表失败:', error)
    ElMessage.error('获取标签列表失败')
  } finally {
    loading.value = false
  }
}

const searchTags = () => {
  // 前端过滤，因为API不支持搜索参数
  // filteredTags 计算属性会自动更新
}

const resetSearch = () => {
  searchForm.keyword = ''
}

const addTag = () => {
  tagDialog.isEdit = false
  tagDialog.form = {
    id: null,
    name: '',
    category: 1
  }
  tagDialog.visible = true
}

const editTag = (tag) => {
  tagDialog.isEdit = true
  tagDialog.form = { 
    id: tag.id,
    name: tag.name,
    category: 1
  }
  tagDialog.visible = true
}

const deleteTag = async (tag) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除标签 "${tag.name}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    loading.value = true
    const response = await deleteTagApi([tag.id])
    
    if (response.data.code === 200) {
      ElMessage.success('标签删除成功')
      fetchTags()
    } else {
      ElMessage.error(response.data.message || '删除失败')
    }
    
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除标签失败:', error)
      ElMessage.error('删除失败，请重试')
    }
  } finally {
    loading.value = false
  }
}

const saveTag = async () => {
  try {
    await tagFormRef.value.validate()
    
    saveLoading.value = true
    
    let response
    if (tagDialog.isEdit) {
      response = await updateTagApi(tagDialog.form)
    } else {
      response = await addTagApi({
        name: tagDialog.form.name,
        category: 1
      })
    }
    
    if (response.data.code === 200) {
      ElMessage.success(tagDialog.isEdit ? '标签更新成功' : '标签创建成功')
      tagDialog.visible = false
      fetchTags()
    } else {
      ElMessage.error(response.data.message || '保存失败')
    }
    
  } catch (error) {
    if (error !== false) {
      console.error('保存标签失败:', error)
      ElMessage.error('保存失败，请重试')
    }
  } finally {
    saveLoading.value = false
  }
}

// 生命周期
onMounted(() => {
  fetchTags()
})
</script>

<style lang="scss" scoped>
.tag-management {
  .search-section {
    margin-bottom: 24px;
    
    .actions-container {
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;
      
      .left-actions {
        display: flex;
        gap: 8px;
        align-items: center;
      }
    }
  }
  
  .tag-list {
    .tag-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 16px;
      
      .tag-card {
        background: white;
        border: 1px solid #e4e7ed;
        border-radius: 8px;
        padding: 16px;
        transition: all 0.3s ease;
        
        &:hover {
          border-color: #409eff;
          box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
        }
        
        .tag-content {
          display: flex;
          justify-content: space-between;
          align-items: center;
          
          .tag-info {
            flex: 1;
            
            .tag-display {
              font-size: 16px;
              font-weight: 600;
              margin-bottom: 8px;
            }
            
            .tag-id {
              font-size: 12px;
              color: #909399;
            }
          }
          
          .tag-actions {
            display: flex;
            gap: 8px;
            margin-left: 16px;
          }
        }
      }
      
      .empty-state {
        grid-column: 1 / -1;
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 200px;
      }
    }
  }
}

@media (max-width: 768px) {
  .tag-management {
    .search-section {
      .actions-container {
        flex-direction: column;
        align-items: stretch;
        gap: 8px;
        
        .right-actions {
          display: flex;
          justify-content: flex-end;
        }
      }
      .el-col {
        margin-bottom: 12px;
      }
    }
    
    .tag-list {
      .tag-grid {
        grid-template-columns: 1fr;
        
        .tag-card {
          .tag-content {
            flex-direction: column;
            align-items: flex-start;
            gap: 12px;
            
            .tag-actions {
              margin-left: 0;
              align-self: flex-end;
            }
          }
        }
      }
    }
  }
}
</style>
