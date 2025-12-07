<template>
  <div class="permission-management">
    <div class="page-header">
      <h2 class="page-title">权限管理</h2>
    </div>
    
    <div class="admin-card">
      <div class="card-body">
        <!-- 操作栏 -->
        <div class="action-bar">
          <div class="action-buttons">
            <el-button type="primary" @click="createRole">
              <el-icon><Plus /></el-icon>
              添加角色
            </el-button>
            <el-button type="success" @click="createPermission">
              <el-icon><Key /></el-icon>
              添加权限
            </el-button>
          </div>
          <div class="search-box">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索角色或权限"
              clearable
              @keyup.enter="searchRoles"
              style="width: 250px"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="searchRoles" style="margin-left: 12px">搜索</el-button>
          </div>
        </div>
        
        <!-- 角色权限管理 -->
        <div class="role-permission-section">
          <h3>角色权限配置</h3>
          <div class="role-cards">
            <div v-for="role in roles" :key="role.id" class="role-card">
              <div class="role-header">
                <div class="role-info">
                  <h4>{{ role.name }}</h4>
                  <p>{{ role.description }}</p>
                </div>
                <div class="role-actions">
                  <el-button type="text" size="small" @click="editRole(role)">
                    编辑
                  </el-button>
                  <el-button type="text" size="small" @click="configurePermissions(role)">
                    配置权限
                  </el-button>
                  <el-button 
                    v-if="!role.isSystem"
                    type="text" 
                    size="small" 
                    @click="deleteRole(role)"
                  >
                    删除
                  </el-button>
                </div>
              </div>
              
              <div class="role-stats">
                <div class="stat-item">
                  <span class="stat-value">{{ role.userCount }}</span>
                  <span class="stat-label">用户数</span>
                </div>
                <div class="stat-item">
                  <span class="stat-value">{{ role.permissions.length }}</span>
                  <span class="stat-label">权限数</span>
                </div>
              </div>
              
              <div class="role-permissions">
                <el-tag
                  v-for="permission in role.permissions.slice(0, 6)"
                  :key="permission.id"
                  size="small"
                  type="info"
                  style="margin-right: 4px; margin-bottom: 4px;"
                >
                  {{ permission.name }}
                </el-tag>
                <el-tag v-if="role.permissions.length > 6" size="small" type="info">
                  +{{ role.permissions.length - 6 }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 权限树管理 -->
        <div class="permission-tree-section">
          <h3>权限树管理</h3>
          <div class="permission-tree-container">
            <el-tree
              ref="permissionTreeRef"
              :data="permissionTree"
              :props="treeProps"
              show-checkbox
              node-key="id"
              :expand-on-click-node="false"
              :check-on-click-node="true"
              class="permission-tree"
            >
              <template #default="{ node, data }">
                <span class="tree-node">
                  <el-icon v-if="data.icon" class="node-icon">
                    <component :is="data.icon" />
                  </el-icon>
                  <span class="node-label">{{ node.label }}</span>
                  <span v-if="data.code" class="node-code">{{ data.code }}</span>
                  <div class="node-actions">
                    <el-button type="text" size="small" @click="editPermission(data)">
                      编辑
                    </el-button>
                    <el-button 
                      v-if="!data.isSystem"
                      type="text" 
                      size="small" 
                      @click="deletePermission(data)"
                    >
                      删除
                    </el-button>
                  </div>
                </span>
              </template>
            </el-tree>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 创建/编辑角色对话框 -->
    <el-dialog
      v-model="roleDialog.visible"
      :title="roleDialog.mode === 'create' ? '添加角色' : '编辑角色'"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="roleFormRef"
        :model="roleDialog.form"
        :rules="roleFormRules"
        label-width="80px"
      >
        <el-form-item label="角色名称" prop="name">
          <el-input 
            v-model="roleDialog.form.name" 
            placeholder="请输入角色名称"
            maxlength="20"
          />
        </el-form-item>
        
        <el-form-item label="角色标识" prop="code">
          <el-input 
            v-model="roleDialog.form.code" 
            placeholder="请输入角色标识"
            maxlength="50"
          />
        </el-form-item>
        
        <el-form-item label="角色描述" prop="description">
          <el-input
            v-model="roleDialog.form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        
        <el-form-item label="排序" prop="sort">
          <el-input-number 
            v-model="roleDialog.form.sort" 
            :min="0" 
            :max="999"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="roleDialog.form.status">
            <el-radio label="active">启用</el-radio>
            <el-radio label="disabled">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="roleDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="saveRole">确定</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 权限配置对话框 -->
    <el-dialog
      v-model="permissionConfigDialog.visible"
      title="配置角色权限"
      width="600px"
      destroy-on-close
    >
      <div v-if="permissionConfigDialog.role" class="permission-config">
        <div class="config-header">
          <h4>{{ permissionConfigDialog.role.name }}</h4>
          <p>{{ permissionConfigDialog.role.description }}</p>
        </div>
        
        <el-divider />
        
        <div class="permission-selection">
          <el-tree
            ref="configTreeRef"
            :data="permissionTree"
            :props="treeProps"
            show-checkbox
            node-key="id"
            :default-checked-keys="selectedPermissionIds"
            :check-strictly="false"
            class="config-tree"
          >
            <template #default="{ node, data }">
              <span class="config-tree-node">
                <el-icon v-if="data.icon" class="node-icon">
                  <component :is="data.icon" />
                </el-icon>
                <span class="node-label">{{ node.label }}</span>
                <span v-if="data.code" class="node-code">{{ data.code }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="permissionConfigDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="savePermissionConfig">保存配置</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 创建/编辑权限对话框 -->
    <el-dialog
      v-model="permissionDialog.visible"
      :title="permissionDialog.mode === 'create' ? '添加权限' : '编辑权限'"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="permissionFormRef"
        :model="permissionDialog.form"
        :rules="permissionFormRules"
        label-width="80px"
      >
        <el-form-item label="父级权限" prop="parentId">
          <el-tree-select
            v-model="permissionDialog.form.parentId"
            :data="permissionTree"
            :props="treeSelectProps"
            placeholder="请选择父级权限"
            style="width: 100%"
            clearable
          />
        </el-form-item>
        
        <el-form-item label="权限名称" prop="name">
          <el-input 
            v-model="permissionDialog.form.name" 
            placeholder="请输入权限名称"
            maxlength="50"
          />
        </el-form-item>
        
        <el-form-item label="权限标识" prop="code">
          <el-input 
            v-model="permissionDialog.form.code" 
            placeholder="请输入权限标识"
            maxlength="100"
          />
        </el-form-item>
        
        <el-form-item label="权限类型" prop="type">
          <el-select v-model="permissionDialog.form.type" placeholder="请选择权限类型" style="width: 100%">
            <el-option label="菜单" value="menu" />
            <el-option label="按钮" value="button" />
            <el-option label="接口" value="api" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="图标" prop="icon">
          <el-input 
            v-model="permissionDialog.form.icon" 
            placeholder="请输入图标名称"
            maxlength="50"
          />
        </el-form-item>
        
        <el-form-item label="排序" prop="sort">
          <el-input-number 
            v-model="permissionDialog.form.sort" 
            :min="0" 
            :max="999"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="permissionDialog.form.status">
            <el-radio label="active">启用</el-radio>
            <el-radio label="disabled">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="permissionDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="savePermission">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Plus,
  Key,
  Search,
  User,
  Setting,
  Document,
  ChatDotRound,
  Cpu,
  DataAnalysis,
  Message
} from '@element-plus/icons-vue'

// 响应式数据
const searchKeyword = ref('')
const permissionTreeRef = ref()
const configTreeRef = ref()
const roleFormRef = ref()
const permissionFormRef = ref()

const roles = ref([])
const permissionTree = ref([])
const selectedPermissionIds = ref([])

const treeProps = {
  children: 'children',
  label: 'name'
}

const treeSelectProps = {
  children: 'children',
  label: 'name',
  value: 'id'
}

const roleDialog = reactive({
  visible: false,
  mode: 'create',
  form: {
    name: '',
    code: '',
    description: '',
    sort: 0,
    status: 'active'
  }
})

const permissionConfigDialog = reactive({
  visible: false,
  role: null
})

const permissionDialog = reactive({
  visible: false,
  mode: 'create',
  form: {
    parentId: null,
    name: '',
    code: '',
    type: 'menu',
    icon: '',
    sort: 0,
    status: 'active'
  }
})

// 表单验证规则
const roleFormRules = {
  name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { min: 2, max: 20, message: '角色名称长度为2-20字符', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入角色标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '角色标识只能包含字母、数字和下划线，且以字母开头', trigger: 'blur' }
  ]
}

