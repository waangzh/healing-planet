<template>
  <div class="system-settings">
    <div class="page-title">系统设置</div>
    
    <el-tabs v-model="activeTab" type="card">
      <!-- 基础设置 -->
      <el-tab-pane label="基础设置" name="basic">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">网站基本信息</h3>
          </div>
          <div class="card-body">
            <el-form
              ref="basicFormRef"
              :model="basicForm"
              :rules="basicRules"
              label-width="120px"
              class="admin-form"
            >
              <el-form-item label="网站名称" prop="siteName">
                <el-input v-model="basicForm.siteName" />
              </el-form-item>
              <el-form-item label="网站标语" prop="siteSlogan">
                <el-input v-model="basicForm.siteSlogan" />
              </el-form-item>
              <el-form-item label="网站描述" prop="siteDescription">
                <el-input
                  v-model="basicForm.siteDescription"
                  type="textarea"
                  :rows="3"
                />
              </el-form-item>
              <el-form-item label="网站关键词" prop="siteKeywords">
                <el-input v-model="basicForm.siteKeywords" placeholder="用逗号分隔" />
              </el-form-item>
              <el-form-item label="网站Logo" prop="siteLogo">
                <el-input v-model="basicForm.siteLogo" placeholder="Logo图片URL" />
              </el-form-item>
              <el-form-item label="网站Favicon" prop="siteFavicon">
                <el-input v-model="basicForm.siteFavicon" placeholder="Favicon图片URL" />
              </el-form-item>
              <el-form-item label="备案号" prop="icp">
                <el-input v-model="basicForm.icp" />
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" @click="saveBasicSettings">保存设置</el-button>
              </div>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 轮播图管理 -->
      <el-tab-pane label="轮播图管理" name="carousel">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">首页轮播图</h3>
            <el-button type="primary" @click="showCarouselDialog">添加轮播图</el-button>
          </div>
          <div class="card-body">
            <el-table :data="carouselList" style="width: 100%">
              <el-table-column label="图片" width="150">
                <template #default="{ row }">
                  <el-image
                    :src="row.image"
                    fit="cover"
                    style="width: 120px; height: 60px; border-radius: 4px;"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="title" label="标题" />
              <el-table-column prop="subtitle" label="副标题" />
              <el-table-column prop="link" label="链接" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'">
                    {{ row.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sort" label="排序" width="80" />
              <el-table-column label="操作" width="200">
                <template #default="{ row, $index }">
                  <el-button size="small" @click="editCarousel(row, $index)">编辑</el-button>
                  <el-button
                    size="small"
                    :type="row.enabled ? 'warning' : 'success'"
                    @click="toggleCarousel(row)"
                  >
                    {{ row.enabled ? '禁用' : '启用' }}
                  </el-button>
                  <el-button size="small" type="danger" @click="deleteCarousel($index)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 内容审核 -->
      <el-tab-pane label="内容审核" name="moderation">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">审核规则设置</h3>
          </div>
          <div class="card-body">
            <el-form
              ref="moderationFormRef"
              :model="moderationForm"
              label-width="150px"
              class="admin-form"
            >
              <el-form-item label="自动审核">
                <el-switch v-model="moderationForm.autoModeration" />
                <span class="form-hint">开启后，系统将自动审核符合条件的内容</span>
              </el-form-item>
              <el-form-item label="敏感词过滤">
                <el-switch v-model="moderationForm.sensitiveWordFilter" />
              </el-form-item>
              <el-form-item label="图片审核">
                <el-switch v-model="moderationForm.imageModeration" />
              </el-form-item>
              <el-form-item label="新用户审核">
                <el-switch v-model="moderationForm.newUserModeration" />
                <span class="form-hint">新注册用户的内容需要人工审核</span>
              </el-form-item>
              <el-form-item label="审核关键词">
                <el-input
                  v-model="moderationForm.keywords"
                  type="textarea"
                  :rows="5"
                  placeholder="每行一个关键词"
                />
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" @click="saveModerationSettings">保存设置</el-button>
              </div>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 邮件设置 -->
      <el-tab-pane label="邮件设置" name="email">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">SMTP邮件配置</h3>
          </div>
          <div class="card-body">
            <el-form
              ref="emailFormRef"
              :model="emailForm"
              :rules="emailRules"
              label-width="120px"
              class="admin-form"
            >
              <el-form-item label="SMTP服务器" prop="smtpHost">
                <el-input v-model="emailForm.smtpHost" />
              </el-form-item>
              <el-form-item label="SMTP端口" prop="smtpPort">
                <el-input-number v-model="emailForm.smtpPort" :min="1" :max="65535" />
              </el-form-item>
              <el-form-item label="加密方式" prop="encryption">
                <el-select v-model="emailForm.encryption" style="width: 100%">
                  <el-option label="无加密" value="none" />
                  <el-option label="SSL" value="ssl" />
                  <el-option label="TLS" value="tls" />
                </el-select>
              </el-form-item>
              <el-form-item label="用户名" prop="username">
                <el-input v-model="emailForm.username" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="emailForm.password" type="password" show-password />
              </el-form-item>
              <el-form-item label="发件人邮箱" prop="fromEmail">
                <el-input v-model="emailForm.fromEmail" />
              </el-form-item>
              <el-form-item label="发件人名称" prop="fromName">
                <el-input v-model="emailForm.fromName" />
              </el-form-item>
              <div class="form-actions">
                <el-button @click="testEmailSettings">测试连接</el-button>
                <el-button type="primary" @click="saveEmailSettings">保存设置</el-button>
              </div>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 安全设置 -->
      <el-tab-pane label="安全设置" name="security">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">安全配置</h3>
          </div>
          <div class="card-body">
            <el-form
              ref="securityFormRef"
              :model="securityForm"
              label-width="150px"
              class="admin-form"
            >
              <el-form-item label="登录验证码">
                <el-switch v-model="securityForm.loginCaptcha" />
              </el-form-item>
              <el-form-item label="注册验证码">
                <el-switch v-model="securityForm.registerCaptcha" />
              </el-form-item>
              <el-form-item label="登录失败限制">
                <el-switch v-model="securityForm.loginAttemptLimit" />
                <span class="form-hint">连续登录失败后临时锁定账户</span>
              </el-form-item>
              <el-form-item label="最大失败次数">
                <el-input-number
                  v-model="securityForm.maxLoginAttempts"
                  :min="3"
                  :max="10"
                  :disabled="!securityForm.loginAttemptLimit"
                />
              </el-form-item>
              <el-form-item label="锁定时间(分钟)">
                <el-input-number
                  v-model="securityForm.lockoutDuration"
                  :min="5"
                  :max="1440"
                  :disabled="!securityForm.loginAttemptLimit"
                />
              </el-form-item>
              <el-form-item label="Session超时(分钟)">
                <el-input-number v-model="securityForm.sessionTimeout" :min="30" :max="1440" />
              </el-form-item>
              <el-form-item label="强制HTTPS">
                <el-switch v-model="securityForm.forceHttps" />
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" @click="saveSecuritySettings">保存设置</el-button>
              </div>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
    
    <!-- 轮播图编辑对话框 -->
    <el-dialog
      v-model="carouselDialog"
      :title="isEditCarousel ? '编辑轮播图' : '添加轮播图'"
      width="600px"
    >
      <el-form
        ref="carouselFormRef"
        :model="carouselForm"
        :rules="carouselRules"
        label-width="80px"
      >
        <el-form-item label="标题" prop="title">
          <el-input v-model="carouselForm.title" />
        </el-form-item>
        <el-form-item label="副标题" prop="subtitle">
          <el-input v-model="carouselForm.subtitle" />
        </el-form-item>
        <el-form-item label="图片URL" prop="image">
          <el-input v-model="carouselForm.image" />
        </el-form-item>
        <el-form-item label="链接" prop="link">
          <el-input v-model="carouselForm.link" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="carouselForm.sort" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="carouselForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="carouselDialog = false">取消</el-button>
          <el-button type="primary" @click="saveCarousel">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

// 响应式数据
const activeTab = ref('basic')
const carouselDialog = ref(false)
const isEditCarousel = ref(false)
const editCarouselIndex = ref(-1)

// 表单引用
const basicFormRef = ref()
const moderationFormRef = ref()
const emailFormRef = ref()
const securityFormRef = ref()
const carouselFormRef = ref()

// 基础设置表单
const basicForm = reactive({
  siteName: '绿植社区',
  siteSlogan: '绿色生活，从植物开始',
  siteDescription: '专业的植物养护知识分享社区',
  siteKeywords: '植物,养护,绿植,园艺,多肉',
  // siteLogo: '/logo.png',
  siteFavicon: '/favicon.ico',
  icp: '京ICP备XXXXXXXX号'
})

const basicRules = {
  siteName: [
    { required: true, message: '请输入网站名称', trigger: 'blur' }
  ]
}

// 内容审核表单
const moderationForm = reactive({
  autoModeration: true,
  sensitiveWordFilter: true,
  imageModeration: true,
  newUserModeration: false,
  keywords: '违规内容\n敏感词汇\n广告信息'
})

// 邮件设置表单
const emailForm = reactive({
  smtpHost: 'smtp.qq.com',
  smtpPort: 587,
  encryption: 'tls',
  username: '',
  password: '',
  fromEmail: '',
  fromName: '绿植社区'
})

const emailRules = {
  smtpHost: [
    { required: true, message: '请输入SMTP服务器', trigger: 'blur' }
  ],
  smtpPort: [
    { required: true, message: '请输入SMTP端口', trigger: 'blur' }
  ]
}

// 安全设置表单
const securityForm = reactive({
  loginCaptcha: true,
  registerCaptcha: true,
  loginAttemptLimit: true,
  maxLoginAttempts: 5,
  lockoutDuration: 15,
  sessionTimeout: 120,
  forceHttps: false
})

// 轮播图数据
const carouselList = ref([])

// 轮播图表单
const carouselForm = reactive({
  title: '',
  subtitle: '',
  image: '',
  link: '',
  sort: 1,
  enabled: true
})

const carouselRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' }
  ],
  image: [
    { required: true, message: '请输入图片URL', trigger: 'blur' }
  ]
}

