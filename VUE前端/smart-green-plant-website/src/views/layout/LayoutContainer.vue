<script setup>
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore, useMessageStore } from '@/stores'
import { ElMessageBox } from 'element-plus'
import {
  HomeFilled,
  Setting,
  QuestionFilled,
  UserFilled,
  Warning,
  Search,
  Bell,
  Histogram,
  Plus,
  Expand,
  ArrowRightBold,
  ArrowLeftBold,
  Refresh
} from '@element-plus/icons-vue'
import { useGoHome } from '@/utils/gohome'
import AvatarFrame from '@/components/AvatarFrame.vue'

const userStore = useUserStore()
const messageStore = useMessageStore()
const route = useRoute()
const { goHome } = useGoHome()

// 从 HomePage 组件获取异常状态
const hasAbnormalData = ref(false)

// 提供给子组件的方法来更新异常状态
const updateAbnormalStatus = (status) => {
  hasAbnormalData.value = status
}

// 消息弹出框是否可见
const messageVisible = ref(false)
// 是否固定显示（点击后）
const isMessageFixed = ref(false)
// 刷新状态
const refreshing = ref(false)

// 添加侧边栏折叠状态
const isCollapse = ref(false)

// 切换折叠状态
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

// 手动刷新消息
const handleRefreshMessages = async () => {
  refreshing.value = true
  try {
    await messageStore.refreshMessages()
  } finally {
    refreshing.value = false
  }
}

// 初始化获取消息
onMounted(async () => {
  await messageStore.fetchMessages()
  await messageStore.fetchUnreadCount()
})

// 处理消息点击
const handleMessageClick = async (message) => {
  messageVisible.value = false
  ElMessageBox.confirm(
    message.content,
    message.title,
    {
      confirmButtonText: '确认',
      cancelButtonText: '关闭',
      type: message.type === '警告' ? 'warning' : 'info',
    }
  ).then(async () => {
    // 标记消息为已读
    if (!message.isRead) {
      await messageStore.markAsRead(message.id)
    }
  }).catch(() => {})
}