const permissionFormRules = {
  name: [
    { required: true, message: '请输入权限名称', trigger: 'blur' },
    { min: 2, max: 50, message: '权限名称长度为2-50字符', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入权限标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_:]*$/, message: '权限标识只能包含字母、数字、下划线和冒号，且以字母开头', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择权限类型', trigger: 'change' }
  ]
}

// 方法
const fetchRoles = async () => {
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 300))
    
    const mockRoles = [
      {
        id: 1,
        name: '超级管理员',
        code: 'super_admin',
        description: '拥有系统所有权限的超级管理员',
        userCount: 2,
        permissions: [
          { id: 1, name: '用户管理' },
          { id: 2, name: '内容管理' },
          { id: 3, name: '植物管理' },
          { id: 4, name: 'AI管理' },
          { id: 5, name: '系统设置' },
          { id: 6, name: '数据分析' },
          { id: 7, name: '消息管理' }
        ],
        isSystem: true,
        sort: 1,
        status: 'active'
      },
      {
        id: 2,
        name: '管理员',
        code: 'admin',
        description: '拥有大部分管理权限的管理员',
        userCount: 5,
        permissions: [
          { id: 1, name: '用户管理' },
          { id: 2, name: '内容管理' },
          { id: 3, name: '植物管理' },
          { id: 6, name: '数据分析' }
        ],
        isSystem: false,
        sort: 2,
        status: 'active'
      },
      {
        id: 3,
        name: '编辑员',
        code: 'editor',
        description: '负责内容编辑和审核的编辑员',
        userCount: 8,
        permissions: [
          { id: 2, name: '内容管理' },
          { id: 3, name: '植物管理' }
        ],
        isSystem: false,
        sort: 3,
        status: 'active'
      },
      {
        id: 4,
        name: '审核员',
        code: 'moderator',
        description: '负责内容审核和用户管理的审核员',
        userCount: 12,
        permissions: [
          { id: 1, name: '用户管理' },
          { id: 7, name: '消息管理' }
        ],
        isSystem: false,
        sort: 4,
        status: 'active'
      }
    ]
    
    // 应用搜索过滤
    if (searchKeyword.value) {
      roles.value = mockRoles.filter(role => 
        role.name.includes(searchKeyword.value) ||
        role.code.includes(searchKeyword.value) ||
        role.description.includes(searchKeyword.value)
      )
    } else {
      roles.value = mockRoles
    }
    
  } catch (error) {
    ElMessage.error('获取角色列表失败')
  }
}

