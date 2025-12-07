<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import avatar from '@/assets/img/用户.svg';
import { useUserStore } from '@/stores/modules/user';
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElPopover } from 'element-plus';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getUnreadMessageCount } from '@/api/message';
import { logout } from '@/api/user';

const searchQuery = ref('');
const isDarkMode = ref(false);
const userStore = useUserStore();
const showDropdown = ref(false);
const router = useRouter();

// 添加消息相关状态
const unreadMessageCount = ref(0);
const ws = ref(null);
const messageList = ref([]);
const showMessageTooltip = ref(false);

// WebSocket连接状态管理
const isConnected = ref(false);
const reconnectCount = ref(0);
const maxReconnectAttempts = 10;
const reconnectInterval = ref(1000); // 初始重连间隔为1秒
const reconnectTimer = ref(null);

onMounted(() => {
  // 初始化主题状态
  isDarkMode.value = document.documentElement.getAttribute('data-theme') === 'dark';
  console.log('初始化中', userStore.user.id);
  
  if (userStore.user.id) {
    initWebSocket();
    fetchUnreadMessageCount();
  } else {
    setTimeout(() => {
      if (userStore.user.id) {
        initWebSocket();
        fetchUnreadMessageCount();
      }
    }, 1000);
  }
  
  // 定时刷新未读消息数
  const intervalId = setInterval(() => {
    if (userStore.user.id) {
      fetchUnreadMessageCount();
    }
  }, 60000); // 每分钟刷新一次
  
  onUnmounted(() => {
    clearInterval(intervalId);
    clearReconnectTimer();
  });
});

// 获取未读消息数量
const fetchUnreadMessageCount = async () => {
  try {
    const res = await getUnreadMessageCount();
    if (res && res.data.code === 200) {
      unreadMessageCount.value = res.data.data || 0;
    }
  } catch (error) {
    console.error('获取未读消息数量失败:', error);
  }
};

// 清除重连定时器
const clearReconnectTimer = () => {
  if (reconnectTimer.value) {
    clearTimeout(reconnectTimer.value);
    reconnectTimer.value = null;
  }
};

// 重连WebSocket
const reconnectWebSocket = () => {
  if (reconnectCount.value >= maxReconnectAttempts) {
    console.log('已达到最大重连次数，停止重连');
    return;
  }
  
  clearReconnectTimer();
  
  // 计算指数退避时间 (1s, 2s, 4s, 8s...)，最大30秒
  const delay = Math.min(30000, reconnectInterval.value * Math.pow(2, reconnectCount.value));
  
  console.log(`WebSocket ${reconnectCount.value + 1}次重连将在${delay}ms后进行`);
  
  reconnectTimer.value = setTimeout(() => {
    console.log(`正在进行第${reconnectCount.value + 1}次重连...`);
    reconnectCount.value++;
    initWebSocket();
  }, delay);
};

// 跳转到消息页面
const goToMessagePage = (tab = 'notification') => {
  router.push({
    path: '/message',
    query: { tab }
  });
};

// 格式化时间
const formatDateTime = (dateString) => {
  if (!dateString) {
    return new Date().toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    }).replace(/\//g, '-');
  }
  
  const date = new Date(dateString);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  
  return `${year}-${month}-${day} ${hours}:${minutes}`;
};

// 获取消息类型显示文本
const getMessageTypeText = (type) => {
  switch (type) {
    case 'COMMENT': return '评论了你的文章';
    case 'LIKE': return '点赞了你的文章';
    case 'FOLLOW': return '关注了你';
    case 'SYSTEM': return '系统通知';
    case 'MENTION': return '在文章中提到了你';
    case '点赞了你的内容': return '点赞了你的内容';
    case '收藏了你的文章': return '收藏了你的文章'; 
    case '关注了你': return '关注了你';
    default: return type || '有新消息';
  }
};

