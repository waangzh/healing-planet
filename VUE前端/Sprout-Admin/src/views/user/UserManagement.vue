<template>
  <div class="user-management">
    <div class="page-title">用户管理</div>
    
    <!-- 搜索和操作区域 -->
    <div class="admin-card">
      <div class="card-body">
        <div class="search-section">
          <!-- 单行工具条 -->
          <div class="search-toolbar" v-auto-submit-form="handleSearch">
            <el-input
              v-model="searchForm.username"
              placeholder="搜索用户名..."
              :prefix-icon="Search"
              clearable
              @input="handleSearch"
              class="w-220"
            />
            <el-input
              v-model="searchForm.alias"
              placeholder="搜索昵称..."
              clearable
              @input="handleSearch"
              class="w-220"
            />
            <el-select
              v-model="searchForm.status"
              placeholder="状态"
              clearable
              @change="handleFilter"
              class="w-140"
            >
              <el-option label="全部" :value="null" />
              <el-option label="正常" :value="true" />
              <el-option label="禁用" :value="false" />
            </el-select>
            <el-select
              v-model="searchForm.postCount"
              placeholder="发帖数"
              clearable
              @change="handleFilter"
              class="w-140"
            >
              <el-option label="1+ 篇" :value="1" />
              <el-option label="5+ 篇" :value="5" />
              <el-option label="10+ 篇" :value="10" />
              <el-option label="20+ 篇" :value="20" />
              <el-option label="50+ 篇" :value="50" />
            </el-select>
            <el-select
              v-model="searchForm.followerCount"
              placeholder="粉丝数"
              clearable
              @change="handleFilter"
              class="w-140"
            >
              <el-option label="10+ 人" :value="10" />
              <el-option label="50+ 人" :value="50" />
              <el-option label="100+ 人" :value="100" />
              <el-option label="500+ 人" :value="500" />
              <el-option label="1000+ 人" :value="1000" />
            </el-select>
            <el-select
              v-model="searchForm.followingCount"
              placeholder="关注数"
              clearable
              @change="handleFilter"
              class="w-140"
            >
              <el-option label="5+ 人" :value="5" />
              <el-option label="20+ 人" :value="20" />
              <el-option label="50+ 人" :value="50" />
              <el-option label="100+ 人" :value="100" />
              <el-option label="500+ 人" :value="500" />
            </el-select>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <div class="toolbar-actions">
              <el-button type="primary" :icon="Plus" @click="showCreateDialog">
                新增用户
              </el-button>
              <el-button :icon="Refresh" @click="refreshData">刷新</el-button>
              <el-button 
                v-if="selectedUsers.length > 0" 
                type="danger" 
                :icon="Delete"
                @click="handleBatchDelete"
              >
                批量删除 ({{ selectedUsers.length }})
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 用户列表 -->
    <div class="admin-card">
      <div class="card-body">
        <el-table
          v-loading="loading"
          :data="users"
          style="width: 100%"
          class="admin-table"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55" />
          <!-- 去掉单独 ID 列，仅在查看详情时展示 -->
          <el-table-column label="用户信息" width="200">
            <template #default="{ row }">
              <div class="user-info">
                <el-avatar 
                  :src="row.avatar" 
                  :size="40"
                  @error="handleAvatarError"
                >
                  <img src="https://cube.elemecdn.com/e/fd/0fc7d20532fdaf769a25683617711png.png" />
                </el-avatar>
                <div class="user-details">
                  <div class="username">{{ row.username }}</div>
                  <!-- 去除昵称 alias 显示 -->
                  <div class="email">{{ row.email }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="联系方式" width="220">
            <template #default="{ row }">
              <div class="contact-box">
                <div class="contact-item">
                  <span class="contact-label">手机号:</span>
                  <span>{{ row.mobile || '-' }}</span>
                </div>
                <div class="contact-item">
                  <span class="contact-label">邮箱:</span>
                  <span>{{ row.email || '-' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="bio" label="个人简介" width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag 
                :type="row.status ? 'success' : 'danger'"
                size="small"
              >
                {{ row.status ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="激活" width="80" align="center">
            <template #default="{ row }">
              <el-tag 
                :type="row.active ? 'success' : 'warning'"
                size="small"
              >
                {{ row.active ? '已激活' : '未激活' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="身份" width="90" align="center">
            <template #default="{ row }">
              <!-- 假设后端未来提供 role 字段；当前临时用 username === 'admin' 判断 -->
              <el-tag :type="(row.role || row.username === 'admin') ? 'danger' : 'info'" size="small">
                {{ (row.role === 'admin' || row.username === 'admin') ? '管理员' : '用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="统计信息" width="90">
            <template #default="{ row }">
              <div class="user-stats">
                <div class="stat-item">
                  <span class="stat-label">发帖:</span>
                  <span class="stat-value">{{ row.postCount }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">粉丝:</span>
                  <span class="stat-value">{{ row.followerCount }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">关注:</span>
                  <span class="stat-value">{{ row.followingCount }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">积分:</span>
                  <span class="stat-value">{{ row.score }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="注册时间" width="100">
            <template #default="{ row }">
              {{ formatDate(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="200" fixed="right">
            <template #default="{ row }">
              <el-button 
                type="primary" 
                size="small" 
                @click="viewUser(row)"
              >
                查看
              </el-button>
              <el-button 
                size="small" 
                @click="editUser(row)"
              >
                编辑
              </el-button>
              <el-button 
                :type="row.status ? 'warning' : 'success'" 
                size="small" 
                @click="toggleUserStatus(row)"
              >
                {{ row.status ? '禁用' : '启用' }}
              </el-button>
              <el-popconfirm
                title="确定要删除这个用户吗？"
                @confirm="deleteUser(row)"
              >
                <template #reference>
                  <el-button 
                    type="danger" 
                    size="small"
                  >
                    删除
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="pagination.pageNo"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>
    
    <!-- 用户详情抽屉 -->
    <el-drawer
      v-model="userDetailDrawer"
      title="用户详情"
      size="600px"
    >
      <div v-if="selectedUser" class="user-detail-content">
        <!-- 用户头像展示区域 -->
        <div class="detail-section user-avatar-section">
          <div class="avatar-display">
            <el-avatar 
              :src="selectedUser.avatar" 
              :size="120"
              @error="handleAvatarError"
            >
              <img src="https://cube.elemecdn.com/e/fd/0fc7d20532fdaf769a25683617711png.png" />
            </el-avatar>
            <div class="user-basic-info">
              <h2 class="user-display-name">{{ selectedUser.alias || selectedUser.username }}</h2>
              <p class="user-username">@{{ selectedUser.username }}</p>
              <div class="user-status-tags">
                <el-tag :type="selectedUser.status ? 'success' : 'danger'" size="small">
                  {{ selectedUser.status ? '正常' : '禁用' }}
                </el-tag>
                <el-tag :type="selectedUser.active ? 'success' : 'warning'" size="small">
                  {{ selectedUser.active ? '已激活' : '未激活' }}
                </el-tag>
                <el-tag :type="(selectedUser.role === 'admin' || selectedUser.username === 'admin') ? 'danger' : 'info'" size="small">
                  {{ (selectedUser.role === 'admin' || selectedUser.username === 'admin') ? '管理员' : '用户' }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
        
        <div class="detail-section">
          <h3>详细信息</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户ID">{{ selectedUser.id }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ selectedUser.email }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ selectedUser.mobile || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="个人简介">{{ selectedUser.bio || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="个性签名">{{ selectedUser.message || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatDate(selectedUser.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="最后修改">{{ formatDate(selectedUser.modifyTime) }}</el-descriptions-item>
          </el-descriptions>
        </div>
        
        <div class="detail-section">
          <h3>统计信息</h3>
          <el-row :gutter="16">
            <el-col :span="6">
              <div class="stat-item">
                <div class="stat-number">{{ selectedUser.postCount }}</div>
                <div class="stat-label">发帖数</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-item">
                <div class="stat-number">{{ selectedUser.followerCount }}</div>
                <div class="stat-label">粉丝数</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-item">
                <div class="stat-number">{{ selectedUser.followingCount }}</div>
                <div class="stat-label">关注数</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-item">
                <div class="stat-number">{{ selectedUser.score }}</div>
                <div class="stat-label">积分</div>
              </div>
            </el-col>
          </el-row>
        </div>
      </div>
    </el-drawer>
    
    <!-- 编辑用户对话框 -->
    <el-dialog
      v-model="editUserDialog"
      title="编辑用户"
      width="600px"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
        class="admin-form"
        v-auto-submit-form="handleUpdateUser"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="alias">
          <el-input v-model="editForm.alias" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="editForm.mobile" />
        </el-form-item>
        <el-form-item label="个人简介" prop="bio">
          <el-input 
            v-model="editForm.bio" 
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="个性签名" prop="message">
          <el-input 
            v-model="editForm.message"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="头像" prop="avatar">
          <div class="avatar-upload-section">
            <div class="avatar-wrapper">
              <img 
                :src="editForm.avatar || 'https://cube.elemecdn.com/e/fd/0fc7d20532fdaf769a25683617711png.png'" 
                alt="用户头像" 
                class="avatar-preview"
                @error="handleAvatarError"
              >
              <div class="avatar-overlay">
                <el-upload
                  class="avatar-uploader"
                  :show-file-list="false"
                  :before-upload="beforeAvatarUpload"
                  :auto-upload="false"
                  @change="handleAvatarFileChange"
                >
                  <div class="upload-mask">
                    <el-icon><Plus /></el-icon>
                    <span>更换头像</span>
                  </div>
                </el-upload>
              </div>
            </div>
            <!-- <el-input 
              v-model="editForm.avatar" 
              placeholder="或输入头像URL" 
              class="avatar-url-input"
              clearable
            /> -->
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editUserDialog = false">取消</el-button>
          <el-button type="primary" @click="handleUpdateUser" :loading="updateLoading">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 新增用户对话框 -->
    <el-dialog
      v-model="createUserDialog"
      title="新增用户"
      width="500px"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="80px"
        class="admin-form"
        v-auto-submit-form="handleCreateUser"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="checkPass">
          <el-input v-model="createForm.checkPass" type="password" show-password />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="createForm.email" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="createUserDialog = false">取消</el-button>
          <el-button type="primary" @click="handleCreateUser" :loading="createLoading">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 头像裁剪对话框 -->
    <el-dialog
      v-model="cropperVisible"
      title="裁剪头像"
      width="600px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="cropper-container">
        <VueCropper
          ref="cropperRef"
          :img="cropperImg"
          :info="true"
          :outputSize="1"
          :outputType="'png'"
          :autoCrop="true"
          :autoCropWidth="200"
          :autoCropHeight="200"
          :fixedBox="true"
          :full="false"
          :canMove="false"
          :canMoveBox="true"
          :original="false"
          :centerBox="false"
          :height="400"
          :infoTrue="true"
          :fixed="true"
          :fixedNumber="[1, 1]"
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cropperVisible = false">取消</el-button>
          <el-button type="primary" @click="handleCropFinish" :loading="uploadLoading">
            确定并上传
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Refresh, Delete } from '@element-plus/icons-vue'
import { getUserList, updateUser, createUser, deleteUsers } from '@/api/user'
import { uploadFileService } from '@/api/common'
import { VueCropper } from 'vue-cropper'
import 'vue-cropper/dist/index.css'

// 响应式数据
const loading = ref(false)
const updateLoading = ref(false)
const createLoading = ref(false)
const uploadLoading = ref(false)
const userDetailDrawer = ref(false)
const editUserDialog = ref(false)
const createUserDialog = ref(false)
const selectedUser = ref(null)
const editFormRef = ref()
const createFormRef = ref()

// 头像裁剪相关
const cropperRef = ref(null)
const cropperVisible = ref(false)
const cropperImg = ref('')

// 搜索表单
const searchForm = reactive({
  username: '',
  alias: '',
  status: null,
  postCount: null,
  followingCount: null,
  followerCount: null
})

// 分页数据
const pagination = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0
})

// 用户列表数据
const users = ref([])
const selectedUsers = ref([])

// 编辑表单
const editForm = reactive({
  username: '',
  alias: '',
  avatar: '',
  email: '',
  mobile: '',
  bio: '',
  message: ''
})

// 创建表单
const createForm = reactive({
  username: '',
  password: '',
  checkPass: '',
  email: ''
})

const editRules = {
  alias: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const createRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  checkPass: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== createForm.password) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

// 方法
const handleSearch = () => {
  pagination.pageNo = 1
  fetchUsers()
}

const handleFilter = () => {
  pagination.pageNo = 1
  fetchUsers()
}

const handleSelectionChange = (selection) => {
  selectedUsers.value = selection
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.pageNo = 1
  fetchUsers()
}

const handlePageChange = (page) => {
  pagination.pageNo = page
  fetchUsers()
}

const refreshData = () => {
  fetchUsers()
}

const showCreateDialog = () => {
  // 重置表单
  Object.assign(createForm, {
    username: '',
    password: '',
    checkPass: '',
    email: ''
  })
  createUserDialog.value = true
  nextTick(() => {
    createFormRef.value?.clearValidate()
  })
}

const viewUser = (user) => {
  selectedUser.value = user
  userDetailDrawer.value = true
}

const editUser = (user) => {
  Object.assign(editForm, {
    username: user.username,
    alias: user.alias,
    avatar: user.avatar,
    email: user.email,
    mobile: user.mobile,
    bio: user.bio,
    message: user.message
  })
  editUserDialog.value = true
  nextTick(() => {
    editFormRef.value?.clearValidate()
  })
}

const toggleUserStatus = async (user) => {
  const action = user.status ? '禁用' : '启用'
  
  try {
    await ElMessageBox.confirm(
      `确定要${action}用户 "${user.username}" 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 调用用户更新接口，只传递 username 和 status
    await updateUser({
      username: user.username,
      status: !user.status
    })
    
    // 更新本地状态
    user.status = !user.status
    ElMessage.success(`${action}成功`)
    
  } catch (error) {
    if (error !== 'cancel') {
      console.error('状态切换失败:', error)
      ElMessage.error(`${action}失败`)
    }
  }
}

const deleteUser = async (user) => {
  try {
    await deleteUsers([user.id])
    ElMessage.success('删除成功')
    fetchUsers()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const handleBatchDelete = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请选择要删除的用户')
    return
  }
  
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedUsers.value.length} 个用户吗？`,
      '批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const userIds = selectedUsers.value.map(user => user.id)
    await deleteUsers(userIds)
    ElMessage.success('批量删除成功')
    fetchUsers()
    
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

const handleUpdateUser = async () => {
  if (!editFormRef.value) return
  
  try {
    await editFormRef.value.validate()
    updateLoading.value = true
    
    await updateUser(editForm)
    ElMessage.success('更新成功')
    editUserDialog.value = false
    fetchUsers()
    
  } catch (error) {
    if (error !== false) { // 不是表单验证错误
      ElMessage.error('更新失败')
    }
  } finally {
    updateLoading.value = false
  }
}

const handleCreateUser = async () => {
  if (!createFormRef.value) return
  
  try {
    await createFormRef.value.validate()
    createLoading.value = true
    
    await createUser(createForm)
    ElMessage.success('创建成功')
    createUserDialog.value = false
    fetchUsers()
    
  } catch (error) {
    if (error !== false) { // 不是表单验证错误
      ElMessage.error('创建失败')
    }
  } finally {
    createLoading.value = false
  }
}

const handleAvatarError = () => {
  // 头像加载失败时的处理
  return true
}

const formatDate = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// 头像上传相关方法
const beforeAvatarUpload = (file) => {
  return false // 阻止自动上传，使用裁剪
}

const handleAvatarFileChange = (uploadFile) => {
  const file = uploadFile.raw
  if (!file) return

  // 验证文件类型和大小
  const isValidType = ['image/jpeg', 'image/png', 'image/gif'].includes(file.type)
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isValidType) {
    ElMessage.error('头像只能是 JPG/PNG/GIF 格式!')
    return
  }
  if (!isLt2M) {
    ElMessage.error('头像大小不能超过 2MB!')
    return
  }

  // 创建预览并显示裁剪器
  const reader = new FileReader()
  reader.onload = (e) => {
    cropperImg.value = e.target.result
    cropperVisible.value = true
  }
  reader.readAsDataURL(file)
}

const handleCropFinish = () => {
  console.log('handleCropFinish 被调用')
  console.log('cropperRef.value:', cropperRef.value)
  
  if (!cropperRef.value) {
    ElMessage.error('裁剪器未初始化')
    return
  }
  
  uploadLoading.value = true
  
  try {
    cropperRef.value.getCropData((data) => {
      console.log('获取到裁剪数据:', data ? '有数据' : '无数据')
      uploadCroppedAvatar(data)
    })
  } catch (error) {
    console.error('获取裁剪数据失败:', error)
    ElMessage.error('获取裁剪数据失败')
    uploadLoading.value = false
  }
}

const uploadCroppedAvatar = async (data) => {
  try {
    // 将base64转换为文件
    const file = dataURLtoFile(data, 'avatar.png')
    console.log('转换后的文件:', file)
    
    const res = await uploadFileService(file)
    console.log('上传响应:', res)
    
    if (res.data && res.data.code === 200) {
      editForm.avatar = res.data.data
      ElMessage.success('头像上传成功')
      cropperVisible.value = false
    } else {
      ElMessage.error(res.data?.message || '头像上传失败')
    }
  } catch (error) {
    console.error('头像上传失败:', error)
    ElMessage.error('头像上传失败: ' + (error.message || '未知错误'))
  } finally {
    uploadLoading.value = false
  }
}

// base64转文件
const dataURLtoFile = (dataurl, filename) => {
  const arr = dataurl.split(',')
  const mime = arr[0].match(/:(.*?);/)[1]
  const bstr = atob(arr[1])
  let n = bstr.length
  const u8arr = new Uint8Array(n)
  while (n--) {
    u8arr[n] = bstr.charCodeAt(n)
  }
  return new File([u8arr], filename, { type: mime })
}

// 获取用户数据
const fetchUsers = async () => {
  loading.value = true
  
  try {
    const params = {
      ...searchForm,
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize
    }
    
    // 清理空值参数
    Object.keys(params).forEach(key => {
      if (params[key] === '' || params[key] === null || params[key] === undefined) {
        delete params[key]
      }
    })
    
    const response = await getUserList(params)
    
    if (response.data && response.data.data.records) {
      users.value = response.data.data.records
      pagination.total = response.data.data.total
    } else {
      users.value = []
      pagination.total = 0
    }
    
  } catch (error) {
    ElMessage.error('获取用户列表失败')
    users.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

// 生命周期
onMounted(() => {
  fetchUsers()
})
</script>

<style lang="scss" scoped>
.user-management {
  .search-section {
    .search-toolbar {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: nowrap;
      white-space: nowrap;
      overflow-x: auto;
      padding: 4px 0;

      // 控件默认宽度
      .w-220 { width: 220px; }
      .w-140 { width: 140px; }

      // 恢复组件高度（去掉 small 尺寸后使用默认）
      :deep(.el-input),
      :deep(.el-select) {
        --el-component-size: var(--el-component-size, 32px);
      }

      .toolbar-actions {
        margin-left: auto; // 靠右
        display: flex;
        gap: 8px;
        flex: none;
      }
    }
  }
  
  .user-info {
    display: flex;
    align-items: center;
    gap: 12px;
    
    .user-details {
      flex: 1;
      min-width: 0; // 防止文本溢出
      
      .username {
        font-weight: 500;
        color: var(--admin-text);
        margin-bottom: 2px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      
      .alias {
        font-size: 13px;
        color: var(--admin-text-light);
        margin-bottom: 2px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      
      .email {
        font-size: 12px;
        color: var(--admin-text-lighter);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }
  
  .user-stats {
    display: flex;
    flex-direction: column;
    gap: 4px;
    
    .stat-item {
      align-items: center;
      // font-size: 12px;
      
      .stat-label {
        color: var(--admin-text-lighter);
        font-weight: 400;
      }
      
      .stat-value {
        color: var(--admin-text);
        font-weight: 500;
        margin-left: 8px;
      }
    }
  }
  
  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: center;
  }
  
  .user-detail-content {
    .detail-section {
      margin-bottom: 24px;
      
      h3 {
        margin-bottom: 12px;
        font-size: 16px;
        font-weight: 600;
        color: var(--admin-text);
      }
    }
    
    .user-avatar-section {
      .avatar-display {
        display: flex;
        align-items: center;
        gap: 20px;
        padding: 20px;
        background: var(--el-fill-color-extra-light);
        border-radius: 8px;
        
        .user-basic-info {
          flex: 1;
          
          .user-display-name {
            margin: 0 0 8px 0;
            font-size: 24px;
            font-weight: 600;
            color: var(--admin-text);
          }
          
          .user-username {
            margin: 0 0 12px 0;
            font-size: 14px;
            color: var(--admin-text-lighter);
          }
          
          .user-status-tags {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
          }
        }
      }
    }
    
    .stat-item {
      text-align: center;
      padding: 16px;
      background: var(--admin-primary-lightest);
      border-radius: 6px;
      
      .stat-number {
        font-size: 24px;
        font-weight: 600;
        color: var(--admin-primary);
        margin-bottom: 4px;
      }
      
      .stat-label {
        font-size: 12px;
        color: var(--admin-text-lighter);
      }
    }
  }
}

// 响应式布局
@media (max-width: 1200px) {
  .user-management {
    .search-section {
      .search-row {
        .el-col {
          margin-bottom: 10px;
        }
        
        .action-buttons {
          justify-content: center;
          
          .el-button {
            min-width: 80px;
          }
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .user-management {
    .search-section {
      .search-row {
        // 小屏幕时搜索条件垂直排列，但仍然居中
        justify-content: center;
        
        .el-col {
          margin-bottom: 12px;
          display: flex;
          justify-content: center;
        }
        
        .action-buttons {
          flex-direction: column;
          align-items: center;
          
          .el-button {
            width: 200px;
            max-width: 100%;
          }
        }
      }
    }
    
    .admin-table {
      // 移动端隐藏一些不重要的列
      :deep(.el-table__column--hidden) {
        display: none;
      }
    }
  }
}

/* 修复表格悬浮闪烁问题 */
.user-management {
  .admin-table {
    // 禁用表格行的默认hover过渡效果，防止闪烁
    :deep(.el-table__row) {
      transition: none !important;
    }
    
    // 稳定表格行hover状态
    :deep(.el-table__row:hover) {
      background-color: var(--el-table-row-hover-bg-color) !important;
    }
    
    // 确保表格单元格内容不会触发重新布局
    :deep(.el-table__cell) {
      overflow: hidden;
      transition: none !important;
    }
    
    // 修复用户信息区域的hover状态
    .user-info {
      position: relative;
      z-index: 1;
    }
    
    // 确保头像不会引起布局变化
    .user-info .el-avatar {
      flex-shrink: 0;
    }
    
    // 禁用表格内所有元素的过渡动画
    :deep(*) {
      transition: none !important;
    }
    
    // 稳定表格体hover状态
    :deep(.el-table__body tr:hover) {
      background-color: var(--el-table-row-hover-bg-color) !important;
    }
  }
}

/* 头像上传样式 */
.avatar-upload-section {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.avatar-wrapper {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  border: 3px solid var(--el-color-primary-light-8);
  flex-shrink: 0;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.avatar-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.avatar-uploader {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
}

.upload-mask {
  height: 100%;
  color: #fff;
  text-align: center;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.upload-mask .el-icon {
  font-size: 20px;
  margin-bottom: 4px;
}

.avatar-upload-section :deep(.el-upload) {
  width: 100%;
  height: 100%;
  display: block;
}

.avatar-upload-section :deep(.el-upload-dragger) {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
}

.avatar-url-input {
  flex: 1;
}

.cropper-container {
  width: 100%;
  height: 400px;
}
</style>
