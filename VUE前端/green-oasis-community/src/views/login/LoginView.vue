<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { User, Message, Lock, View, Hide } from '@element-plus/icons-vue';
import { login, register, getUserInfo } from '@/api/user';
import { useUserStore } from '@/stores';
import SliderVerify from '@/components/login/sliderVerify.vue';

const router = useRouter();
const userStore = useUserStore();

const isLogin = ref(true);
const showPassword = ref(false);
const loading = ref(false);
const form = ref(null);
const sliderVerifyRef = ref(null);

// 滑动验证控制
const isShowSliderVerify = ref(false);

// 设置默认的登录信息
const loginForm = ref({
  username: 'zmjkk',
  password: '123456',
  rememberMe: false,
  // 滑动验证相关字段
  nonceStr: '',
  value: ''
});

const registerForm = ref({
  username: '',
  password: '',
  checkPass: '',
  email: ''
});

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 15, message: '用户名必须是2-15位的字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^\S{6,15}$/,
      message: '密码必须是6-15位的非空字符',
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

const handleLogin = async () => {   
  // 验证表单
  try {
    await form.value.validate();
    // 表单验证成功，显示滑动验证
    isShowSliderVerify.value = true;
  } catch (error) {
    // console.log('表单验证失败:', error);
  }
};

// 滑动验证成功回调
const onVerifySuccess = async (data) => {
  // console.log('滑动验证成功，验证数据:', data);
  
  // 将验证数据添加到登录表单
  loginForm.value.nonceStr = data.nonceStr;
  loginForm.value.value = data.value;
  
  // 执行实际登录
  await performLogin();
};

// 滑动验证失败回调
const onVerifyFail = (message) => {
  // console.log('滑动验证失败:', message);
  ElMessage.error('验证失败，请重试');
};

// 需要重新验证回调
const onVerifyAgain = () => {
  // console.log('需要重新验证');
  ElMessage.warning('滑动操作异常，请重试');
};

// 执行实际登录
const performLogin = async () => {
  try {
    loading.value = true;
    
    // 构建登录参数，包含验证数据
    const loginParams = {
      username: loginForm.value.username,
      password: loginForm.value.password,
      rememberMe: loginForm.value.rememberMe,
      nonceStr: loginForm.value.nonceStr,
      value: loginForm.value.value
    };
    
    // console.log('发送登录请求，参数:', loginParams);
    
    const res = await login(loginParams.username, loginParams.password, loginParams.nonceStr, loginParams.value);
    
    if (res.data.code === 200) {
      // console.log('登录成功，响应数据:', res.data);
      
      // 显示验证成功状态
      if (sliderVerifyRef.value && sliderVerifyRef.value.verifySuccessEvent) {
        sliderVerifyRef.value.verifySuccessEvent();
      }
      
      userStore.setToken(res.data.data.token);
      // console.log('Token已保存:', res.data.data.token);
      
      setTimeout(() => {
        isShowSliderVerify.value = false;
        ElMessage.success('登录成功');
        
        // console.log('开始获取用户信息...');
        // 获取用户信息
        getUserInfo(loginForm.value.username).then(userInfo => {
          // console.log('用户信息获取成功:', userInfo.data);
          userStore.setUser(userInfo.data.data);
          // console.log('准备跳转到首页...');
          router.push('/home');
        }).catch(error => {
          // console.error('获取用户信息失败:', error);
          // 即使获取用户信息失败，也要跳转到首页
          // console.log('用户信息获取失败，仍然跳转到首页...');
          router.push('/home');
        });
        
        // 备用跳转 - 如果上面的异步操作失败，3秒后强制跳转
        setTimeout(() => {
          // console.log('执行备用跳转...');
          router.push('/home');
        }, 3000);
      }, 500);
    } else {
      throw new Error(res.data.message || '登录失败');
    }
  } catch (error) {
    // console.error('登录失败:', error);
    ElMessage.error(error.message || '登录失败');
    // 显示验证失败状态
    if (sliderVerifyRef.value && sliderVerifyRef.value.verifyFailEvent) {
      sliderVerifyRef.value.verifyFailEvent();
    }
  } finally {
    loading.value = false;
  }
};

// 关闭滑动验证弹窗时重置
const onDialogClosed = () => {
  // 清空验证数据
  loginForm.value.nonceStr = '';
  loginForm.value.value = '';
  
  // 刷新验证码
  if (sliderVerifyRef.value && sliderVerifyRef.value.refresh) {
    sliderVerifyRef.value.refresh();
  }
};