const fetchPermissionTree = async () => {
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 300))
    
    const mockPermissionTree = [
      {
        id: 1,
        name: '用户管理',
        code: 'user:management',
        type: 'menu',
        icon: 'User',
        sort: 1,
        status: 'active',
        isSystem: true,
        children: [
          {
            id: 11,
            name: '用户列表',
            code: 'user:list',
            type: 'menu',
            sort: 1,
            status: 'active',
            children: [
              { id: 111, name: '查看用户', code: 'user:view', type: 'button', sort: 1, status: 'active' },
              { id: 112, name: '编辑用户', code: 'user:edit', type: 'button', sort: 2, status: 'active' },
              { id: 113, name: '删除用户', code: 'user:delete', type: 'button', sort: 3, status: 'active' }
            ]
          },
          {
            id: 12,
            name: '用户统计',
            code: 'user:stats',
            type: 'menu',
            sort: 2,
            status: 'active',
            children: [
              { id: 121, name: '查看统计', code: 'user:stats:view', type: 'button', sort: 1, status: 'active' }
            ]
          }
        ]
      },
      {
        id: 2,
        name: '内容管理',
        code: 'content:management',
        type: 'menu',
        icon: 'Document',
        sort: 2,
        status: 'active',
        isSystem: true,
        children: [
          {
            id: 21,
            name: '文章管理',
            code: 'content:post',
            type: 'menu',
            sort: 1,
            status: 'active',
            children: [
              { id: 211, name: '查看帖子', code: 'content:post:view', type: 'button', sort: 1, status: 'active' },
              { id: 212, name: '编辑帖子', code: 'content:post:edit', type: 'button', sort: 2, status: 'active' },
              { id: 213, name: '删除帖子', code: 'content:post:delete', type: 'button', sort: 3, status: 'active' }
            ]
          },
          {
            id: 22,
            name: '评论管理',
            code: 'content:comment',
            type: 'menu',
            sort: 2,
            status: 'active',
            children: [
              { id: 221, name: '查看评论', code: 'content:comment:view', type: 'button', sort: 1, status: 'active' },
              { id: 222, name: '删除评论', code: 'content:comment:delete', type: 'button', sort: 2, status: 'active' }
            ]
          }
        ]
      },
      {
        id: 3,
        name: '植物管理',
        code: 'plant:management',
        type: 'menu',
        icon: 'Plant',
        sort: 3,
        status: 'active',
        isSystem: true,
        children: [
          {
            id: 31,
            name: '植物百科',
            code: 'plant:encyclopedia',
            type: 'menu',
            sort: 1,
            status: 'active',
            children: [
              { id: 311, name: '查看植物', code: 'plant:view', type: 'button', sort: 1, status: 'active' },
              { id: 312, name: '添加植物', code: 'plant:add', type: 'button', sort: 2, status: 'active' },
              { id: 313, name: '编辑植物', code: 'plant:edit', type: 'button', sort: 3, status: 'active' }
            ]
          }
        ]
      },
      {
        id: 4,
        name: 'AI管理',
        code: 'ai:management',
        type: 'menu',
        icon: 'Cpu',
        sort: 4,
        status: 'active',
        isSystem: true,
        children: [
          {
            id: 41,
            name: 'AI对话',
            code: 'ai:chat',
            type: 'menu',
            sort: 1,
            status: 'active',
            children: [
              { id: 411, name: '查看对话', code: 'ai:chat:view', type: 'button', sort: 1, status: 'active' }
            ]
          }
        ]
      },
      {
        id: 5,
        name: '系统设置',
        code: 'system:settings',
        type: 'menu',
        icon: 'Setting',
        sort: 5,
        status: 'active',
        isSystem: true,
        children: [
          {
            id: 51,
            name: '系统配置',
            code: 'system:config',
            type: 'menu',
            sort: 1,
            status: 'active',
            children: [
              { id: 511, name: '查看配置', code: 'system:config:view', type: 'button', sort: 1, status: 'active' },
              { id: 512, name: '修改配置', code: 'system:config:edit', type: 'button', sort: 2, status: 'active' }
            ]
          }
        ]
      },
      {
        id: 6,
        name: '数据分析',
        code: 'data:analysis',
        type: 'menu',
        icon: 'DataAnalysis',
        sort: 6,
        status: 'active',
        isSystem: true,
        children: [
          {
            id: 61,
            name: '用户分析',
            code: 'data:user',
            type: 'menu',
            sort: 1,
            status: 'active',
            children: [
              { id: 611, name: '查看分析', code: 'data:user:view', type: 'button', sort: 1, status: 'active' }
            ]
          }
        ]
      },
      {
        id: 7,
        name: '消息管理',
        code: 'message:management',
        type: 'menu',
        icon: 'Message',
        sort: 7,
        status: 'active',
        isSystem: true,
        children: [
          {
            id: 71,
            name: '系统消息',
            code: 'message:system',
            type: 'menu',
            sort: 1,
            status: 'active',
            children: [
              { id: 711, name: '发送消息', code: 'message:system:send', type: 'button', sort: 1, status: 'active' }
            ]
          }
        ]
      }
    ]
    
    permissionTree.value = mockPermissionTree
    
  } catch (error) {
    ElMessage.error('获取权限树失败')
  }
}

