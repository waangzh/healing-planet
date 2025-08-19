<script setup>
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import { updateUserInfo } from '@/api/user';
import { bindDevice } from '@/api/device';
import { uploadFileService } from '@/api/common';
import { useUserStore } from '@/stores/modules/user';
import { VueCropper } from 'vue-cropper';
import 'vue-cropper/dist/index.css';

const userStore = useUserStore();
const userInfo = userStore.user;
const loading = ref(false);

// 临时头像URL和文件
const tempAvatarUrl = ref('');
const avatarFile = ref(null);

// 表单数据
const form = reactive({
  username: userInfo.username,
  alias: userInfo.alias,
  email: userInfo.email,
  phone: userInfo.phone,
  bio: userInfo.bio,
  avatar: userInfo.avatar,
  message: userInfo.message
});

// 表单校验规则
const rules = { 
  alias: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { required: false, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ],
  bio: [
    { max: 200, message: '不能超过 200 个字符', trigger: 'blur' }
  ],
  message: [
    { max: 200, message: '不能超过 200 个字符', trigger: 'blur' }
  ]
};

const formRef = ref(null);

// 裁剪相关
const cropperRef = ref(null);
const cropperVisible = ref(false);
const cropperImg = ref('');

// 绑定设备相关
const deviceBindDialogVisible = ref(false);
const deviceKey = ref('');
const deviceBindLoading = ref(false);
const deviceResult = ref(null);
const hasNewPassword = ref(false);

// 处理文件选择
const handleFileChange = (uploadFile) => {
  const file = uploadFile.raw;
  if (file) {
    // 验证文件
    const isValidType = ['image/jpeg', 'image/png', 'image/gif'].includes(file.type);
    const isLt2M = file.size / 1024 / 1024 < 2;

    if (!isValidType) {
      ElMessage.error('头像只能是 JPG/PNG/GIF 格式!');
      return;
    }
    if (!isLt2M) {
      ElMessage.error('头像大小不能超过 2MB!');
      return;
    }

    // 创建临时预览
    const reader = new FileReader();
    reader.onload = (e) => {
      cropperImg.value = e.target.result;
      cropperVisible.value = true;
    };
    reader.readAsDataURL(file);
  }
};

// 处理裁剪完成
const handleCropFinish = () => {
  if (!cropperRef.value) return;
  
  loading.value = true;
  cropperRef.value.getCropData(async (data) => {
    try {
      // 将base64转换为文件
      const file = dataURLtoFile(data, 'avatar.png');
      const res = await uploadFileService(file);
      
      if (res.data.code === 200) {
        const imageUrl = res.data.data;
        
        // 更新用户信息
        const updateRes = await updateUserInfo({
          ...userInfo,
          avatar: imageUrl
        });

        if (updateRes.data.code === 200) {
          // 更新 store 中的用户信息
          const newUserInfo = {
            ...userInfo,
            avatar: imageUrl
          };
          userStore.setUser(newUserInfo);
          
          // 更新表单数据
          form.avatar = imageUrl;
          
          // 更新本地引用
          userInfo.avatar = imageUrl;
          
          ElMessage.success('头像更新成功');
          cropperVisible.value = false;
        } else {
          ElMessage.error('头像更新失败');
        }
      }
    } catch (error) {
      console.error('头像上传失败:', error);
      ElMessage.error('头像上传失败');
    } finally {
      loading.value = false;
    }
  });
};

// base64转文件
const dataURLtoFile = (dataurl, filename) => {
  const arr = dataurl.split(',');
  const mime = arr[0].match(/:(.*?);/)[1];
  const bstr = atob(arr[1]);
  let n = bstr.length;
  const u8arr = new Uint8Array(n);
  while (n--) {
    u8arr[n] = bstr.charCodeAt(n);
  }
  return new File([u8arr], filename, { type: mime });
};

// 头像上传前的验证
const beforeAvatarUpload = (file) => {
  return false; // 阻止自动上传
};

// 确认上传头像
const confirmUpload = async () => {
  if (!avatarFile.value) return;
  
  try {
    loading.value = true;
    const formData = new FormData();
    formData.append('file', avatarFile.value);
    
    const res = await uploadFileService(avatarFile.value);
    if (res.data.code === 200) {
      const imageUrl = res.data.data;
      
      // 更新用户信息
      const updateRes = await updateUserInfo({
        ...userInfo,
        avatar: imageUrl
      });

      if (updateRes.data.code === 200) {
        // 更新 store 中的用户信息
        const newUserInfo = {
          ...userInfo,
          avatar: imageUrl
        };
        userStore.setUser(newUserInfo);
        
        // 更新表单数据
        form.avatar = imageUrl;
        
        // 更新本地引用
        userInfo.avatar = imageUrl;
        
        ElMessage.success('头像更新成功');
        
        // 清理临时预览
        URL.revokeObjectURL(tempAvatarUrl.value);
        tempAvatarUrl.value = '';
        avatarFile.value = null;
      } else {
        ElMessage.error('头像更新失败');
      }
    }
  } catch (error) {
    console.error('头像上传失败:', error);
    ElMessage.error('头像上传失败');
  } finally {
    loading.value = false;
  }
};

