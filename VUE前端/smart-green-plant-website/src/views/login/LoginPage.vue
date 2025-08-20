<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore, useDeviceStore, useDeviceStatusStore, useMessageStore, useHistoryStore, useAiMessageStore } from '@/stores'
import { userLoginService, userRegisterService } from '@/api/user'
import { User, Lock, View, Hide } from '@element-plus/icons-vue'
import bgImage from '@/assets/背景图片/login_bg.jpg'
import flowerImage from '@/assets/花瓣 (1).png'
import SliderVerify from '@/components/login/sliderVerify.vue'

// 响应式数据
const showPassword = ref(false)
const isRegister = ref(false) // 控制显示登录/注册表单
const loading = ref(false)
const form = ref()
const sliderVerifyRef = ref(null)

// 滑动验证控制
const isShowSliderVerify = ref(false)

const formModel = ref({
  username: 'root',
  password: '123456',
  email: '', // 添加邮箱字段
  nonceStr: '', // 滑动验证相关字段
  value: ''
})

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 10, message: '用户名必须是3-10位的字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^\S{5,15}$/,
      message: '密码必须是5-15位的非空字符',
      trigger: 'blur',
    },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { 
      type: 'email', 
      message: '请输入正确的邮箱格式', 
      trigger: 'blur' 
    }
  ]
}

// 获取需要的组件实例
const router = useRouter()
const userStore = useUserStore()
const deviceStore = useDeviceStore()
const deviceStatusStore = useDeviceStatusStore()
const messageStore = useMessageStore()
const historyStore = useHistoryStore()
const aiMessageStore = useAiMessageStore()

// 登录方法
const login = async () => {
  try {
    await form.value.validate()
    // 表单验证成功，显示滑动验证
    isShowSliderVerify.value = true
  } catch (error) {
    console.log('表单验证失败:', error)
  }
}

// 滑动验证成功回调
const onVerifySuccess = async (data) => {
  console.log('滑动验证成功，验证数据:', data)
  
  // 将验证数据添加到登录表单
  formModel.value.nonceStr = data.nonceStr
  formModel.value.value = data.value
  
  // 执行实际登录
  await performLogin()
}

// 滑动验证失败回调
const onVerifyFail = (message) => {
  console.log('滑动验证失败:', message)
  ElMessage.error('验证失败，请重试')
}

// 需要重新验证回调
const onVerifyAgain = () => {
  console.log('需要重新验证')
  ElMessage.warning('滑动操作异常，请重试')
}

// 执行实际登录
const performLogin = async () => {
  try {
    loading.value = true
    
    const res = await userLoginService({
      username: formModel.value.username,
      password: formModel.value.password,
      nonceStr: formModel.value.nonceStr,
      value: formModel.value.value
    })
    
    if (res.data.code === '1') {
      // 显示验证成功状态
      if (sliderVerifyRef.value && sliderVerifyRef.value.verifySuccessEvent) {
        sliderVerifyRef.value.verifySuccessEvent()
      }
      
      // 清除所有store的数据
      userStore.$reset()
      deviceStore.$reset()
      deviceStatusStore.$reset()
      messageStore.$reset()
      historyStore.$reset()
      aiMessageStore.$reset()
      
      // 设置新的用户数据
      userStore.setUser(res.data.data)
      userStore.setToken(res.data.data.token)
      
      setTimeout(() => {
        isShowSliderVerify.value = false
        ElMessage.success('登录成功')
        router.push('/')
      }, 500)
    } else {
      throw new Error(res.data.msg || '登录失败')
    }
  } catch (error) {
    console.error('登录失败:', error)
    ElMessage.error(error.message || '登录失败')
    // 显示验证失败状态
    if (sliderVerifyRef.value && sliderVerifyRef.value.verifyFailEvent) {
      sliderVerifyRef.value.verifyFailEvent()
    }
  } finally {
    loading.value = false
  }
}

// 关闭滑动验证弹窗时重置
const onDialogClosed = () => {
  // 清空验证数据
  formModel.value.nonceStr = ''
  formModel.value.value = ''
  
  // 刷新验证码
  if (sliderVerifyRef.value && sliderVerifyRef.value.refresh) {
    sliderVerifyRef.value.refresh()
  }
}