const searchRoles = () => {
  fetchRoles()
}

const createRole = () => {
  roleDialog.mode = 'create'
  roleDialog.form = {
    name: '',
    code: '',
    description: '',
    sort: 0,
    status: 'active'
  }
  roleDialog.visible = true
}

const editRole = (role) => {
  roleDialog.mode = 'edit'
  roleDialog.form = {
    id: role.id,
    name: role.name,
    code: role.code,
    description: role.description,
    sort: role.sort,
    status: role.status
  }
  roleDialog.visible = true
}

const configurePermissions = (role) => {
  permissionConfigDialog.role = role
  selectedPermissionIds.value = role.permissions.map(p => p.id)
  permissionConfigDialog.visible = true
}

const createPermission = () => {
  permissionDialog.mode = 'create'
  permissionDialog.form = {
    parentId: null,
    name: '',
    code: '',
    type: 'menu',
    icon: '',
    sort: 0,
    status: 'active'
  }
  permissionDialog.visible = true
}

const editPermission = (permission) => {
  permissionDialog.mode = 'edit'
  permissionDialog.form = {
    id: permission.id,
    parentId: permission.parentId || null,
    name: permission.name,
    code: permission.code,
    type: permission.type,
    icon: permission.icon || '',
    sort: permission.sort,
    status: permission.status
  }
  permissionDialog.visible = true
}

const saveRole = async () => {
  if (!roleFormRef.value) return
  
  try {
    await roleFormRef.value.validate()
    
    ElMessage.success('角色保存成功')
    roleDialog.visible = false
    fetchRoles()
    
  } catch {
    // 验证失败
  }
}