// 取消上传
const cancelUpload = () => {
  if (tempAvatarUrl.value) {
    URL.revokeObjectURL(tempAvatarUrl.value);
  }
  tempAvatarUrl.value = '';
  avatarFile.value = null;
};

// 更新用户信息
const handleUpdateUserInfo = async (data = form) => {
  try {
    loading.value = true;
    const res = await updateUserInfo(data);
    if (res.data.code === 200) {
      ElMessage.success('更新成功');
      // 更新 store 中的用户信息
      userStore.setUser({ ...userInfo, ...data });
    }
  } catch (error) {
    console.error('更新失败:', error);
    ElMessage.error('更新失败');
  } finally {
    loading.value = false;
  }
};

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      await handleUpdateUserInfo();
    }
  });
};

// 重置表单
const handleReset = () => {
  // 重置表单数据到初始状态
  Object.assign(form, {
    username: userInfo.username,
    alias: userInfo.alias,
    email: userInfo.email,
    phone: userInfo.phone,
    bio: userInfo.bio,
    message: userInfo.message
  });
  
  // 重置表单校验状态
  if (formRef.value) {
    formRef.value.resetFields();
  }
  
  ElMessage.success('已重置表单');
};

// 处理绑定设备
const handleBindDevice = async () => {
  if (!deviceKey.value) {
    ElMessage.warning('请输入设备密钥');
    return;
  }
  
  try {
    deviceBindLoading.value = true;
    const res = await bindDevice(deviceKey.value);
    
    if (res.data.code === 200) {
      deviceResult.value = res.data.data;
      
      // 判断是否是首次绑定（有密码）
      hasNewPassword.value = !res.data.data.password.includes('您已获取过密码');
      
      if (hasNewPassword.value) {
        ElMessage.success('设备绑定成功，请保存您的账号和密码');
      } else {
        ElMessage.success('设备绑定成功');
      }
    } else {
      ElMessage.error(res.data.message || '绑定失败');
    }
  } catch (error) {
    console.error('设备绑定失败:', error);
    ElMessage.error('设备绑定失败，请重试');
  } finally {
    deviceBindLoading.value = false;
  }
};

// 关闭绑定设备对话框
const closeDeviceBindDialog = () => {
  deviceBindDialogVisible.value = false;
  deviceKey.value = '';
  deviceResult.value = null;
  hasNewPassword.value = false;
};
</script>

