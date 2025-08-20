<template>
  <div class="admin-management">
    <div class="page-header">
      <h2 class="page-title">管理员管理</h2>
    </div>
    
    <div class="admin-card">
      <div class="card-body">
        <!-- 操作栏 -->
        <div class="action-bar">
          <el-button type="primary" @click="createAdmin">
            <el-icon><Plus /></el-icon>
            添加管理员
          </el-button>
          <div class="search-box">
            <el-input
              v-model="searchForm.keyword"
              placeholder="搜索管理员用户名或邮箱"
              clearable
              @keyup.enter="searchAdmins"
              style="width: 300px"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select v-model="searchForm.role" placeholder="角色" clearable style="width: 150px; margin-left: 12px">
              <el-option label="超级管理员" value="super_admin" />
              <el-option label="管理员" value="admin" />
              <el-option label="编辑员" value="editor" />
              <el-option label="审核员" value="moderator" />
            </el-select>
            <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 120px; margin-left: 12px">
              <el-option label="正常" value="active" />
              <el-option label="禁用" value="disabled" />
            </el-select>
            <el-button type="primary" @click="searchAdmins" style="margin-left: 12px">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </div>
        </div>
        
        <!-- 统计卡片 -->
        <div class="stats-row">
          <el-row :gutter="16">
            <el-col :xs="12" :sm="6">
              <div class="stat-card primary">
                <div class="stat-icon">
                  <el-icon><User /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.total }}</div>
                  <div class="stat-label">总管理员</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card success">
                <div class="stat-icon">
                  <el-icon><Check /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.active }}</div>
                  <div class="stat-label">正常状态</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card warning">
                <div class="stat-icon">
                  <el-icon><Link /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.online }}</div>
                  <div class="stat-label">在线管理员</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card info">
                <div class="stat-icon">
                  <el-icon><Clock /></el-icon>
                </div>
                <div class="stat-content">
                  <div class="stat-number">{{ stats.todayActive }}</div>
                  <div class="stat-label">今日活跃</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <!-- 管理员列表 -->
        <div class="admin-table">
          <el-table :data="admins" v-loading="loading" stripe>
            <el-table-column prop="avatar" label="头像" width="80">
              <template #default="{ row }">
                <el-avatar :src="row.avatar" :size="40">
                  {{ row.username.charAt(0).toUpperCase() }}
                </el-avatar>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="email" label="邮箱" min-width="180" />
            <el-table-column prop="role" label="角色" width="120">
              <template #default="{ row }">
                <el-tag :type="getRoleTagType(row.role)" size="small">
                  {{ getRoleText(row.role) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="permissions" label="权限" min-width="200">
              <template #default="{ row }">
                <el-tag
                  v-for="permission in row.permissions.slice(0, 3)"
                  :key="permission"
                  size="small"
                  type="info"
                  style="margin-right: 4px;"
                >
                  {{ getPermissionText(permission) }}
                </el-tag>
                <el-tag v-if="row.permissions.length > 3" size="small" type="info">
                  +{{ row.permissions.length - 3 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusTagType(row.status)" size="small">
                  <el-icon><Link /></el-icon>
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastLoginTime" label="最后登录" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.lastLoginTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="text" size="small" @click="viewAdmin(row)">
                  详情
                </el-button>
                <el-button type="text" size="small" @click="editAdmin(row)">
                  编辑
                </el-button>
                <el-button 
                  v-if="row.status === 'active'"
                  type="text" 
                  size="small" 
                  @click="toggleAdminStatus(row, 'disabled')"
                >
                  禁用
                </el-button>
                <el-button 
                  v-else
                  type="text" 
                  size="small" 
                  @click="toggleAdminStatus(row, 'active')"
                >
                  启用
                </el-button>
                <el-button 
                  v-if="row.role !== 'super_admin'"
                  type="text" 
                  size="small" 
                  @click="deleteAdmin(row)"
                >
                  删除
                </el-button>
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
            @size-change="fetchAdmins"
            @current-change="fetchAdmins"
          />
        </div>
      </div>
    </div>
    
    <!-- 创建/编辑管理员对话框 -->
    <el-dialog
      v-model="adminDialog.visible"
      :title="adminDialog.mode === 'create' ? '添加管理员' : '编辑管理员'"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="adminFormRef"
        :model="adminDialog.form"
        :rules="adminFormRules"
        label-width="100px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input 
            v-model="adminDialog.form.username" 
            placeholder="请输入用户名"
            :disabled="adminDialog.mode === 'edit'"
            maxlength="20"
          />
        </el-form-item>
        
        <el-form-item label="邮箱" prop="email">
          <el-input 
            v-model="adminDialog.form.email" 
            placeholder="请输入邮箱地址"
            type="email"
          />
        </el-form-item>
        
        <el-form-item v-if="adminDialog.mode === 'create'" label="密码" prop="password">
          <el-input 
            v-model="adminDialog.form.password" 
            placeholder="请输入密码"
            type="password"
            show-password
            maxlength="50"
          />
        </el-form-item>
        
        <el-form-item label="角色" prop="role">
          <el-select v-model="adminDialog.form.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="管理员" value="admin" />
            <el-option label="编辑员" value="editor" />
            <el-option label="审核员" value="moderator" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="权限" prop="permissions">
          <el-checkbox-group v-model="adminDialog.form.permissions">
            <el-checkbox label="user_management">用户管理</el-checkbox>
            <el-checkbox label="content_management">内容管理</el-checkbox>
            <el-checkbox label="plant_management">植物管理</el-checkbox>
            <el-checkbox label="ai_management">AI管理</el-checkbox>
            <el-checkbox label="system_settings">系统设置</el-checkbox>
            <el-checkbox label="data_analysis">数据分析</el-checkbox>
            <el-checkbox label="message_management">消息管理</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="adminDialog.form.status">
            <el-radio label="active">正常</el-radio>
            <el-radio label="disabled">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="adminDialog.form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="adminDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="saveAdmin">确定</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 管理员详情对话框 -->
    <el-dialog
      v-model="detailDialog.visible"
      title="管理员详情"
      width="600px"
      destroy-on-close
    >
      <div v-if="detailDialog.admin" class="admin-detail">
        <div class="admin-basic-info">
          <div class="admin-avatar">
            <el-avatar :src="detailDialog.admin.avatar" :size="80">
              {{ detailDialog.admin.username.charAt(0).toUpperCase() }}
            </el-avatar>
          </div>
          <div class="admin-info">
            <h3>{{ detailDialog.admin.username }}</h3>
            <p>{{ detailDialog.admin.email }}</p>
            <el-tag :type="getRoleTagType(detailDialog.admin.role)">
              {{ getRoleText(detailDialog.admin.role) }}
            </el-tag>
          </div>
        </div>
        
        <el-divider />
        
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户ID">
            {{ detailDialog.admin.id }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(detailDialog.admin.status)">
              {{ getStatusText(detailDialog.admin.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(detailDialog.admin.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="最后登录">
            {{ formatDateTime(detailDialog.admin.lastLoginTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="登录次数">
            {{ detailDialog.admin.loginCount }}
          </el-descriptions-item>
          <el-descriptions-item label="最后IP">
            {{ detailDialog.admin.lastLoginIp }}
          </el-descriptions-item>
          <el-descriptions-item label="权限列表" :span="2">
            <div class="permission-list">
              <el-tag
                v-for="permission in detailDialog.admin.permissions"
                :key="permission"
                type="info"
                size="small"
                style="margin-right: 8px; margin-bottom: 4px;"
              >
                {{ getPermissionText(permission) }}
              </el-tag>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailDialog.admin.remark" label="备注" :span="2">
            {{ detailDialog.admin.remark }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Plus,
  Search,
  User,
  Check,
  Link,
  Clock
} from '@element-plus/icons-vue'

// 响应式数据
const admins = ref([])
const loading = ref(false)
const adminFormRef = ref()

const searchForm = reactive({
  keyword: '',
  role: '',
  status: ''
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const stats = reactive({
  total: 0,
  active: 0,
  online: 0,
  todayActive: 0
})

const adminDialog = reactive({
  visible: false,
  mode: 'create',
  form: {
    username: '',
    email: '',
    password: '',
    role: '',
    permissions: [],
    status: 'active',
    remark: ''
  }
})

const detailDialog = reactive({
  visible: false,
  admin: null
})

// 表单验证规则
const adminFormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度为6-50字符', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  permissions: [
    { required: true, message: '请选择权限', trigger: 'change' }
  ]
}

// 方法
const getRoleTagType = (role) => {
  const typeMap = {
    super_admin: 'danger',
    admin: 'primary',
    editor: 'success',
    moderator: 'warning'
  }
  return typeMap[role] || 'info'
}

const getRoleText = (role) => {
  const textMap = {
    super_admin: '超级管理员',
    admin: '管理员',
    editor: '编辑员',
    moderator: '审核员'
  }
  return textMap[role] || '未知'
}

const getStatusTagType = (status) => {
  const typeMap = {
    active: 'success',
    disabled: 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    active: '正常',
    disabled: '禁用'
  }
  return textMap[status] || '未知'
}

const getPermissionText = (permission) => {
  const textMap = {
    user_management: '用户管理',
    content_management: '内容管理',
    plant_management: '植物管理',
    ai_management: 'AI管理',
    system_settings: '系统设置',
    data_analysis: '数据分析',
    message_management: '消息管理'
  }
  return textMap[permission] || permission
}

const formatDateTime = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const fetchAdmins = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))
    
    const roles = ['super_admin', 'admin', 'editor', 'moderator']
    const statuses = ['active', 'disabled']
    const permissions = ['user_management', 'content_management', 'plant_management', 'ai_management', 'system_settings', 'data_analysis', 'message_management']
    
    const mockAdmins = Array.from({ length: 50 }, (_, index) => {
      const role = roles[index % roles.length]
      const status = statuses[index % statuses.length]
      const adminPermissions = permissions.slice(0, Math.floor(Math.random() * permissions.length) + 1)
      
      return {
        id: index + 1,
        username: `admin${index + 1}`,
        email: `admin${index + 1}@example.com`,
        avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=admin${index + 1}`,
        role,
        permissions: adminPermissions,
        status,
        loginCount: Math.floor(Math.random() * 1000) + 1,
        lastLoginIp: `192.168.1.${Math.floor(Math.random() * 255)}`,
        lastLoginTime: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
        createdAt: new Date(Date.now() - Math.random() * 365 * 24 * 60 * 60 * 1000).toISOString(),
        remark: index % 3 === 0 ? `备注信息${index + 1}` : ''
      }
    })
    
    // 应用搜索过滤
    let filteredAdmins = mockAdmins
    if (searchForm.keyword) {
      filteredAdmins = filteredAdmins.filter(admin => 
        admin.username.includes(searchForm.keyword) ||
        admin.email.includes(searchForm.keyword)
      )
    }
    if (searchForm.role) {
      filteredAdmins = filteredAdmins.filter(admin => 
        admin.role === searchForm.role
      )
    }
    if (searchForm.status) {
      filteredAdmins = filteredAdmins.filter(admin => 
        admin.status === searchForm.status
      )
    }
    
    // 分页
    const start = (pagination.current - 1) * pagination.size
    const end = start + pagination.size
    
    admins.value = filteredAdmins.slice(start, end)
    pagination.total = filteredAdmins.length
    
    // 更新统计
    stats.total = mockAdmins.length
    stats.active = mockAdmins.filter(a => a.status === 'active').length
    stats.online = Math.floor(stats.active * 0.3)
    stats.todayActive = Math.floor(stats.active * 0.6)
    
  } catch (error) {
    ElMessage.error('获取管理员列表失败')
  } finally {
    loading.value = false
  }
}

const searchAdmins = () => {
  pagination.current = 1
  fetchAdmins()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.role = ''
  searchForm.status = ''
  pagination.current = 1
  fetchAdmins()
}

const createAdmin = () => {
  adminDialog.mode = 'create'
  adminDialog.form = {
    username: '',
    email: '',
    password: '',
    role: '',
    permissions: [],
    status: 'active',
    remark: ''
  }
  adminDialog.visible = true
}

const editAdmin = (admin) => {
  adminDialog.mode = 'edit'
  adminDialog.form = {
    id: admin.id,
    username: admin.username,
    email: admin.email,
    password: '',
    role: admin.role,
    permissions: [...admin.permissions],
    status: admin.status,
    remark: admin.remark || ''
  }
  adminDialog.visible = true
}

const viewAdmin = (admin) => {
  detailDialog.admin = admin
  detailDialog.visible = true
}

const saveAdmin = async () => {
  if (!adminFormRef.value) return
  
  try {
    await adminFormRef.value.validate()
    
    ElMessage.success('管理员保存成功')
    adminDialog.visible = false
    fetchAdmins()
    
  } catch {
    // 验证失败
  }
}

const toggleAdminStatus = async (admin, status) => {
  try {
    const action = status === 'active' ? '启用' : '禁用'
    await ElMessageBox.confirm(
      `确定要${action}管理员 "${admin.username}" 吗？`,
      `确认${action}`,
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: status === 'active' ? 'success' : 'warning'
      }
    )
    
    admin.status = status
    ElMessage.success(`管理员${action}成功`)
    fetchAdmins()
    
  } catch {
    // 用户取消操作
  }
}

const deleteAdmin = async (admin) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除管理员 "${admin.username}" 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    ElMessage.success('管理员删除成功')
    fetchAdmins()
    
  } catch {
    // 用户取消操作
  }
}

// 生命周期
onMounted(() => {
  fetchAdmins()
})
</script>

<style lang="scss" scoped>
.admin-management {
  .action-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    .search-box {
      display: flex;
      align-items: center;
    }
  }
  
  .stats-row {
    margin-bottom: 24px;
  }
  
  .admin-table {
    margin-bottom: 24px;
  }
  
  .pagination-wrapper {
    display: flex;
    justify-content: center;
  }
  
  .admin-detail {
    .admin-basic-info {
      display: flex;
      gap: 20px;
      align-items: center;
      margin-bottom: 20px;
      
      .admin-avatar {
        flex-shrink: 0;
      }
      
      .admin-info {
        h3 {
          margin: 0 0 8px 0;
          color: #303133;
          font-size: 20px;
          font-weight: 600;
        }
        
        p {
          margin: 0 0 8px 0;
          color: #606266;
          font-size: 14px;
        }
      }
    }
    
    .permission-list {
      line-height: 1.8;
    }
  }
}

@media (max-width: 768px) {
  .admin-management {
    .action-bar {
      flex-direction: column;
      gap: 16px;
      align-items: stretch;
      
      .search-box {
        flex-direction: column;
        gap: 12px;
        
        .el-input,
        .el-select {
          width: 100% !important;
        }
      }
    }
    
    .admin-detail {
      .admin-basic-info {
        flex-direction: column;
        text-align: center;
      }
    }
  }
}
</style>
