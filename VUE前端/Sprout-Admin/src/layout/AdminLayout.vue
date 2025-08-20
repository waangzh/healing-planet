<template>
  <div class="admin-layout">
    <!-- 头部导航 -->
    <el-header class="layout-header">
      <div class="header-left">
        <el-button
          type="text"
          :icon="isCollapse ? 'Expand' : 'Fold'"
          @click="toggleSidebar"
        />
        <div class="logo">
          <!-- <img src="/logo.png" alt="Logo" class="logo-img" /> -->
          <span v-if="!isCollapse" class="logo-text">{{ systemConfig.siteName }}</span>
        </div>
      </div>
      
      <div class="header-right">
        <!-- 通知 -->
        <el-badge :value="totalPendingCount" :hidden="totalPendingCount === 0">
          <el-button type="text" :icon="Bell" @click="showNotifications" />
        </el-badge>
        
        <!-- 用户信息 -->
        <el-dropdown @command="handleUserMenuCommand">
          <div class="user-info">
            <el-avatar :src="adminAvatar" :size="32" />
            <span class="username">{{ adminName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <!-- <el-dropdown-item command="profile">个人资料</el-dropdown-item> -->
              <!-- <el-dropdown-item command="settings">系统设置</el-dropdown-item> -->
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="layout-container">
      <!-- 侧边栏 -->
      <el-aside :width="isCollapse ? '64px' : '180px'" class="layout-aside">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :unique-opened="true"
          router
          class="sidebar-menu"
        >
          <template v-for="route in menuRoutes" :key="route.path">
            <el-menu-item
              v-if="!route.children"
              :index="route.path"
              @click="$router.push(route.path)"
            >
              <el-icon>
                <component :is="route.meta.icon" />
              </el-icon>
              <template #title>{{ route.meta.title }}</template>
            </el-menu-item>
            
            <el-sub-menu v-else :index="route.path">
              <template #title>
                <el-icon>
                  <component :is="route.meta.icon" />
                </el-icon>
                <span>{{ route.meta.title }}</span>
              </template>
              <el-menu-item
                v-for="child in route.children"
                :key="child.path"
                :index="child.path"
                @click="$router.push(child.path)"
              >
                {{ child.meta.title }}
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="layout-main">
        <!-- 面包屑导航 -->
        <div class="breadcrumb-container">
          <el-breadcrumb>
            <el-breadcrumb-item to="/">首页</el-breadcrumb-item>
            <el-breadcrumb-item
              v-for="item in breadcrumbs"
              :key="item.path"
              :to="item.path"
            >
              {{ item.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <!-- 页面内容 -->
        <div class="page-content">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </el-main>
    </el-container>

    <!-- 通知抽屉 -->
    <el-drawer v-model="notificationDrawer" title="通知中心" size="400px">
      <div class="notification-content">
        <el-tabs v-model="activeNotificationTab">
          <el-tab-pane label="待审核" name="pending">
            <div class="notification-item">
              <el-badge :value="pendingItems.pendingPosts" :hidden="pendingItems.pendingPosts === 0">
                <div class="notification-card" @click="$router.push('/posts')">
                  <el-icon><Document /></el-icon>
                  <span>待审核帖子</span>
                </div>
              </el-badge>
            </div>
            <div class="notification-item">
              <el-badge :value="pendingItems.pendingComments" :hidden="pendingItems.pendingComments === 0">
                <div class="notification-card" @click="$router.push('/comments')">
                  <el-icon><ChatLineRound /></el-icon>
                  <span>待审核评论</span>
                </div>
              </el-badge>
            </div>
            <div class="notification-item">
              <el-badge :value="pendingItems.pendingReports" :hidden="pendingItems.pendingReports === 0">
                <div class="notification-card" @click="$router.push('/reports')">
                  <el-icon><Warning /></el-icon>
                  <span>待处理举报</span>
                </div>
              </el-badge>
            </div>
          </el-tab-pane>
          <el-tab-pane label="系统消息" name="system">
            <div class="notification-item">
              <el-badge :value="pendingItems.systemMessages" :hidden="pendingItems.systemMessages === 0">
                <div class="notification-card" @click="$router.push('/system-messages')">
                  <el-icon><Bell /></el-icon>
                  <span>系统消息</span>
                </div>
              </el-badge>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { useDashboardStore } from '@/stores/dashboard'
import { Bell, ArrowDown, Document, ChatLineRound, Warning } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()
const dashboardStore = useDashboardStore()

// 响应式数据
const isCollapse = ref(false)
const notificationDrawer = ref(false)
const activeNotificationTab = ref('pending')

// 计算属性
const systemConfig = computed(() => adminStore.systemConfig)
const adminName = computed(() => adminStore.adminName)
const adminAvatar = computed(() => adminStore.adminAvatar)
const totalPendingCount = computed(() => dashboardStore.totalPendingCount)
const pendingItems = computed(() => dashboardStore.pendingItems)

// 当前激活的菜单项
const activeMenu = computed(() => {
  return route.path
})

// 面包屑导航
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(item => item.meta && item.meta.title)
  return matched
})

// 菜单路由
const menuRoutes = computed(() => {
  const routes = router.getRoutes()
  return routes
    .filter(route => route.path !== '/' && route.path !== '/login' && route.meta && route.meta.title && !route.meta.hidden)
    .map(route => ({
      path: route.path,
      meta: route.meta
    }))
})

// 方法
const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
}

const showNotifications = () => {
  notificationDrawer.value = true
}

const handleUserMenuCommand = (command) => {
  switch (command) {
    case 'profile':
      // 跳转到个人资料页面
      break
    case 'settings':
      router.push('/settings')
      break
    case 'logout':
      adminStore.logout()
      router.push('/login')
      break
  }
}

// 监听路由变化，自动关闭通知抽屉
watch(() => route.path, () => {
  notificationDrawer.value = false
})
</script>

<style lang="scss" scoped>
.admin-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.layout-header {
  background: var(--admin-surface);
  border-bottom: 1px solid var(--admin-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  height: 60px !important;
  box-shadow: var(--el-box-shadow-lighter);
  
  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
    
    .logo {
      display: flex;
      align-items: center;
      gap: 8px;
      
      .logo-img {
        width: 32px;
        height: 32px;
      }
      
      .logo-text {
        font-size: 18px;
        font-weight: 600;
        color: var(--admin-primary);
      }
    }
  }
  
  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
    
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      padding: 8px 12px;
      border-radius: 6px;
      transition: background-color 0.3s;
      
      &:hover {
        background-color: var(--admin-primary-lightest);
      }
      
      .username {
        font-size: 14px;
        color: var(--admin-text);
      }
    }
  }
}