// 方法
const saveBasicSettings = async () => {
  if (!basicFormRef.value) return
  
  await basicFormRef.value.validate((valid) => {
    if (valid) {
      // 模拟保存
      ElMessage.success('基础设置保存成功')
    }
  })
}

const saveModerationSettings = () => {
  // 模拟保存
  ElMessage.success('审核设置保存成功')
}

const saveEmailSettings = async () => {
  if (!emailFormRef.value) return
  
  await emailFormRef.value.validate((valid) => {
    if (valid) {
      // 模拟保存
      ElMessage.success('邮件设置保存成功')
    }
  })
}

const testEmailSettings = () => {
  // 模拟测试邮件连接
  ElMessage.success('邮件连接测试成功')
}

const saveSecuritySettings = () => {
  // 模拟保存
  ElMessage.success('安全设置保存成功')
}

const showCarouselDialog = () => {
  isEditCarousel.value = false
  resetCarouselForm()
  carouselDialog.value = true
}

const editCarousel = (item, index) => {
  isEditCarousel.value = true
  editCarouselIndex.value = index
  Object.assign(carouselForm, item)
  carouselDialog.value = true
}

const saveCarousel = async () => {
  if (!carouselFormRef.value) return
  
  await carouselFormRef.value.validate((valid) => {
    if (valid) {
      if (isEditCarousel.value) {
        // 编辑
        Object.assign(carouselList.value[editCarouselIndex.value], carouselForm)
        ElMessage.success('轮播图更新成功')
      } else {
        // 添加
        carouselList.value.push({ ...carouselForm })
        ElMessage.success('轮播图添加成功')
      }
      carouselDialog.value = false
    }
  })
}