const handleRegister = async () => {
  await form.value.validate();
  try {
    loading.value = true;
    const res = await register(registerForm.value);
    
    if (res.data.code === 200) {
      ElMessage.success('注册成功，请登录');
      isLogin.value = true;
      registerForm.value = {
        username: '',
        password: '',
        checkPass: '',
        email: ''
      };
    }
    else{
      ElMessage.error(res.data.message || '注册失败');
    }
  } catch (error) {
    console.error('注册失败:', error);
    ElMessage.error(error.message || '注册失败');
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <i class="fas fa-leaf"></i>
        <h2>绿植社区</h2>
      </div>
      
      <div class="form-switch">
        <button 
          :class="['switch-btn', { active: isLogin }]"
          @click="isLogin = true"
        >
          登录
        </button>
        <button 
          :class="['switch-btn', { active: !isLogin }]"
          @click="isLogin = false"
        >
          注册
        </button>
      </div>
      
      <!-- 登录表单 -->
      <el-form v-if="isLogin" ref="form" :model="loginForm" :rules="rules" class="login-form">
        <el-form-item prop="username">
          <el-input 
            v-model="loginForm.username" 
            :prefix-icon="User"
            placeholder="用户名 : zmjkk"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input 
            v-model="loginForm.password" 
            :prefix-icon="Lock"
            :type="showPassword ? 'text' : 'password'"
            placeholder="密码 : 123456"
            @keyup.enter="handleLogin"
          >
            <template #suffix>
              <el-icon class="cursor-pointer" @click="showPassword = !showPassword">
                <component :is="showPassword ? View : Hide" />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        
        <div class="form-options">
          <label class="remember-me">
            <input 
              type="checkbox" 
              v-model="loginForm.rememberMe"
            >
            <span>记住我</span>
          </label>
        </div>
        
        <el-button 
          type="primary" 
          class="login-btn" 
          @click="handleLogin"
          :loading="loading"
        >
          {{ loading ? '登录中...' : '登录' }}
        </el-button>
      </el-form>
      
      <!-- 注册表单 -->
      <el-form v-else ref="form" :model="registerForm" :rules="rules" class="register-form">
        <el-form-item prop="username">
          <el-input 
            v-model="registerForm.username" 
            :prefix-icon="User"
            placeholder="用户名"
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input 
            v-model="registerForm.email" 
            :prefix-icon="Message"
            placeholder="邮箱"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input 
            v-model="registerForm.password" 
            :prefix-icon="Lock"
            :type="showPassword ? 'text' : 'password'"
            placeholder="密码"
          >
            <template #suffix>
              <el-icon class="cursor-pointer" @click="showPassword = !showPassword">
                <component :is="showPassword ? View : Hide" />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        
        <el-form-item prop="checkPass">
          <el-input 
            v-model="registerForm.checkPass" 
            :prefix-icon="Lock"
            :type="showPassword ? 'text' : 'password'"
            placeholder="确认密码"
          >
            <template #suffix>
              <el-icon class="cursor-pointer" @click="showPassword = !showPassword">
                <component :is="showPassword ? View : Hide" />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        
        <el-button 
          type="primary" 
          class="register-btn" 
          @click="handleRegister"
          :loading="loading"
        >
          {{ loading ? '注册中...' : '注册' }}
        </el-button>
      </el-form>
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
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: var(--bg);
  padding: 20px;
  box-sizing: border-box;
}

.login-box {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  background-color: var(--surface);
  border-radius: 10px;
  box-shadow: 0 2px 10px var(--shadow);
  box-sizing: border-box;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header i {
  font-size: 40px;
  color: var(--primary);
  margin-bottom: 10px;
}

.login-header h2 {
  margin: 0;
  color: var(--text);
  font-size: 24px;
}

.form-switch {
  display: flex;
  margin-bottom: 30px;
  border-bottom: 1px solid var(--border);
}

.switch-btn {
  flex: 1;
  padding: 10px;
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s;
}

.switch-btn.active {
  color: var(--primary);
  border-bottom: 2px solid var(--primary);
}

.login-form,
.register-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  width: 100%;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-secondary);
  cursor: pointer;
}

.remember-me input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.login-btn,
.register-btn {
  width: 100%;
  padding: 12px;
  background-color: var(--primary);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.login-btn:hover,
.register-btn:hover {
  background-color: var(--primary);
  opacity: 0.9;
}

.login-btn:disabled,
.register-btn:disabled {
  background-color: var(--border);
  cursor: not-allowed;
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
  color: var(--text-secondary);
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
  border-bottom: 1px solid var(--border);
}

:deep(.el-dialog__title) {
  font-size: 16px;
  color: var(--text);
}

:deep(.el-dialog__body) {
  padding: 20px;
}

@media (max-width: 480px) {
  .login-box {
    padding: 30px 20px;
  }
  
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