// 格式化时间
const formatTime = (timeStr) => {
  const date = new Date(timeStr)
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours()}:${date.getMinutes()}`
}

// 使用计算属性获取背景图片
const backgroundImage = computed(() => {
  return userStore.user?.diyBk || '@/assets/背景图片/layout_bg.png'
})

// 处理点击铃铛
const handleBellClick = () => {
  isMessageFixed.value = !isMessageFixed.value
  messageVisible.value = isMessageFixed.value
}

// 处理鼠标进入
const handleMouseEnter = () => {
  if (!isMessageFixed.value) {
    messageVisible.value = true
  }
}

// 处理鼠标离开
const handleMouseLeave = () => {
  if (!isMessageFixed.value) {
    messageVisible.value = false
  }
}

// 处理点击外部关闭消息
const handleClickOutside = (e) => {
  const messageIcon = document.querySelector('.message-icon')
  const messagePopup = document.querySelector('.message-popup')
  
  if (messageVisible.value && 
      !messageIcon.contains(e.target) && 
      !messagePopup?.contains(e.target)) {
    messageVisible.value = false
    isMessageFixed.value = false
  }
}

// 添加点击外部关闭事件
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '180px'" class="aside-container">
      <div class="aside-header">
        <el-button class="collapse-btn" :icon="Expand" @click="toggleCollapse" v-if="isCollapse" />
        <div class="el-aside__logo" @click="goHome" v-if="!isCollapse" />
      </div>

      <el-menu :default-active="route.path" class="el-menu-vertical" :collapse="isCollapse" router>
        <el-menu-item index="/home">
          <el-icon>
            <HomeFilled />
          </el-icon>
          <template #title>
            <div class="menu-title">
              <span>首页</span>
              <el-icon v-if="hasAbnormalData" class="warning-icon">
                <Warning />
              </el-icon>
            </div>
          </template>
        </el-menu-item>

        <el-menu-item index="/control">
          <el-icon>
            <Setting />
          </el-icon>
          <template #title>设备控制</template>
        </el-menu-item>

        <el-menu-item index="/plant">
          <el-icon>
            <Plus />
          </el-icon>
          <template #title>植物管理</template>
        </el-menu-item>

        <!-- <el-menu-item index="/categories">
          <el-icon>
            <List />
          </el-icon>
          <template #title>植物百科</template>
        </el-menu-item> -->

        <el-menu-item index="/ai">
          <el-icon>
            <QuestionFilled />
          </el-icon>
          <template #title>小绿助手</template>
        </el-menu-item>

        <el-menu-item index="/history">
          <el-icon>
            <Histogram />
          </el-icon>
          <template #title>历史数据</template>
        </el-menu-item>

        <el-menu-item index="/user">
          <el-icon>
            <UserFilled />
          </el-icon>
          <template #title>我的</template>
        </el-menu-item>
      </el-menu>

      <div class="aside-collapse-btn" @click="toggleCollapse">
        <el-icon>
          <component :is="isCollapse ? ArrowRightBold : ArrowLeftBold" />
        </el-icon>
      </div>
    </el-aside>

    <el-container class="right-container">
      <el-header>
        <div class="header-left">
          <span class="welcome-text">欢迎进入 在线管理系统</span>
        </div>
        <div class="header-right">
          <!-- <el-icon>
            <Search />
          </el-icon> -->
          <div class="message-icon" 
               @click="handleBellClick"
               @mouseenter="handleMouseEnter"
               @mouseleave="handleMouseLeave">
            <el-badge :value="messageStore.unreadCount" :hidden="messageStore.unreadCount === 0" class="message-badge">
              <div class="bell">
                <el-icon>
                  <Bell />
                </el-icon>
              </div>
            </el-badge>

            <div v-show="messageVisible" class="message-popup">
              <div class="popup-header">
                <div class="header-left">
                  <span class="popup-title">消息通知</span>
                  <span class="unread-count" v-if="messageStore.unreadCount > 0">
                    {{ messageStore.unreadCount }}条未读
                  </span>
                </div>
                <el-button 
                  type="text" 
                  size="small" 
                  @click="handleRefreshMessages"
                  :loading="refreshing"
                  class="refresh-btn"
                  title="刷新消息"
                >
                  <el-icon>
                    <Refresh />
                  </el-icon>
                </el-button>
              </div>

              <div class="message-list">
                <template v-if="messageStore.messages.length">
                  <div v-for="msg in messageStore.messages" :key="msg.id" class="message-item"
                    :class="{ unread: !msg.isRead }" @click="handleMessageClick(msg)">
                    <div class="message-header">
                      <div class="left">
                        <el-tag :type="msg.type === '警告' ? 'warning' : 'info'" size="small" effect="plain">
                          {{ msg.type }}
                        </el-tag>
                        <span class="title">{{ msg.title }}</span>
                      </div>
                      <div class="right">
                        <span class="status" :class="{ unread: !msg.isRead }">
                          {{ msg.isRead ? '已读' : '未读' }}
                        </span>
                        <span class="time">{{ formatTime(msg.createdTime) }}</span>
                      </div>
                    </div>
                    <div class="message-content">{{ msg.content }}</div>
                  </div>
                </template>
                <div v-else class="no-message">
                  暂无消息
                </div>
              </div>
            </div>
          </div>
          <AvatarFrame />
          <span class="user-dropdown">
            欢迎您，{{ userStore.user.nickName || "游客" }}
            <el-icon>
            </el-icon>
          </span>
        </div>
      </el-header>

      <el-main class="main-container">
        <div class="main-bg" :style="{
          backgroundImage: `url(${backgroundImage})`
        }"></div>
        <router-view @update-abnormal="updateAbnormalStatus"></router-view>
      </el-main>

      <el-footer>植愈星球 ©2025 Created by 植愈星球项目组</el-footer>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
  background-color: var(--el-bg-color);

  .aside-container {
    position: relative;
    z-index: 2;
    transition: width 0.3s;
    background-color: var(--el-menu-bg-color);
    border-right: 1px solid var(--el-border-color-light);
    overflow: hidden;
    
    .aside-header {
      height: 60px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 16px;
      border-bottom: 1px solid var(--el-border-color-light);

      .el-aside__logo {
        width: 120px;
        height: 40px;
        background: url('@/assets/绿植logo.png') no-repeat center/contain;
        cursor: pointer;
        transition: all 0.3s ease;

        &:hover {
          transform: scale(1.02);
        }
      }
    }

    .aside-collapse-btn {
      position: absolute;
      top: 60%;
      right: -12px;
      transform: translateY(-50%);
      width: 24px;
      height: 50px;
      background-color: rgba(128, 128, 128, 0.1);
      border-radius: 6px 0 0 6px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;
      color: white;
      transition: all 0.3s;
      z-index: 10;
      // box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);

      &:hover {
        background-color: rgba(128, 128, 128, 0.2);
        width: 28px;
        right: -14px;
      }

      .el-icon {
        font-size: 16px;
        transition: transform 0.3s;
      }

      &:hover .el-icon {
        transform: scale(1.2);
      }
    }

    .el-menu-vertical {
      border-right: none;

      &:not(.el-menu--collapse) {
        width: 180px;
      }

      .el-menu-item {
        display: flex;
        align-items: center;
        padding: 0 16px;
        height: 50px;

        .el-icon {
          font-size: 18px;
          margin-right: 12px;
        }

        &.is-active {
          background: var(--el-color-primary-light-9);
          
          &::before {
            content: '';
            position: absolute;
            left: 0;
            top: 0;
            bottom: 0;
            width: 4px;
            background: var(--el-color-primary);
          }
        }

        .menu-title {
          display: flex;
          align-items: center;
          gap: 8px;

          .warning-icon {
            color: var(--el-color-warning);
            font-size: 14px;
          }
        }
      }
    }
  }

  .el-header {
    background-color: var(--el-color-primary);
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    height: 48px;
    color: #fff;

    .header-left {
      .welcome-text {
        font-size: 14px;
        color: #fff;
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 20px;

      .el-icon {
        font-size: 20px;
        color: #fff;
        cursor: pointer;

        &:hover {
          opacity: 0.8;
        }
      }

      .user-dropdown {
        display: flex;
        align-items: center;
        gap: 4px;
        color: #fff;
        cursor: pointer;
        font-size: 14px;

        .el-icon {
          font-size: 12px;
        }
      }

      .message-icon {
        position: relative;
        cursor: pointer;
        padding: 0 16px;
        height: 100%;
        display: flex;
        align-items: center;
        z-index: 1001; // 调整消息图标的z-index

        .el-icon {
          display: flex;
          align-items: center;
          justify-content: center;  
        }
        
        .message-badge {
          :deep(.el-badge__content) {
            background-color: var(--el-color-danger);
          }
        }
      }
    }
  }

  .el-main {
    background-color: transparent;
    padding: 20px;

    :deep(.el-card) {
      border-radius: 8px;
      border: none;
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
    }
  }

  .el-footer {
    background-color: #ffffff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    color: #666;
    border-top: 1px solid #e4e7ed;
  }
}

@keyframes pulse {
  0% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.2);
  }

  100% {
    transform: scale(1);
  }
}

// 覆盖 element-plus 的一些默认样式
:deep(.el-dropdown-menu__item) {
  padding: 8px 20px;
}

:deep(.el-avatar) {
  background-color: #fff;
}

.message-popup {
  position: absolute;
  top: 100%;
  right: 0;
  width: 360px;
  max-height: 480px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  z-index: 1002; // 设置消息弹出框的z-index高于消息图标但低于其他弹出层
  
  .popup-header {
    padding: 12px 16px;
    border-bottom: 1px solid var(--el-border-color-light);
    display: flex;
    justify-content: space-between;
    align-items: center;
    background-color: var(--el-bg-color);
    border-radius: 8px 8px 0 0;
    
    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
      
      .popup-title {
        font-size: 15px;
        font-weight: 500;
        color: var(--el-text-color-primary);
      }
      
      .unread-count {
        font-size: 12px;
        color: var(--el-color-danger);
        background: var(--el-color-danger-light-9);
        padding: 2px 8px;
        border-radius: 10px;
      }
    }
    
    .refresh-btn {
      padding: 4px 8px;
      color: var(--el-color-primary);
      
      // 新增：让图标更明显
      .el-icon {
        font-size: 20px; // 原为14px
        color: var(--el-color-primary); // 强制主色
        font-weight: bold;
        filter: drop-shadow(0 0 2px var(--el-color-primary));
      }
      &:hover {
        background: var(--el-color-primary-light-9);
        .el-icon {
          color: var(--el-color-success); // 悬停时变为绿色
          filter: drop-shadow(0 0 4px var(--el-color-success));
        }
      }
    }
  }
  
  .message-list {
    max-height: 400px;
    overflow-y: auto;
    
    .message-item {
      padding: 12px 16px;
      border-bottom: 1px solid var(--el-border-color-lighter);
      cursor: pointer;
      transition: all 0.3s;
      
      &:hover {
        background: var(--el-fill-color-light);
      }
      
      &.unread {
        background: var(--el-color-primary-light-9);
        
        &:hover {
          background: var(--el-color-primary-light-8);
        }
      }
      
      .message-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
        
        .left {
          display: flex;
          align-items: center;
          gap: 8px;
          
          .el-tag {
            font-size: 12px;
            padding: 0 6px;
            height: 20px;
            line-height: 18px;
          }
          
          .title {
            color: var(--el-text-color-primary);
            font-size: 14px;
          }
        }
        
        .right {
          display: flex;
          align-items: center;
          gap: 12px;
          
          .status {
            font-size: 12px;
            color: var(--el-text-color-secondary);
            
            &.unread {
              color: var(--el-color-danger);
              font-weight: bold;
            }
          }
          
          .time {
            font-size: 12px;
            color: var(--el-text-color-secondary);
          }
        }
      }
      
      .message-content {
        color: var(--el-text-color-regular);
        font-size: 13px;
        line-height: 1.5;
        margin-left: 44px;
      }
    }
    
    .no-message {
      padding: 32px 0;
      text-align: center;
      color: var(--el-text-color-secondary);
      font-size: 14px;
    }
  }
}

// 美化滚动条
.message-list {
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-track {
    background: var(--el-fill-color-blank);
  }
  
  &::-webkit-scrollbar-thumb {
    background: var(--el-border-color);
    border-radius: 3px;
    
    &:hover {
      background: var(--el-border-color-darker);
    }
  }
}

.main-container {
  position: relative;
  padding: 20px;
  height: 100%;

  .main-bg {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-size: cover;
    background-position: center;
    opacity: 0.1;
    z-index: 0;
    transition: background-image 0.3s ease;
  }

  :deep(> *) {
    position: relative;
    z-index: 1;
  }
}
</style>