const savePermissionConfig = () => {
  if (!configTreeRef.value) return
  
  const checkedKeys = configTreeRef.value.getCheckedKeys()
  ElMessage.success('权限配置保存成功')
  permissionConfigDialog.visible = false
  fetchRoles()
}

const savePermission = async () => {
  if (!permissionFormRef.value) return
  
  try {
    await permissionFormRef.value.validate()
    
    ElMessage.success('权限保存成功')
    permissionDialog.visible = false
    fetchPermissionTree()
    
  } catch {
    // 验证失败
  }
}

const deleteRole = async (role) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色 "${role.name}" 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    ElMessage.success('角色删除成功')
    fetchRoles()
    
  } catch {
    // 用户取消操作
  }
}

const deletePermission = async (permission) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除权限 "${permission.name}" 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    ElMessage.success('权限删除成功')
    fetchPermissionTree()
    
  } catch {
    // 用户取消操作
  }
}

// 生命周期
onMounted(() => {
  fetchRoles()
  fetchPermissionTree()
})
</script>

<style lang="scss" scoped>
.permission-management {
  .action-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    
    .action-buttons {
      display: flex;
      gap: 12px;
    }
    
    .search-box {
      display: flex;
      align-items: center;
    }
  }
  
  .role-permission-section {
    margin-bottom: 32px;
    
    h3 {
      margin-bottom: 16px;
      color: #303133;
      font-size: 18px;
      font-weight: 600;
    }
    
    .role-cards {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
      gap: 20px;
      
      .role-card {
        background: white;
        border: 1px solid #e4e7ed;
        border-radius: 8px;
        padding: 20px;
        transition: all 0.3s ease;
        
        &:hover {
          border-color: #409eff;
          box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
        }
        
        .role-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          margin-bottom: 16px;
          
          .role-info {
            h4 {
              margin: 0 0 8px 0;
              color: #303133;
              font-size: 16px;
              font-weight: 600;
            }
            
            p {
              margin: 0;
              color: #606266;
              font-size: 14px;
              line-height: 1.4;
            }
          }
          
          .role-actions {
            display: flex;
            flex-direction: column;
            gap: 4px;
          }
        }
        
        .role-stats {
          display: flex;
          gap: 20px;
          margin-bottom: 16px;
          
          .stat-item {
            text-align: center;
            
            .stat-value {
              display: block;
              font-size: 20px;
              font-weight: 600;
              color: #409eff;
              margin-bottom: 4px;
            }
            
            .stat-label {
              font-size: 12px;
              color: #909399;
            }
          }
        }
        
        .role-permissions {
          line-height: 1.6;
        }
      }
    }
  }
  
  .permission-tree-section {
    h3 {
      margin-bottom: 16px;
      color: #303133;
      font-size: 18px;
      font-weight: 600;
    }
    
    .permission-tree-container {
      background: white;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      padding: 20px;
      
      .permission-tree {
        .tree-node {
          display: flex;
          align-items: center;
          gap: 8px;
          flex: 1;
          
          .node-icon {
            color: #409eff;
          }
          
          .node-label {
            font-weight: 500;
            color: #303133;
          }
          
          .node-code {
            font-size: 12px;
            color: #909399;
            background: #f0f0f0;
            padding: 2px 6px;
            border-radius: 4px;
          }
          
          .node-actions {
            display: flex;
            gap: 8px;
            margin-left: auto;
          }
        }
      }
    }
  }
  
  .permission-config {
    .config-header {
      text-align: center;
      
      h4 {
        margin: 0 0 8px 0;
        color: #303133;
        font-size: 18px;
        font-weight: 600;
      }
      
      p {
        margin: 0;
        color: #606266;
        font-size: 14px;
      }
    }
    
    .permission-selection {
      max-height: 400px;
      overflow-y: auto;
      
      .config-tree {
        .config-tree-node {
          display: flex;
          align-items: center;
          gap: 8px;
          
          .node-icon {
            color: #409eff;
          }
          
          .node-label {
            font-weight: 500;
            color: #303133;
          }
          
          .node-code {
            font-size: 12px;
            color: #909399;
            background: #f0f0f0;
            padding: 2px 6px;
            border-radius: 4px;
          }
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .permission-management {
    .action-bar {
      flex-direction: column;
      gap: 16px;
      align-items: stretch;
      
      .search-box {
        justify-content: center;
      }
    }
    
    .role-cards {
      grid-template-columns: 1fr;
    }
    
    .role-card {
      .role-header {
        flex-direction: column;
        gap: 12px;
        
        .role-actions {
          flex-direction: row;
          justify-content: center;
        }
      }
    }
  }
}
</style>
