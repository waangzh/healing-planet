<script setup>
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores'
import { userUpdateInfoService, userUpdatePasswordService } from '@/api/user'
import { userUploadImageService } from '@/api/common'
import { Camera, Lock, Upload, User, Message, Phone, Check, Key, Close } from '@element-plus/icons-vue'
import avatarSvg from '@/assets/默认头像.svg'

const userStore = useUserStore()

// 用户信息
const userInfo = ref({
  id: userStore.user.id,
  nickName: userStore.user.nickName,
  password: userStore.user.password,
  email: userStore.user.email,
  phone: userStore.user.phone,
  avatar: userStore.user.avatar,
  username: userStore.user.username,
  diyBk: userStore.user.diyBk
})

// 修改密码表单
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 显示修改密码对话框
const showPasswordDialog = ref(false)

// 临时头像URL
const tempAvatarUrl = ref('')
const avatarFile = ref(null)

// 临时背景图URL
const tempBgUrl = ref('')
const bgFile = ref(null)

// 处理头像选择
const onAvatarSelect = (uploadFile) => {
  tempAvatarUrl.value = URL.createObjectURL(uploadFile.raw)
  avatarFile.value = uploadFile.raw
}

// 处理背景图选择
const onBgSelect = (uploadFile) => {
  tempBgUrl.value = URL.createObjectURL(uploadFile.raw)
  bgFile.value = uploadFile.raw
}

// 保存用户信息
const saveUserInfo = async () => {
  try {
    const res = await userUpdateInfoService({
      id: userInfo.value.id,
      nickName: userInfo.value.nickName,
      avatar: userInfo.value.avatar,
      email: userInfo.value.email,
      phone: userInfo.value.phone
    })
    
    if (res.data.code === '1') {
      // 更新 Pinia 存储
      userStore.setUser({
        ...userStore.user,
        ...userInfo.value
      })
      ElMessage.success('保存成功')
    } else {
      ElMessage.error('保存失败')
    }
  } catch (error) {
    console.error('保存用户信息失败:', error)
    ElMessage.error('保存失败')
  }
}

// 修改密码
const changePassword = async () => {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('新密码与确认密码不一致')
    return
  }

  if (passwordForm.value.oldPassword !== userStore.user.password) {
    ElMessage.error('旧密码输入错误')
    return
  }

  try {
    const res = await userUpdatePasswordService({
      id: userInfo.value.id,
      password: passwordForm.value.newPassword
    })

    if (res.data.code === '1') {
      // 更新 Pinia 存储
      userStore.setUser({
        ...userStore.user,
        password: passwordForm.value.newPassword
      })
      
      passwordForm.value = {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
      
      showPasswordDialog.value = false
      ElMessage.success('密码修改成功')
    } else {
      ElMessage.error('密码修改失败')
    }
  } catch (error) {
    console.error('修改密码失败:', error)
    ElMessage.error('修改密码失败')
  }
}

// 上传头像
const handleAvatarUpload = async () => {
  if (!avatarFile.value) return ElMessage.error('请选择头像')
  
  try {
    // 创建 FormData
    const formData = new FormData()
    formData.append('file', avatarFile.value)

    const res = await userUploadImageService(avatarFile.value)
    if (res.data.code === '1') {
      const imageUrl = res.data.data
      
      
      // 更新用户信息中的头像
      const updateRes = await userUpdateInfoService({
        id: userInfo.value.id,
        nickName: userInfo.value.nickName,
        avatar: imageUrl,
        email: userInfo.value.email,
        phone: userInfo.value.phone,
        diyBk: userInfo.value.diyBk
      })

      if (updateRes.data.code === '1') {
        userStore.setUser({
          ...userStore.user,
          avatar: imageUrl
        })
        userInfo.value.avatar = imageUrl
        ElMessage.success('头像更新成功')
      } else {
        ElMessage.error(updateRes.data.msg || '头像更新失败')
      }
    } else {
      ElMessage.error(res.data.msg || '头像上传失败')
    }
  } catch (error) {
    console.error('上传头像失败:', error)
    if (error.response) {
      // 服务器响应了错误状态码
      ElMessage.error(`上传失败: ${error.response.data.msg || '服务器错误'}`)
    } else if (error.request) {
      // 请求发出但没有收到响应
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      // 请求配置出错
      ElMessage.error('上传失败，请稍后重试')
    }
  }
}