// 注册方法
const register = async () => {
  try {
    await form.value.validate()
    loading.value = true
    
    const res = await userRegisterService({
      username: formModel.value.username,
      password: formModel.value.password,
      email: formModel.value.email // 添加邮箱字段
    })
    
    if (res.data.code === '1') {
      ElMessage.success('注册成功')
      isRegister.value = false // 注册成功后切换到登录页
    } else {
      ElMessage.error(res.data.msg || '注册失败')
    }
  } catch (error) {
    console.error('注册失败:', error)
    ElMessage.error(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}

// 切换密码显示
const togglePassword = () => {
  showPassword.value = !showPassword.value
}

// 切换登录/注册表单
const toggleForm = () => {
  isRegister.value = !isRegister.value
  formModel.value = isRegister.value ? 
    { username: '', password: '', email: '' } : // 注册表单：清空所有字段
    { username: 'root', password: '123456', email: '' } // 登录表单：填充默认值
}
</script>

<template>
  <section>
    <img :src="bgImage" class="bg" />
    <!-- 植愈星球标题移到外面 -->
    <h1 class="app-title-main">植愈星球</h1>
    
    <div class="login-container">
      <div class="login-box">
        <div class="title-container">
          <!-- 登录文字移到原来植愈星球的位置 -->
          <h2 class="login-title">{{ isRegister ? '注册' : '登录' }}</h2>
        </div>
        <el-form ref="form" :model="formModel" :rules="rules" class="login-form">
          <el-form-item prop="username">
            <el-input v-model="formModel.username" :prefix-icon="User" :placeholder="isRegister ? '设置用户名' : '用户名 : root'"
              size="large" />
          </el-form-item>


          <el-form-item prop="email" v-if="isRegister">
            <el-input v-model="formModel.email" :prefix-icon="User" :placeholder="isRegister ? '设置邮箱' : '邮箱'"
              size="large" />
          </el-form-item>


          <el-form-item prop="password">
            <el-input v-model="formModel.password" :prefix-icon="Lock" :type="showPassword ? 'text' : 'password'"
              :placeholder="isRegister ? '设置密码' : '密码 : 123456'" size="large"
              @keyup.enter="isRegister ? register() : login()">
              <template #suffix>
                <el-icon class="cursor-pointer" @click="togglePassword">
                  <component :is="showPassword ? View : Hide" />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" class="login-btn" size="large" 
              @click="isRegister ? register() : login()" :loading="loading">
              {{ loading ? (isRegister ? '注册中...' : '登录中...') : (isRegister ? '注册' : '登录') }}
            </el-button>
          </el-form-item>

          <div class="login-options">
            <el-link type="primary" :underline="false" @click="toggleForm">
              {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
            </el-link>
          </div>
        </el-form>
      </div>
    </div>
    <div class="flower">
      <img :src="flowerImage" v-for="n in 7" :key="n" />
    </div>
    
    <!-- 滑动验证弹窗 -->
    <el-dialog 
      title="请拖动滑块完成拼图" 
      v-model="isShowSliderVerify"
      width="360px"
      :close-on-click-modal="false"
      @closed="onDialogClosed"
      append-to-body
    >
      <SliderVerify
        ref="sliderVerifyRef"
        :canvas-width="320"
        :canvas-height="155"
        :accuracy="5"
        slider-hint="向右滑动完成验证"
        @success="onVerifySuccess"
        @fail="onVerifyFail"
        @again="onVerifyAgain"
      />
    </el-dialog>
  </section>
</template>

<style lang="scss" scoped>
.el-input__wrapper {
  --el-input-bg-color: rgba(255, 255, 255, 0.8) !important;
}

.el-button--primary {
  --el-button-hover-bg-color: var(--el-color-primary-light-3) !important;
  --el-button-hover-border-color: var(--el-color-primary-light-3) !important;
  --el-button-active-bg-color: var(--el-color-primary-dark-2) !important;
  --el-button-active-border-color: var(--el-color-primary-dark-2) !important;
}

.el-button--primary:disabled,
.el-button--primary.is-disabled {
  background-color: #a0cfff;
  border-color: #a0cfff;
  color: #ffffff;
  cursor: not-allowed;
}

section {
  width: 100vw;
  height: 100vh;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: #f5f7fa;
}

/* 主标题样式 - 移到外面，添加立体效果 */
.app-title-main {
  font-size: 60px;
  font-weight: 700;
  color: #67c23a;
  margin: 0;
  letter-spacing: 2px;
  position: relative;
  z-index: 3;
  text-align: center;
  /* 立体效果 - 使用更浅的颜色 */
  text-shadow: 
    0 1px 0 #7ed321,
    0 2px 0 #6fc71e,
    0 3px 0 #60bb1b,
    0 4px 0 #51af18,
    0 5px 0 #42a315,
    0 6px 0 #339712,
    0 7px 0 #248b0f,
    0 8px 10px rgba(0, 0, 0, 0.2),
    0 10px 20px rgba(0, 0, 0, 0.1);
  transform: perspective(500px) rotateX(15deg);
  background: linear-gradient(135deg, #9de74a, #7ed321, #67c23a);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 8px 15px rgba(126, 211, 33, 0.3));
}

.bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 0;
}

.login-container {
  position: relative;
  z-index: 2;
  width: 100%;
  display: flex;
  justify-content: center;
}

.login-box {
  width: 460px;
  padding: 40px;
  background: rgba(188, 254, 223, 0.2);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
}

/* 标题相关样式 */
.title-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 30px;
}

/* 登录标题样式 - 移到原来植愈星球的位置 */
.login-title {
  font-size: 35px;
  font-weight: 600;
  color: #67c23a;
  margin: 0;
  letter-spacing: 1px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.app-title {
  font-size: 32px;
  font-weight: 700;
  color: #67c23a;
  margin: 0;
  letter-spacing: 1px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.title-divider {
  width: 80px;
  height: 3px;
  background: linear-gradient(to right, transparent, #67c23a, transparent);
  margin: 12px 0;
  border-radius: 3px;
}

.subtitle {
  font-size: 16px;
  color: #909399;
  font-weight: 500;
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 25px;
  }

  :deep(.el-input) {
    --el-input-hover-border: var(--el-color-primary);
    
    .el-input__wrapper {
      padding: 1px 15px;
      height: 45px;
      border-radius: 8px;
      box-shadow: 0 0 0 1px #dcdfe6 inset;
      transition: all 0.3s ease;
      
      &:hover {
        box-shadow: 0 0 0 1px var(--el-color-primary) inset;
      }
      
      &.is-focus {
        box-shadow: 0 0 0 1px var(--el-color-primary) inset;
      }
    }

    .el-input__inner {
      font-size: 16px;
      font-weight: 500;
      color: #333;
    }

    .el-input__prefix-inner {
      .el-icon {
        font-size: 18px;
        color: var(--el-text-color-secondary);
      }
    }
    
    /* 增大placeholder字体 */
    input::placeholder {
      font-size: 16px;
      color: #999;
    }
  }
}

.login-btn {
  width: 100%;
  height: 45px;
  font-size: 16px;
  border-radius: 8px;
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(var(--el-color-primary-rgb), 0.3);
  }
}

.login-options {
  display: flex;
  justify-content: center;
  margin-top: 15px;
  
  .el-link {
    font-size: 14px;
    transition: all 0.3s ease;
    
    &:hover {
      transform: translateY(-2px);
    }
  }
}

.flower {
  position: fixed;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  z-index: 1;
  pointer-events: none;

  img {
    position: absolute;
    max-width: 15px;
  }
}

@keyframes animate {
  0% {
    opacity: 0;
    top: -10px;
    transform: translateX(20px) rotate(0deg);
  }
  10% { opacity: 1; }
  20% { transform: translateX(-20px) rotate(45deg); }
  40% { transform: translateX(-20px) rotate(90deg); }
  60% { transform: translateX(20px) rotate(180deg); }
  80% { transform: translateX(-20px) rotate(45deg); }
  100% {
    top: 110%;
    transform: translateX(20px) rotate(225deg);
  }
}

.flower img {
  &:nth-child(1) { left: 20%; animation: animate 20s linear infinite; }
  &:nth-child(2) { left: 50%; animation: animate 14s -2s linear infinite; }
  &:nth-child(3) { left: 70%; animation: animate 12s -3s linear infinite; }
  &:nth-child(4) { left: 5%; animation: animate 15s -2s linear infinite; }
  &:nth-child(5) { left: 85%; animation: animate 18s -1s linear infinite; }
  &:nth-child(6) { left: 90%; animation: animate 12s -1s linear infinite; }
  &:nth-child(7) { left: 15%; animation: animate 14s -2s linear infinite; }
}

// 响应式设计
@media (max-width: 768px) {
  .app-title-main {
    font-size: 36px;
    margin-bottom: 20px;
    /* 移动端保持立体效果但减弱，使用更浅的颜色 */
    text-shadow: 
      0 1px 0 #7ed321,
      0 2px 0 #6fc71e,
      0 3px 0 #60bb1b,
      0 4px 0 #51af18,
      0 5px 10px rgba(0, 0, 0, 0.2);
    transform: perspective(300px) rotateX(10deg);
  }
  
  .login-box {
    width: 90%;
    max-width: 460px;
    padding: 30px 20px;
  }
  
  .login-title {
    font-size: 24px;
  }
}

/* 滑动验证组件样式 */
:deep(.slide-verify) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

:deep(.slider) {
  border-radius: 0 0 8px 8px;
}

:deep(.slider-hint) {
  font-size: 14px;
  color: #909399;
}

:deep(.slider.verify-success) {
  background-color: rgba(103, 194, 58, 0.1);
}

:deep(.slider.verify-fail) {
  background-color: rgba(245, 108, 108, 0.1);
}

/* 滑动验证弹窗样式 */
:deep(.el-dialog__header) {
  padding: 15px 20px;
  border-bottom: 1px solid #ebeef5;
}

:deep(.el-dialog__title) {
  font-size: 16px;
  color: #303133;
}

:deep(.el-dialog__body) {
  padding: 20px;
}

@media (max-width: 480px) {
  /* 移动端弹窗适配 */
  :deep(.el-dialog) {
    width: 90% !important;
    margin: 0 auto;
  }
  
  :deep(.slide-verify) {
    width: 100% !important;
  }
}
</style>