const toggleCarousel = (item) => {
  item.enabled = !item.enabled
  ElMessage.success(`轮播图${item.enabled ? '启用' : '禁用'}成功`)
}

const deleteCarousel = (index) => {
  carouselList.value.splice(index, 1)
  ElMessage.success('轮播图删除成功')
}

const resetCarouselForm = () => {
  Object.assign(carouselForm, {
    title: '',
    subtitle: '',
    image: '',
    link: '',
    sort: 1,
    enabled: true
  })
  
  if (carouselFormRef.value) {
    carouselFormRef.value.clearValidate()
  }
}

// 初始化轮播图数据
const initCarouselData = () => {
  carouselList.value = [
    {
      title: '绿植养护指南',
      subtitle: '让你的植物茁壮成长',
      image: 'https://picsum.photos/800/400?random=1',
      link: '/guide',
      sort: 1,
      enabled: true
    },
    {
      title: '植物识别功能',
      subtitle: 'AI智能识别植物种类',
      image: 'https://picsum.photos/800/400?random=2',
      link: '/identify',
      sort: 2,
      enabled: true
    }
  ]
}

// 生命周期
onMounted(() => {
  initCarouselData()
})
</script>

<style lang="scss" scoped>
.system-settings {
  .form-hint {
    font-size: 12px;
    color: #909399;
    margin-left: 8px;
  }
  
  .dialog-footer {
    text-align: right;
  }
}
</style>