// 上传背景图
const handleBgUpload = async () => {
  if (!bgFile.value) return ElMessage.error('请选择背景图')
  
  try {
    // 先上传图片获取URL
    const uploadRes = await userUploadImageService(bgFile.value)
    if (uploadRes.data.code === '1') {
      const imageUrl = uploadRes.data.data
      
      // 使用返回的URL更新用户信息
      const updateRes = await userUpdateInfoService({
        id: userInfo.value.id,
        nickName: userInfo.value.nickName,
        avatar: userInfo.value.avatar,
        email: userInfo.value.email,
        phone: userInfo.value.phone,
        diyBk: imageUrl  // 将上传后的图片URL作为背景图地址
      })

      if (updateRes.data.code === '1') {
        // 更新 store 中的用户信息
        userStore.setUser({
          ...userStore.user,
          diyBk: imageUrl
        })
        // 更新本地数据
        userInfo.value.diyBk = imageUrl
        ElMessage.success('背景图更新成功')
      } else {
        ElMessage.error(updateRes.data.msg || '背景图更新失败')
      }
    } else {
      ElMessage.error(uploadRes.data.msg || '背景图上传失败')
    }
  } catch (error) {
    console.error('上传背景图失败:', error)
    if (error.response) {
      ElMessage.error(`上传失败: ${error.response.data.msg || '服务器错误'}`)
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      ElMessage.error('上传失败，请稍后重试')
    }
  }
}

// 监听 Pinia 存储变化
watch(() => userStore.user, (newValue) => {
  userInfo.value = { ...newValue }
}, { deep: true })
</script>

<template>
  <div class="user-page">
    <!-- 顶部背景区域 -->
    <div class="profile-header">
      <div class="header-overlay"></div>
      <!-- <img src="@/assets/user_bg(1).png" alt="背景图" class="header-bg"> -->
      
      <!-- 用户基本信息 -->
      <div class="user-header-info">
        <div class="avatar-wrapper">
          <el-avatar 
            :size="130" 
            :src="tempAvatarUrl || userInfo.avatar || avatarSvg" 
            class="profile-avatar"
          />
          <div class="avatar-upload-icon">
            <el-upload
              class="avatar-uploader"
              accept="image/*"
              :show-file-list="false"
              :auto-upload="false"
              @change="onAvatarSelect"
            >
              <el-icon><Camera /></el-icon>
            </el-upload>
          </div>
        </div>
        <h1 class="user-name">{{ userStore.user.nickName }}</h1>
        <p class="user-title">{{ userStore.user.username || '未设置昵称' }}</p>
      </div>
    </div>

    <!-- 主要内容区 -->
    <div class="profile-content">
      <div class="profile-card">
        <!-- 快捷操作区 -->
        <div class="quick-actions">
          <el-button 
            type="primary" 
            :icon="Lock"
            round
            @click="showPasswordDialog = true"
          >
            修改密码
          </el-button>
          <el-button 
            type="success" 
            :icon="Upload"
            round
            :disabled="!tempAvatarUrl"
            @click="handleAvatarUpload"
          >
            更新头像
          </el-button>
        </div>

        <!-- 个人信息表单 -->
        <div class="info-section">
          <h3 class="section-title">个人信息</h3>
          <el-form :model="userInfo" label-width="80px" class="info-form">
            <el-form-item label="昵称">
              <el-input 
                v-model="userInfo.nickName" 
                placeholder="设置昵称"
                :prefix-icon="User"
              />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input 
                v-model="userInfo.email" 
                placeholder="设置邮箱"
                :prefix-icon="Message"
              />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input 
                v-model="userInfo.phone" 
                placeholder="设置手机号"
                :prefix-icon="Phone"
              />
            </el-form-item>
            <el-form-item label="背景图">
              <div class="bg-upload-wrapper">
                <div class="bg-preview" :style="{ backgroundImage: `url(${tempBgUrl || userInfo.diyBk})` }">
                  <el-upload
                    class="bg-uploader"
                    accept="image/*"
                    :show-file-list="false"
                    :auto-upload="false"
                    @change="onBgSelect"
                  >
                    <div class="upload-area">
                      <el-icon><Upload /></el-icon>
                      <span>点击更换背景图</span>
                    </div>
                  </el-upload>
                </div>
                <el-button 
                  type="primary"
                  :icon="Upload"
                  :disabled="!tempBgUrl"
                  @click="handleBgUpload"
                  class="upload-btn"
                >
                  更新背景图
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button 
                type="primary" 
                :icon="Check"
                @click="saveUserInfo"
              >
                保存修改
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </div>

    <!-- 修改密码对话框 -->
    <el-dialog
      v-model="showPasswordDialog"
      title="修改密码"
      width="400px"
      center
      destroy-on-close
    >
      <el-form :model="passwordForm" label-width="100px">
        <el-form-item label="旧密码">
          <el-input 
            v-model="passwordForm.oldPassword" 
            type="password" 
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input 
            v-model="passwordForm.newPassword" 
            type="password" 
            show-password
            :prefix-icon="Key"
          />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input 
            v-model="passwordForm.confirmPassword" 
            type="password" 
            show-password
            :prefix-icon="Key"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button :icon="Close" @click="showPasswordDialog = false">取消</el-button>
          <el-button type="primary" :icon="Check" @click="changePassword">确认</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.user-page {
  min-height: 100vh;
  // background-color: #f0f2f5;
}