// 处理消息数据，适配不同的消息格式
const processMessageData = (message) => {
  return {
    senderName: message.fromUserName || message.senderName || '未知用户',
    senderAvatar: message.fromUserAvatar || message.senderAvatar || avatar,
    content: message.topic || message.content || '',
    type: message.type || '通知',
    createTime: message.createTime || new Date().toISOString(),
    topicId: message.topicId || '',
    fromUserId: message.fromUserId || ''
  };
};

// 跳转到相关页面
const goToMessageDetail = (message) => {
  if (!message) return;
  
  // 关注消息跳转到用户页面
  if (message.type === '关注了你' || message.type === 'FOLLOW') {
    router.push(`/user/${message.fromUserId}`);
    return;
  }
  
  // 文章相关消息跳转到文章页面
  if (message.topicId) {
    router.push(`/post/${message.topicId}`);
    return;
  }
  
  // 默认跳转到消息页面
  goToMessagePage('notification');
};

const goHome = () => {
  router.push('/');
};

const toggleTheme = () => {
  isDarkMode.value = !isDarkMode.value;
  const theme = isDarkMode.value ? 'dark' : 'light';
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem('theme', theme);
};

const handleCommand = (command) => {
  switch (command) {
    case 'profile':
      // 跳转到个人资料页，确保是自己的个人页面，不带用户名参数
      // 使用location.href强制刷新页面以确保数据更新
      router.push('/user').then(() => {
        window.location.reload();
      });
      break;
    case 'settings':
      // 跳转到设置页
      router.push('/setting');
      break;
    case 'logout':
      logout(userStore.user.username);
      userStore.resetUser();
      router.push('/login');
      // 跳转到登录页
      break;
  }
};

const handleSearch = () => {
  if (!searchQuery.value.trim()) return;
  router.push({
    path: '/search',
    query: {
      keyword: searchQuery.value.trim()
    }
  });
  searchQuery.value = '';
};

const goToSetting = () => {
  router.push('/setting');
};

// 初始化 WebSocket 连接
const initWebSocket = () => {
  console.log('初始化WebSocket连接', userStore.user.id);
  const userId = userStore.user.id;
  if (!userId) {
    console.log('用户ID不存在');
    return;
  }
  
  // 如果已有连接，先关闭
  if (ws.value) {
    try {
      ws.value.close();
    } catch (e) {
      console.error('关闭现有WebSocket连接出错:', e);
    }
  }
  
  try {
    ws.value = new WebSocket(`ws://localhost:8080/notify/${userId}`);
    
    ws.value.onopen = () => {
      console.log('WebSocket 连接成功');
      isConnected.value = true;
      reconnectCount.value = 0; // 重置重连计数
      reconnectInterval.value = 1000; // 重置重连间隔
    };
    
    ws.value.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        // 处理消息格式，确保统一的数据结构
        const processedMessage = processMessageData(message);
        messageList.value.unshift(processedMessage);
        console.log('收到新消息:', processedMessage);
        
        // 收到消息后，刷新未读消息计数
        fetchUnreadMessageCount();
        ElMessage({
          message: `${processedMessage.senderName}${getMessageTypeText(processedMessage.type)}`,
          type: 'info',
          duration: 3000
        });
      } catch (error) {
        console.error('处理WebSocket消息失败:', error, event.data);
      }
    };
    
    ws.value.onerror = (error) => {
      console.error('WebSocket 错误:', error);
      isConnected.value = false;
    };
    
    ws.value.onclose = (event) => {
      console.log('WebSocket 连接关闭', event);
      isConnected.value = false;
      
      // 非正常关闭且不是主动关闭时尝试重连
      if (!event.wasClean) {
        console.log('连接异常关闭，准备重连');
        reconnectWebSocket();
      }
    };
  } catch (error) {
    console.error('WebSocket 初始化失败:', error);
    isConnected.value = false;
    reconnectWebSocket();
  }
};

// 手动重连WebSocket
const manualReconnectWebSocket = () => {
  reconnectCount.value = 0; // 重置重连计数
  reconnectInterval.value = 1000; // 重置重连间隔
  initWebSocket();
};

// 组件卸载时关闭 WebSocket
onUnmounted(() => {
  if (ws.value) {
    ws.value.close();
  }
  clearReconnectTimer();
});
</script>