.layout-container {
  flex: 1;
  height: calc(100vh - 60px);
}

.layout-aside {
  background: var(--admin-surface);
  border-right: 1px solid var(--admin-border);
  transition: width 0.3s;
  box-shadow: var(--el-box-shadow-lighter);
  
  .sidebar-menu {
    border: none;
    background: var(--admin-surface);
    
    :deep(.el-menu-item) {
      color: var(--admin-text-light);
      
      &:hover {
        color: var(--admin-primary);
        background-color: var(--admin-primary-lightest);
      }
      
      &.is-active {
        color: var(--admin-primary);
        background-color: var(--admin-primary-lightest);
        border-right: 3px solid var(--admin-primary);
      }
    }
    
    :deep(.el-sub-menu__title) {
      color: var(--admin-text-light);
      
      &:hover {
        color: var(--admin-primary);
        background-color: var(--admin-primary-lightest);
      }
    }
    
    :deep(.el-sub-menu.is-active .el-sub-menu__title) {
      color: var(--admin-primary);
    }
  }
}

.layout-main {
  background: var(--admin-bg);
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  
  .breadcrumb-container {
    background: var(--admin-surface);
    padding: 16px 24px;
    border-bottom: 1px solid var(--admin-border);
    
    :deep(.el-breadcrumb__inner) {
      color: var(--admin-text-light);
      
      &.is-link {
        color: var(--admin-primary);
        
        &:hover {
          color: var(--admin-primary-light);
        }
      }
    }
  }
  
  .page-content {
    flex: 1;
    padding: 24px;
    overflow-y: auto;
  }
}

.notification-content {
  .notification-item {
    margin-bottom: 12px;
    
    .notification-card {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      background: var(--admin-primary-lightest);
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.3s;
      border: 1px solid var(--admin-border);
      
      &:hover {
        background: var(--admin-primary-lighter);
        transform: translateY(-1px);
        box-shadow: var(--el-box-shadow-lighter);
      }
      
      .el-icon {
        color: var(--admin-primary);
      }
      
      span {
        color: var(--admin-text);
      }
    }
  }
}

// 页面过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
