<template>
  <div class="admin-login-wrapper">
    <div class="login-card">
      <div class="logo-title">
        <i class="fas fa-shield-alt brand-icon"></i>
        <h2>植愈星球管理后台登录</h2>
        <p class="subtitle">欢迎使用绿植社区管理系统</p>
      </div>

      <el-form ref="formRef" :model="loginForm" :rules="rules" class="login-form" @submit.prevent>
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" :prefix-icon="User" placeholder="用户名 : admin" @keyup.enter="handlePreCheck" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" :prefix-icon="Lock" :type="showPwd ? 'text' : 'password'" placeholder="密码 : 123456" @keyup.enter="handlePreCheck">
            <template #suffix>
              <el-icon class="pwd-toggle" @click="showPwd = !showPwd">
                <component :is="showPwd ? View : Hide" />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <div class="form-options">
          <label class="remember">
            <input type="checkbox" v-model="loginForm.remember" />
            <span>记住我</span>
          </label>
        </div>
        <el-button type="primary" class="login-btn" :loading="loading" @click="handlePreCheck">{{ loading ? '验证中...' : '登录' }}</el-button>
        <div class="demo-tip">演示: admin / 123456</div>
      </el-form>
    </div>

    <el-dialog title="请完成滑块验证" v-model="showVerify" width="360px" :close-on-click-modal="false" @closed="onVerifyDialogClosed" append-to-body>
      <SliderVerify ref="sliderRef" :canvas-width="320" :canvas-height="155" :accuracy="5" slider-hint="向右滑动完成验证" @success="onVerifySuccess" @fail="onVerifyFail" @again="onVerifyAgain" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, View, Hide } from '@element-plus/icons-vue'
import SliderVerify from '@/components/login/sliderVerify.vue'
import { useAdminStore } from '@/stores/admin'

const router = useRouter()
const adminStore = useAdminStore()

const formRef = ref(null)
const sliderRef = ref(null)
const showVerify = ref(false)
const showPwd = ref(false)
const loading = ref(false)

const loginForm = ref({
  username: 'admin',
  password: '123456',
  remember: false,
  nonceStr: '',
  value: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度 2-20', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 30, message: '长度 6-30', trigger: 'blur' }
  ]
}

const handlePreCheck = async () => {
  try {
    await formRef.value.validate()
    showVerify.value = true
  } catch (_) {}
}

const onVerifySuccess = async (data) => {
  loginForm.value.nonceStr = data.nonceStr
  loginForm.value.value = data.value
  await performLogin()
}
const onVerifyFail = () => ElMessage.error('验证失败,请重试')
const onVerifyAgain = () => ElMessage.warning('滑动异常,请再试一次')
const onVerifyDialogClosed = () => {
  loginForm.value.nonceStr = ''
  loginForm.value.value = ''
  sliderRef.value?.refresh?.()
}

const performLogin = async () => {
  loading.value = true
  try {
  const { username, password, nonceStr, value } = loginForm.value
  const res = await adminStore.login({ username, password, nonceStr, value })
    if (res.success) {
      ElMessage.success('登录成功')
      showVerify.value = false
      router.push('/dashboard')
    } else {
      ElMessage.error(res.message || '登录失败')
      sliderRef.value?.verifyFailEvent?.()
    }
  } catch (e) {
    ElMessage.error('请求出错')
    sliderRef.value?.verifyFailEvent?.()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.admin-login-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg,var(--el-color-primary) 0%, #2d8cf0 60%, #1b6dd8 100%);
  padding: 30px 15px;
  box-sizing: border-box;
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--surface, #fff);
  border-radius: 14px;
  padding: 40px 36px 46px;
  box-shadow: 0 10px 30px -5px rgba(0,0,0,.15),0 4px 10px rgba(0,0,0,.08);
  position: relative;
  overflow: hidden;
}
.login-card:before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 85% 15%, rgba(255,255,255,.35), transparent 60%);
  pointer-events: none;
}
.logo-title { text-align: center; margin-bottom: 28px; }
.brand-icon { font-size: 42px; color: var(--el-color-primary); margin-bottom: 8px; }
.logo-title h2 { margin: 6px 0 4px; font-size: 24px; font-weight: 600; letter-spacing: 1px; color: var(--el-text-color-primary); }
.subtitle { margin: 0; font-size: 14px; color: var(--el-text-color-secondary); }

.login-form { display: flex; flex-direction: column; gap: 18px; }
.pwd-toggle { cursor: pointer; }
.form-options { display: flex; justify-content: space-between; align-items: center; font-size: 13px; margin-top: -6px; }
.remember { display: flex; align-items: center; gap: 6px; cursor: pointer; color: var(--el-text-color-secondary); }
.remember input { width: 14px; height: 14px; }

.login-btn { width: 100%; height: 44px; font-size: 15px; font-weight: 600; letter-spacing: 1px; }
.demo-tip { margin-top: -6px; font-size: 12px; text-align: center; color: var(--el-text-color-placeholder); }

/* 适配深色 */
@media (prefers-color-scheme: dark) {
  .login-card { background: #1e1f24; }
  .logo-title h2 { color: #fff; }
  .subtitle { color: rgba(255,255,255,.55); }
  .demo-tip { color: rgba(255,255,255,.35); }
}

/* 输入框样式微调 */
:deep(.el-input__wrapper) { padding: 4px 14px; }
:deep(.el-input__inner) { font-size: 14px; }

/* 滑块弹窗微调 */
:deep(.el-dialog__header) { padding: 12px 16px; }
:deep(.el-dialog__body) { padding: 12px 16px 20px; }

@media (max-width: 480px) {
  .login-card { padding: 32px 26px 40px; }
  .logo-title h2 { font-size: 22px; }
}
</style>