<template>
  <nav class="top-navbar">
    <div class="navbar-container">
      <div class="logo-section" @click="goHome" title="返回首页">
        <div class="logo">
          <i class="fas fa-leaf"></i>
        </div>
        <span>植愈星球</span>
      </div>
      
      <div class="nav-links">
        <div 
          class="nav-item" 
          :class="{ active: $route.path === '/home' }"
          @click="goHome"
        >
          <span>首页</span>
        </div>
        <div 
          class="nav-item" 
          :class="{ active: $route.path === '/plants' }"
          @click="router.push('/plants')"
        >
          <span>绿植百科</span>
        </div>
        <div class="nav-item"
          :class="{ active: $route.path === '/ai' }"
          @click="router.push('/ai')"
        >
          <span>小绿助手</span>
          <!-- <i class="fas fa-robot"></i> -->
        </div>
        <!-- <div class="nav-item">
          <span>我的网络</span>
        </div> -->
      </div>
      
      <div class="nav-actions">
        <div class="search-box">
          <i class="fas fa-search search-icon"></i>
          <input 
            type="text" 
            v-model="searchQuery" 
            placeholder="搜索..." 
            class="search-input"
            @keyup.enter="handleSearch"
          >
        </div>
        <!-- <div class="icon-btn" @click="toggleTheme">
          <i :class="isDarkMode ? 'fas fa-sun' : 'fas fa-moon'"></i>
        </div> -->
        <div class="icon-btn" @click="goToSetting">
          <i class="fas fa-cog"></i>
        </div>
        <div class="icon-btn" @click="goToMessagePage('chat')">
          <i class="far fa-comment-alt"></i>
        </div>
        
        <!-- 使用 el-popover 实现消息悬浮效果 -->
        <el-popover
          placement="bottom"
          :width="300"
          trigger="hover"
          popper-class="notification-popover"
        >
          <template #reference>
            <div class="icon-btn notification-btn">
              <i class="far fa-bell" @click="goToMessagePage('notification')"></i>
              <span v-if="unreadMessageCount > 0" class="notification-badge">{{ unreadMessageCount }}</span>
            </div>
          </template>
          
          <div class="message-panel">
            <div class="message-panel-header">
              <span>最近消息</span>
              <span class="view-all" @click="goToMessagePage('notification')">查看全部</span>
            </div>
            
            <div class="message-list">
              <div v-if="messageList.length === 0" class="no-message">
                <div class="empty-state">
                  <div class="empty-icon">
                    <i class="far fa-bell-slash"></i>
                  </div>
                  <div class="empty-text">暂无最近消息</div>
                </div>
              </div>
              <div 
                v-else 
                v-for="(message, index) in messageList.slice(0, 5)" 
                :key="index" 
                class="message-item"
                @click="goToMessageDetail(message)"
              >
                <div class="message-avatar">
                  <img :src="message.senderAvatar || avatar" class="msg-avatar" alt="头像">
                </div>
                <div class="message-content">
                  <div class="message-title">
                    <span class="message-sender">{{ message.senderName || '系统' }}</span>
                    <span>{{ getMessageTypeText(message.type) }}</span>
                  </div>
                  <div class="message-text" v-if="message.content">{{ message.content }}</div>
                  <div class="message-time">{{ formatDateTime(message.createTime) }}</div>
                </div>
              </div>
            </div>
          </div>
        </el-popover>
        
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-avatar">
            <img :src="userStore.user.avatar || avatar" :alt="userStore.user.username || '用户头像'">
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <div class="user-info">
                <img :src="userStore.user.avatar || avatar" :alt="userStore.user.username || '用户头像'">
                <div class="info">
                  <div class="username">{{ userStore.user.alias }}</div>
                  <div class="email">{{ userStore.user.email }}</div>
                </div>
              </div>
              <el-dropdown-item command="profile">
                <i class="fas fa-user"></i> 个人中心
              </el-dropdown-item>
              <el-dropdown-item command="settings">
                <i class="fas fa-cog"></i> 设置中心
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <i class="fas fa-sign-out-alt"></i> 退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.top-navbar {
  background-color: var(--el-bg-color-page);
  box-shadow: 0 1px 3px var(--shadow);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1400px;
  margin: 0 auto;
  padding: 8px 20px;
  height: 60px;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 15px;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--primary);
  color: white;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  font-size: 20px;
}