<template>
  <div class="setting-container">
    <div class="setting-card">
      <h2 class="setting-title">个人设置</h2>
      
      <!-- 头像上传 -->
      <div class="avatar-section">
        <div class="avatar-label">头像</div>
        <div class="avatar-content">
          <div class="avatar-wrapper">
            <img 
              :src="tempAvatarUrl || userInfo.avatar" 
              :alt="userInfo.username" 
              class="avatar-preview"
            >
            <div class="avatar-overlay">
              <el-upload
                class="avatar-uploader"
                :show-file-list="false"
                :before-upload="beforeAvatarUpload"
                :auto-upload="false"
                @change="handleFileChange"
              >
                <div class="upload-mask">
                  <i class="el-icon-plus"></i>
                  <span>点击更换头像</span>
                </div>
              </el-upload>
            </div>
          </div>
          
          <!-- 确认取消按钮 -->
          <div v-if="tempAvatarUrl" class="avatar-actions">
            <el-button type="primary" size="small" @click="confirmUpload">确认更换</el-button>
            <el-button size="small" @click="cancelUpload">取消</el-button>
          </div>
        </div>
      </div>

      <!-- 头像裁剪对话框 -->
      <el-dialog
        v-model="cropperVisible"
        title="裁剪头像"
        width="600px"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        destroy-on-close
      >
        <div class="cropper-container">
          <vue-cropper
            ref="cropperRef"
            :img="cropperImg"
            :info="true"
            :outputSize="1"
            :outputType="'png'"
            :autoCrop="true"
            :fixed="true"
            :fixedNumber="[1, 1]"
            :autoCropWidth="200"
            :autoCropHeight="200"
            :centerBox="true"
            :high="true"
          />
        </div>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cropperVisible = false">取消</el-button>
            <el-button 
              type="primary" 
              @click="handleCropFinish"
              :loading="loading"
            >
              确定
            </el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 个人信息表单 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="setting-form"
      >
        <el-form-item label="用户名" disabled>
          <el-input v-model="form.username" disabled/>
        </el-form-item>

        <el-form-item label="昵称" prop="alias">
          <el-input v-model="form.alias" placeholder="请输入昵称" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>

        <el-form-item label="个人简介" prop="bio">
          <el-input
            v-model="form.bio"
            type="textarea"
            :rows="4"
            placeholder="介绍一下自己吧..."
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="个性签名" prop="message">
          <el-input
            v-model="form.message"
            type="textarea"
            :rows="4"
            placeholder="个性签名..."
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">保存修改</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="deviceBindDialogVisible = true">绑定设备</el-button>
        </el-form-item>
      </el-form>

      <!-- 绑定设备对话框 -->
      <el-dialog
        v-model="deviceBindDialogVisible"
        title="绑定设备"
        width="500px"
        :close-on-click-modal="false"
        :before-close="closeDeviceBindDialog"
      >
        <div v-if="!deviceResult" class="device-bind-form">
          <p class="bind-tip">请输入设备密钥进行绑定</p>
          <el-input 
            v-model="deviceKey" 
            placeholder="请输入设备密钥" 
            @keyup.enter="handleBindDevice"
          />
        </div>
        
        <div v-else class="device-result">
          <el-alert
            v-if="hasNewPassword"
            title="重要提示：请保存好您的账号和密码，密码只显示一次！"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 15px"
          />
          
          <div class="result-item">
            <span class="result-label">账号：</span>
            <span class="result-value">{{ deviceResult.account }}</span>
            <el-button 
              type="primary" 
              size="small" 
              link
              @click="navigator.clipboard.writeText(deviceResult.account)"
            >
              复制
            </el-button>
          </div>
          
          <div class="result-item">
            <span class="result-label">密码：</span>
            <span class="result-value">{{ deviceResult.password }}</span>
            <el-button 
              v-if="hasNewPassword"
              type="primary" 
              size="small" 
              link
              @click="navigator.clipboard.writeText(deviceResult.password)"
            >
              复制
            </el-button>
          </div>
        </div>
        
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="closeDeviceBindDialog">{{ deviceResult ? '关闭' : '取消' }}</el-button>
            <el-button 
              v-if="!deviceResult"
              type="primary" 
              :loading="deviceBindLoading"
              @click="handleBindDevice"
            >
              绑定
            </el-button>
          </div>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.setting-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.setting-card {
  background: var(--comment-bg);
  border-radius: 8px;
  padding: 24px;
}

.setting-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--comment-text-primary);
  margin: 0 0 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--comment-divider);
}

.avatar-section {
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--comment-divider);
}

.avatar-label {
  font-size: 14px;
  color: var(--comment-text-regular);
  margin-bottom: 16px;
}

.avatar-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.avatar-wrapper {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  border: 3px solid var(--el-color-primary-light-8);

  &:hover .avatar-overlay {
    opacity: 1;
  }
}

.avatar-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
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

  .avatar-uploader {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
  }
}

.upload-mask {
  color: #fff;
  text-align: center;
  font-size: 14px;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;

  i {
    font-size: 24px;
    margin-bottom: 8px;
  }

  span {
    display: block;
  }
}

:deep(.el-upload) {
  width: 100%;
  height: 100%;
  display: block;
}

:deep(.el-upload-dragger) {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.setting-form {
  :deep(.el-form-item__label) {
    color: var(--comment-text-regular);
  }

  :deep(.el-input__inner) {
    background-color: var(--comment-input-bg);
    border-color: var(--comment-divider);
    color: var(--comment-text-primary);

    &:focus {
      background-color: var(--comment-input-hover);
    }

    &:disabled {
      background-color: var(--comment-hover-bg);
      color: var(--comment-text-secondary);
    }
  }

  :deep(.el-textarea__inner) {
    background-color: var(--comment-input-bg);
    border-color: var(--comment-divider);
    color: var(--comment-text-primary);

    &:focus {
      background-color: var(--comment-input-hover);
    }
  }
}

.el-form-item {
  :deep(.el-form-item__content) {
    .el-button {
      margin-left: 16px;
      
      &:first-child {
        margin-left: 0;
      }
    }
  }
}

.cropper-container {
  height: 400px;
  width: 100%;
  
  :deep(.vue-cropper) {
    height: 100%;
    width: 100%;
    background-color: var(--comment-bg);
  }
}

// 设备绑定样式
.device-bind-form {
  padding: 20px 0;
}

.bind-tip {
  font-size: 14px;
  color: var(--comment-text-secondary);
  margin-bottom: 15px;
  line-height: 1.5;
}

.device-result {
  padding: 10px;
}

.result-item {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
  padding: 10px;
  background-color: var(--comment-input-bg);
  border-radius: 4px;
}

.result-label {
  font-weight: 600;
  color: var(--comment-text-primary);
  margin-right: 8px;
}

.result-value {
  flex: 1;
  color: var(--comment-text-regular);
  word-break: break-all;
}
</style>