.profile-header {
  height: 360px;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;

  .header-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 1;
  }

  .header-bg {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    filter: blur(2px);
  }

  .user-header-info {
    position: relative;
    z-index: 2;
    text-align: center;

    .avatar-wrapper {
      position: relative;
      display: inline-block;
      margin-bottom: 20px;

      .profile-avatar {
        border: 4px solid rgba(255, 255, 255, 0.8);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        transition: transform 0.3s ease;

        &:hover {
          transform: scale(1.05);
        }
      }

      .avatar-upload-icon {
        position: absolute;
        height: 25px;
        width: 25px;
        bottom: 0;
        right: 0;
        background: var(--el-color-primary);
        border-radius: 50%;
        padding: 8px;
        cursor: pointer;
        transition: all 0.3s ease;

        &:hover {
          transform: scale(1.1);
          background: var(--el-color-primary-light-3);
        }

        .avatar-uploader {
          height: 100%;

          .el-icon {
            color: white;
            font-size: 25px;
          }
        }
      }
    }

    .user-name {
      font-size: 32px;
      font-weight: 600;
      margin: 0 0 8px;
      text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    }

    .user-title {
      font-size: 18px;
      opacity: 0.9;
      margin: 0;
      text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    }
  }
}

.profile-content {
  margin-top: -60px;
  padding: 0 20px;
  position: relative;
  z-index: 2;
}

.profile-card {
  max-width: 800px;
  margin: 0 auto;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  padding: 30px;
  z-index: 2;
}

.quick-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 30px;
  padding-bottom: 30px;
  border-bottom: 1px solid #eee;
}

.info-section {
  .section-title {
    font-size: 20px;
    color: #2c3e50;
    margin: 0 0 24px;
    padding-left: 12px;
    border-left: 4px solid var(--el-color-primary);
  }

  .info-form {
    max-width: 500px;
    margin: 0 auto;
  }
}

:deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: all 0.3s ease;
  
  &:hover {
    box-shadow: 0 0 0 1px var(--el-color-primary) inset;
  }

  &.is-focus {
    box-shadow: 0 0 0 1px var(--el-color-primary) inset;
  }
}

:deep(.el-button) {
  transition: all 0.3s ease;

  &.is-round {
    padding-left: 24px;
    padding-right: 24px;
  }

  &:not(.is-disabled):hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.dialog-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.bg-upload-wrapper {
  .bg-preview {
    height: 100px;
    width: 200px;
    background-size: cover;
    background-position: center;
    border-radius: 8px;
    overflow: hidden;
    position: relative;
    margin-bottom: 16px;
    border: 2px dashed var(--el-border-color);
    
    &:hover .upload-area {
      opacity: 1;
    }
    
    .upload-area {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      background: rgba(0, 0, 0, 0.5);
      opacity: 0;
      transition: opacity 0.3s;
      cursor: pointer;
      
      .el-icon {
        font-size: 32px;
        color: white;
        margin-bottom: 8px;
      }
      
      span {
        color: white;
        font-size: 14px;
      }
    }
  }
  
  .upload-btn {
    width: 100%;
  }
}
</style>