.logo-section span {
  font-size: 20px;
  font-weight: 600;
  color: var(--primary);
  letter-spacing: 1px;
  position: relative;
  transition: color 0.3s ease;
  cursor: pointer;
}

.logo-section:hover span {
  color: #4CAF50;
}

.logo-section span::after {
  content: '';
  position: absolute;
  bottom: -5px;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(45deg, var(--primary), #4CAF50);
  transform: scaleX(0);
  transform-origin: right;
  transition: transform 0.3s ease;
}

.logo-section:hover span::after {
  transform: scaleX(1);
  transform-origin: left;
}

.search-box {
  position: relative;
  width: 240px;
  margin-right: 50px;
}

.search-input {
  background-color: var(--bg);
  border: none;
  border-radius: 10px;
  padding: 8px 15px 8px 35px;
  width: 100%;
  font-size: 14px;
}

.search-input:focus {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-secondary);
}

.nav-links {
  display: flex;
  align-items: center;
}

.nav-item {
  padding: 0 20px;
  height: 60px;
  display: flex;
  align-items: center;
  cursor: pointer;
  color: var(--text-secondary);
  font-weight: 500;
  gap: 5px;
}

.nav-item.active {
  color: var(--primary);
  position: relative;
}

.nav-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background-color: var(--primary);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: var(--bg);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text);
  font-size: 16px;
  transition: all 0.3s ease;
}

.icon-btn:hover {
  background-color: var(--hover);
  transform: scale(1.05);
}

.icon-btn:active {
  transform: scale(0.95);
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 900px) {
  .search-box {
    width: 180px;
  }
  
  .nav-item {
    padding: 0 15px;
  }
}

@media (max-width: 768px) {
  .nav-links {
    display: none;
  }
}

@media (max-width: 480px) {
  .search-box {
    display: none;
  }
}

.user-info {
  padding: 10px 15px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--border);
}

.user-info img {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.user-info .info {
  display: flex;
  flex-direction: column;
}

.user-info .username {
  font-weight: 500;
  color: var(--text);
}

.user-info .email {
  font-size: 12px;
  color: var(--text-secondary);
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 15px;
}

:deep(.el-dropdown-menu__item i) {
  width: 16px;
  text-align: center;
}

:deep(.el-dropdown-menu__item--divided) {
  border-top: 1px solid var(--border);
  margin-top: 5px;
  padding-top: 5px;
}

/* 添加通知样式 */
.notification-btn {
  position: relative;
}

.notification-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background-color: var(--el-color-danger);
  color: white;
  font-size: 12px;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}

/* 通知弹窗样式 */
:deep(.notification-popover) {
  padding: 0;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.message-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.message-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 14px;
  font-weight: 500;
}

.view-all {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 13px;
  transition: opacity 0.2s;
}

.view-all:hover {
  opacity: 0.8;
}

.message-list {
  display: flex;
  flex-direction: column;
  max-height: 350px;
  overflow-y: auto;
  padding: 0;
}

.message-item {
  display: flex;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background-color 0.2s;
}

.message-item:hover {
  background-color: var(--el-fill-color-light);
}

.message-item:last-child {
  border-bottom: none;
}

.message-avatar {
  margin-right: 12px;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-title {
  font-size: 14px;
  margin-bottom: 4px;
  color: var(--el-text-color-primary);
}

.message-sender {
  font-weight: 600;
  margin-right: 4px;
}

.message-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.message-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.no-message {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: 20px 0;
  font-size: 13px;
  width: 100%;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
}

.empty-icon {
  font-size: 36px;
  color: var(--el-text-color-secondary);
  opacity: 0.5;
  margin-bottom: 10px;
}

.empty-text {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
</style> 