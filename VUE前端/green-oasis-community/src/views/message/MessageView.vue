<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useUserStore } from '@/stores';
import { getMessageNotifications, markMessageAsRead, deleteMessage, getUnreadMessageCount } from '@/api/message';
import { ElMessage, ElEmpty, ElIcon } from 'element-plus';
import { Link, Document } from '@element-plus/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import avatar from '@/assets/img/用户.svg';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const userId = userStore.user.id;

// 状态变量
const notifications = ref([]);
const unreadCount = ref(0);
const isLoading = ref(true);
const activeTab = ref('notification'); // 'notification' 或 'chat'
const contacts = ref([]);
const currentContact = ref(null);
const messages = ref([]);
const newMessage = ref('');

// 批量操作相关
const selectedNotifications = ref([]);
const isSelectMode = ref(false);

// 模拟的联系人数据
const mockContacts = [
  { id: '1', name: '陈小绿', avatar: 'https://s3.ax1x.com/2020/12/01/DfHNo4.jpg', lastMessage: '你好，请问这个植物怎么养？', unread: 2, lastTime: '10:30' },
  { id: '2', name: '王花花', avatar: 'https://s3.ax1x.com/2020/12/01/DfHNo4.jpg', lastMessage: '我的植物长虫子了，怎么办？', unread: 0, lastTime: '昨天' },
  { id: '3', name: '李多肉', avatar: 'https://s3.ax1x.com/2020/12/01/DfHNo4.jpg', lastMessage: '谢谢你的建议！', unread: 0, lastTime: '周一' },
];

// 模拟的聊天消息数据
const mockMessages = {
  '1': [
    { id: '1', senderId: '1', content: '你好，请问这个植物怎么养？', time: '10:25', type: 'received' },
    { id: '2', senderId: userId, content: '您好，可以发张照片看看是什么植物吗？', time: '10:28', type: 'sent' },
    { id: '3', senderId: '1', content: '好的，稍等', time: '10:30', type: 'received' },
  ],
  '2': [
    { id: '1', senderId: '2', content: '我的植物长虫子了，怎么办？', time: '昨天 18:30', type: 'received' },
    { id: '2', senderId: userId, content: '可以尝试用肥皂水喷洒，或者使用专业的植物杀虫剂', time: '昨天 18:45', type: 'sent' },
  ],
  '3': [
    { id: '1', senderId: '3', content: '你推荐的多肉植物我买了，非常漂亮！', time: '周一 09:15', type: 'received' },
    { id: '2', senderId: userId, content: '很高兴你喜欢，记得不要浇太多水哦', time: '周一 10:20', type: 'sent' },
    { id: '3', senderId: '3', content: '谢谢你的建议！', time: '周一 10:25', type: 'received' },
  ],
};

// 加载通知消息
const loadNotifications = async () => {
  try {
    isLoading.value = true;
    const res = await getMessageNotifications();
    if (res.data && res.data.code === 200 && res.data.data) {
      notifications.value = res.data.data;
    }
  } catch (error) {
    console.error('获取通知失败:', error);
    ElMessage.error('获取通知失败');
  } finally {
    isLoading.value = false;
  }
};

// 加载未读消息数量
const loadUnreadCount = async () => {
  try {
    const res = await getUnreadMessageCount();
    if (res && res.data.code === 200) {
      unreadCount.value = res.data.data || 0;
    }
  } catch (error) {
    console.error('获取未读消息数量失败:', error);
  }
};

// 标记消息为已读
const markAsRead = async (messageId, showMessage = true) => {
  try {
    const res = await markMessageAsRead(messageId);
    if (res && res.data.code === 200) {
      // 更新本地状态
      const index = notifications.value.findIndex(notification => notification.id === messageId);
      if (index !== -1) {
        notifications.value[index].isRead = true;
      }
      // 重新获取未读消息数量
      loadUnreadCount();
      // 触发消息已读事件，通知其他组件更新
      window.dispatchEvent(new CustomEvent('message-read'));
      if (showMessage) {
        ElMessage.success('标记为已读');
      }
    }
  } catch (error) {
    console.error('标记消息已读失败:', error);
    if (showMessage) {
      ElMessage.error('操作失败');
    }
  }
};

// 删除消息
const removeMessage = async (messageId) => {
  try {
    // 传入数组格式，即使只删除一个消息
    const res = await deleteMessage([messageId]);
    if (res && res.data.code === 200) {
      // 从列表中移除该消息
      notifications.value = notifications.value.filter(notification => notification.id !== messageId);
      // 重新获取未读消息数量
      loadUnreadCount();
      // 触发消息已读事件，通知其他组件更新
      window.dispatchEvent(new CustomEvent('message-read'));
      ElMessage.success('删除成功');
    }
  } catch (error) {
    console.error('删除消息失败:', error);
    ElMessage.error('删除失败');
  }
};

// 处理通知点击事件
const handleNotificationClick = async (notification) => {
  // 首先标记为已读（静默模式，不显示提示）
  if (!notification.isRead) {
    await markAsRead(notification.id, false);
  }
  
  // 根据 objectType 进行不同的跳转
  // 如果是帖子、文章或回复，均跳转到对应文章详情页
  if ((notification.objectType === '帖子' || notification.objectType === '文章' || notification.objectType === '回复') && notification.objectId) {
    // 跳转到文章详情页
    try {
      router.push(`/post/${notification.objectId}`);
      // ElMessage.success('正在跳转到文章页面...');
    } catch (error) {
      console.error('跳转失败:', error);
      ElMessage.error('跳转失败，请稍后重试');
    }
  } else if (notification.objectType === '用户' && notification.senderName) {
    // 跳转到用户主页
    goToUserHome(notification);
  } else if (notification.objectId) {
    // 其他类型，可以根据需要扩展
    console.log('未处理的通知类型:', notification.objectType);
    ElMessage.info(`暂不支持跳转到 ${notification.objectType} 页面`);
  }
};

// 跳转到用户主页
const goToUserHome = (notification) => {
  const username = notification.senderName || '';
  if (!username) return;
  // 如果是自己则跳转自己的主页（可能路由无需用户名）
  if (username === userStore.user.username) {
    router.push('/user');
  } else {
    router.push(`/user/${encodeURIComponent(username)}`);
  }
};

// 全部标记为已读
const markAllAsRead = async () => {
  if (notifications.value.length === 0) return;
  
  try {
    // 过滤出未读消息的ID列表
    const unreadIds = notifications.value
      .filter(notification => !notification.isRead)
      .map(notification => notification.id);
    
    if (unreadIds.length === 0) {
      ElMessage.info('没有未读消息');
      return;
    }
    
    // 为每个未读消息发送已读请求
    const promises = unreadIds.map(id => markMessageAsRead(id));
    await Promise.all(promises);
    
    // 更新本地状态
    notifications.value.forEach(notification => {
      notification.isRead = true;
    });
    
    // 重新获取未读消息数量
    loadUnreadCount();
    
    // 触发消息已读事件，通知其他组件更新
    window.dispatchEvent(new CustomEvent('message-read'));
    
    ElMessage.success('已将所有消息标记为已读');
  } catch (error) {
    console.error('标记所有消息为已读失败:', error);
    ElMessage.error('操作失败');
  }
};

// 切换选择模式
const toggleSelectMode = () => {
  isSelectMode.value = !isSelectMode.value;
  if (!isSelectMode.value) {
    selectedNotifications.value = [];
  }
};

// 选择/取消选择通知
const toggleSelectNotification = (notificationId) => {
  const index = selectedNotifications.value.indexOf(notificationId);
  if (index > -1) {
    selectedNotifications.value.splice(index, 1);
  } else {
    selectedNotifications.value.push(notificationId);
  }
};

// 全选/取消全选
const toggleSelectAll = () => {
  if (selectedNotifications.value.length === notifications.value.length) {
    selectedNotifications.value = [];
  } else {
    selectedNotifications.value = notifications.value.map(n => n.id);
  }
};

// 批量删除
const batchDelete = async () => {
  if (selectedNotifications.value.length === 0) {
    ElMessage.warning('请先选择要删除的消息');
    return;
  }
  
  try {
    // 批量删除选中的消息 - 传入数组
    await deleteMessage(selectedNotifications.value);
    
    // 从列表中移除已删除的消息
    notifications.value = notifications.value.filter(
      notification => !selectedNotifications.value.includes(notification.id)
    );
    
    // 清空选择并退出选择模式
    selectedNotifications.value = [];
    isSelectMode.value = false;
    
    // 重新获取未读消息数量
    loadUnreadCount();
    
    // 触发消息已读事件，通知其他组件更新
    window.dispatchEvent(new CustomEvent('message-read'));
    
    ElMessage.success('批量删除成功');
  } catch (error) {
    console.error('批量删除失败:', error);
    ElMessage.error('批量删除失败');
  }
};

// 选择联系人
const selectContact = (contact) => {
  currentContact.value = contact;
  messages.value = mockMessages[contact.id] || [];
  
  // 标记为已读
  if (contact.unread > 0) {
    contact.unread = 0;
  }
};

// 发送消息
const sendMessage = () => {
  if (!newMessage.value.trim() || !currentContact.value) return;
  
  const message = {
    id: Date.now().toString(),
    senderId: userId,
    content: newMessage.value,
    time: new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}),
    type: 'sent'
  };
  
  messages.value.push(message);
  
  // 更新联系人最后一条消息
  const contactIndex = contacts.value.findIndex(c => c.id === currentContact.value.id);
  if (contactIndex !== -1) {
    contacts.value[contactIndex].lastMessage = newMessage.value;
    contacts.value[contactIndex].lastTime = '刚刚';
  }
  
  newMessage.value = '';
  
  // 模拟滚动到底部
  setTimeout(() => {
    const chatContainer = document.querySelector('.chat-messages');
    if (chatContainer) {
      chatContainer.scrollTop = chatContainer.scrollHeight;
    }
  }, 50);
};

// 格式化时间
const formatDate = (dateStr) => {
  if (!dateStr) return '';
  
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now - date;
  
  // 今天内
  if (diff < 24 * 60 * 60 * 1000 && date.getDate() === now.getDate()) {
    return date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
  }
  
  // 昨天
  if (diff < 48 * 60 * 60 * 1000 && now.getDate() - date.getDate() === 1) {
    return `昨天 ${date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}`;
  }
  
  // 一周内
  if (diff < 7 * 24 * 60 * 60 * 1000) {
    const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
    return days[date.getDay()];
  }
  
  // 更早
  return date.toLocaleDateString();
};

// 计算总未读消息数
const totalUnread = computed(() => {
  return contacts.value.reduce((total, contact) => total + contact.unread, 0);
});

// 初始化
onMounted(() => {
  // 从URL参数获取要显示的标签页
  if (route.query.tab && (route.query.tab === 'notification' || route.query.tab === 'chat')) {
    activeTab.value = route.query.tab;
  }
  
  loadNotifications();
  loadUnreadCount();
  
  // 加载模拟数据
  contacts.value = mockContacts;
  if (contacts.value.length > 0) {
    currentContact.value = contacts.value[0];
    messages.value = mockMessages[currentContact.value.id] || [];
  }
});

// 监听标签切换
watch(activeTab, (newTab) => {
  if (newTab === 'notification') {
    loadNotifications();
    loadUnreadCount();
  }
});

// 自动滚动到底部
const scrollToBottom = () => {
  setTimeout(() => {
    const chatContainer = document.querySelector('.chat-messages');
    if (chatContainer) {
      chatContainer.scrollTop = chatContainer.scrollHeight;
    }
  }, 50);
};

// 监听消息变化，自动滚动
watch(messages, () => {
  scrollToBottom();
}, { deep: true });
</script>

<template>
  <div class="message-container">
    <!-- 标签页切换 -->
    <div class="message-tabs">
      <div 
        class="tab" 
        :class="{ active: activeTab === 'notification' }" 
        @click="activeTab = 'notification'"
      >
        系统通知
        <span v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</span>
      </div>
      <div 
        class="tab" 
        :class="{ active: activeTab === 'chat' }" 
        @click="activeTab = 'chat'"
      >
        私信
        <span v-if="totalUnread > 0" class="unread-badge">{{ totalUnread }}</span>
      </div>
    </div>
    
    <!-- 内容区域 -->
    <div class="message-content">
      <!-- 通知列表 -->
      <div v-if="activeTab === 'notification'" class="notification-panel">
        <div class="notification-header-actions" v-if="notifications.length > 0">
          <div class="left-actions">
            <button class="action-btn" @click="toggleSelectMode">
              <i :class="isSelectMode ? 'fas fa-times' : 'fas fa-check-square'"></i>
              {{ isSelectMode ? '取消选择' : '批量操作' }}
            </button>
            <button 
              v-if="isSelectMode" 
              class="action-btn" 
              @click="toggleSelectAll"
            >
              <i class="fas fa-list"></i>
              {{ selectedNotifications.length === notifications.length ? '取消全选' : '全选' }}
            </button>
            <button 
              v-if="isSelectMode && selectedNotifications.length > 0" 
              class="action-btn delete-btn"
              @click="batchDelete"
            >
              <i class="fas fa-trash"></i>
              批量删除 ({{ selectedNotifications.length }})
            </button>
          </div>
          <div class="right-actions">
            <button class="mark-all-btn" @click="markAllAsRead">
              <i class="fas fa-check-double"></i> 全部标为已读
            </button>
          </div>
        </div>
        
        <div v-if="isLoading" class="loading-state">
          <div class="spinner"></div>
          <div>加载中...</div>
        </div>
        
        <div v-else-if="notifications.length === 0" class="empty-state">
          <el-empty description="暂无通知消息" :image-size="120" />
        </div>
        
        <div v-else class="notifications-list">
          <div 
            v-for="notification in notifications" 
            :key="notification.id" 
            class="notification-item"
            :class="{ 'unread': !notification.isRead, 'selected': selectedNotifications.includes(notification.id) }"
          >
            <!-- 选择复选框 -->
            <div v-if="isSelectMode" class="notification-checkbox">
              <input 
                type="checkbox" 
                :checked="selectedNotifications.includes(notification.id)"
                @change="toggleSelectNotification(notification.id)"
              />
            </div>
            
            <div class="notification-avatar" @click.stop="goToUserHome(notification)" title="进入用户主页">
              <img :src="notification.senderAvatar || avatar" alt="发送者头像">
              <div v-if="!notification.isRead" class="unread-dot"></div>
            </div>
            
            <div 
              class="notification-content" 
              @click="handleNotificationClick(notification)"
              :class="{ 'clickable': notification.objectType === '帖子' || notification.objectType === '回复' }"
            >
              <div class="notification-header">
                <span class="sender-name">{{ notification.senderName }}</span>
                <span class="notification-type">{{ notification.type }}</span>
              </div>
              
              <!-- 显示文章名 -->
              <div 
                v-if="(notification.objectType === '帖子' || notification.objectType === '回复') && notification.objectName" 
                class="article-name"
              >
                <el-icon class="article-icon"><Document /></el-icon>
                <span class="article-title">{{ notification.objectName }}</span>
              </div>
              
              <div class="notification-message" v-if="notification.message">
                {{ notification.message }}
              </div>
              
              <div class="notification-time">
                {{ formatDate(notification.createdAt) }}
                <span v-if="notification.isRead" class="read-label">已读</span>
              </div>
            </div>
            
            <div class="notification-actions" v-if="!isSelectMode">
              <button 
                v-if="!notification.isRead" 
                class="action-btn read-btn"
                @click="markAsRead(notification.id)"
              >
                标为已读
              </button>
              <button 
                class="action-btn delete-btn"
                @click="removeMessage(notification.id)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 私信聊天 -->
      <div v-else-if="activeTab === 'chat'" class="chat-panel">
        <!-- 联系人列表 -->
        <div class="contacts-list">
          <div 
            v-for="contact in contacts" 
            :key="contact.id" 
            class="contact-item"
            :class="{ 'active': currentContact && currentContact.id === contact.id }"
            @click="selectContact(contact)"
          >
            <div class="contact-avatar">
              <img :src="contact.avatar" alt="联系人头像">
              <div v-if="contact.unread > 0" class="unread-dot">{{ contact.unread }}</div>
            </div>
            
            <div class="contact-info">
              <div class="contact-header">
                <span class="contact-name">{{ contact.name }}</span>
                <span class="last-time">{{ contact.lastTime }}</span>
              </div>
              
              <div class="last-message">{{ contact.lastMessage }}</div>
            </div>
          </div>
          <div v-if="contacts.length === 0" class="empty-contacts">
            <el-empty description="暂无联系人" />
          </div>
        </div>
        
        <!-- 聊天窗口 -->
        <div class="chat-window">
          <div v-if="!currentContact" class="no-chat-selected">
            <div class="placeholder-text">选择一个联系人开始聊天</div>
          </div>
          
          <template v-else>
            <!-- 聊天头部 -->
            <div class="chat-header">
              <div class="chat-title">{{ currentContact.name }}</div>
            </div>
            
            <!-- 聊天消息 -->
            <div class="chat-messages">
              <div 
                v-for="message in messages" 
                :key="message.id" 
                class="message-bubble"
                :class="message.type"
              >
                <div class="message-content">{{ message.content }}</div>
                <div class="message-time">{{ message.time }}</div>
              </div>
              
              <div v-if="messages.length === 0" class="no-messages">
                <div class="placeholder-text">暂无消息记录</div>
              </div>
            </div>
            
            <!-- 消息输入框 -->
            <div class="message-input">
              <input 
                type="text" 
                v-model="newMessage" 
                placeholder="输入消息..." 
                @keyup.enter="sendMessage"
              >
              <button class="send-btn" @click="sendMessage">发送</button>
            </div>
          </template>
        </div>
      </div>
    </div>
    </div>
</template>

<style scoped>
.message-container {
  max-width: 1200px;
  margin: 20px auto;
  background-color: var(--el-bg-color-page);
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
}

.message-tabs {
  display: flex;
  background-color: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.tab {
  padding: 15px 25px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  position: relative;
  color: var(--el-text-color-secondary);
  transition: all 0.3s ease;
}

.tab.active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background-color: var(--el-color-primary);
}

.unread-badge {
  background-color: var(--el-color-danger);
  color: white;
  font-size: 12px;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  margin-left: 5px;
}

.message-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 通知面板样式 */
.notification-panel {
  width: 100%;
  overflow-y: auto;
  padding: 0;
}

.loading-state, .empty-state {
  display: flex;
  position: relative;
  justify-content: center;
  align-items: center;
  height: 200px;
  top: calc(50% - 100px);
  color: var(--el-text-color-secondary);
}

.notifications-list {
  padding: 10px;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  padding: 15px;
  border-radius: 8px;
  margin-bottom: 10px;
  /* background-color: var(--el-bg-color); */
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s;
}

.notification-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.1);
}

.notification-item.selected {
  background-color: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
}

.notification-checkbox {
  margin-right: 12px;
  display: flex;
  align-items: center;
}

.notification-checkbox input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

/* .notification-item.unread {
  background-color: var(--el-bg-color);
} */

.notification-avatar {
  position: relative;
  margin-right: 15px;
}

.notification-avatar img {
  width: 45px;
  height: 45px;
  border-radius: 50%;
  object-fit: cover;
}

.unread-dot {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: var(--el-color-danger);
}

.notification-content {
  flex: 1;
  min-width: 0;
  transition: all 0.2s ease;
}

.notification-content.clickable {
  margin-right: 10px;
  cursor: pointer;
}

.notification-content.clickable:hover {
  background-color: rgba(var(--el-color-primary-rgb), 0.05);
  border-radius: 4px;
  padding: 5px;
  margin: -5px;
  transform: translateX(2px);
}

.notification-header {
  display: flex;
  align-items: center;
  margin-bottom: 5px;
}

.sender-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-right: 8px;
}

.notification-type {
  color: var(--el-color-primary);
  font-size: 14px;
  margin-right: 8px;
}

.post-indicator {
  color: var(--el-color-success);
  font-size: 12px;
  opacity: 0.8;
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* .post-indicator::before {
  content: '可点击查看';
  font-size: 10px;
  color: var(--el-text-color-secondary);
} */

.article-name {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0;
  padding: 8px 12px;
  background-color: rgba(var(--el-color-primary-rgb), 0.08);
  border-radius: 6px;
  border-left: 3px solid var(--el-color-primary);
  position: relative;
}

/* .article-name::before {
  content: '文章';
  font-size: 10px;
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  padding: 1px 4px;
  border-radius: 2px;
  margin-right: 4px;
} */

.article-icon {
  color: var(--el-color-primary);
  font-size: 14px;
  flex-shrink: 0;
}

.article-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-color-primary-dark-1);
  line-height: 1.3;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  text-overflow: ellipsis;
}

.notification-message {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 5px;
  line-height: 1.4;
}

.notification-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.read-label {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 6px;
  font-size: 11px;
  border-radius: 10px;
  background: var(--el-color-info-light-9);
  color: var(--el-color-info);
  vertical-align: middle;
}

.notification-avatar { cursor: pointer; }
.notification-avatar:hover img { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }

.notification-actions {
  display: flex;
  height: 100%;
  align-items: center;
  gap: 5px;
}

.notification-actions .action-btn {
  padding: 4px 8px;
  font-size: 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.notification-actions .read-btn {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.notification-actions .read-btn:hover {
  background-color: var(--el-color-primary-light-8);
}

.notification-actions .delete-btn {
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.notification-actions .delete-btn:hover {
  background-color: var(--el-color-danger-light-8);
}

/* 聊天面板样式 */
.chat-panel {
  display: flex;
  width: 100%;
  height: 100%;
}

.contacts-list {
  width: 280px;
  border-right: 1px solid var(--el-border-color-light);
  overflow-y: auto;
  position: relative;
}

.contacts-list::before {
  content: '开发中';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) rotate(-30deg);
  font-size: 48px;
  color: rgba(0, 0, 0, 0.1);
  white-space: nowrap;
  pointer-events: none;
  z-index: 1;
}

.contact-item {
  display: flex;
  padding: 15px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background-color 0.3s;
}

.contact-item:hover {
  background-color: var(--el-bg-color-page);
}

.contact-item.active {
  background-color: rgba(var(--el-color-primary-rgb), 0.1);
}

.contact-avatar {
  position: relative;
  margin-right: 12px;
}

.contact-avatar img {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.contact-avatar .unread-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 12px;
  color: white;
}

.contact-info {
  flex: 1;
  min-width: 0;
}

.contact-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.contact-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.last-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.last-message {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-contacts {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.chat-window {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.no-chat-selected {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--el-text-color-secondary);
  background-color: var(--el-bg-color-page);
}

.chat-header {
  padding: 15px;
  border-bottom: 1px solid var(--el-border-color-light);
  background-color: var(--el-bg-color);
}

.chat-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chat-messages {
  flex: 1;
  padding: 15px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background-color: var(--el-bg-color-page);
}

.message-bubble {
  max-width: 70%;
  padding: 10px 15px;
  border-radius: 10px;
  position: relative;
}

.message-bubble.sent {
  align-self: flex-end;
  background-color: var(--el-color-primary-light-8);
  color: var(--el-color-primary-dark-2);
  border-bottom-right-radius: 0;
}

.message-bubble.received {
  align-self: flex-start;
  background-color: white;
  color: var(--el-text-color-primary);
  border-bottom-left-radius: 0;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.message-content {
  word-break: break-word;
}

.message-time {
  font-size: 11px;
  margin-top: 5px;
  opacity: 0.7;
  text-align: right;
}

.no-messages {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--el-text-color-secondary);
}

.message-input {
  display: flex;
  padding: 15px;
  border-top: 1px solid var(--el-border-color-light);
  background-color: var(--el-bg-color);
}

.message-input input {
  flex: 1;
  padding: 10px 15px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  margin-right: 10px;
  outline: none;
  transition: border-color 0.3s;
}

.message-input input:focus {
  border-color: var(--el-color-primary);
}

.send-btn {
  padding: 0 20px;
  background-color: var(--el-color-primary);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.send-btn:hover {
  background-color: var(--el-color-primary-dark-1);
}

.placeholder-text {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.notification-header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 15px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background-color: var(--el-bg-color);
}

.left-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.right-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.action-btn {
  padding: 6px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: all 0.3s;
  background-color: var(--el-bg-color);
  color: var(--el-text-color-primary);
}

.action-btn:hover {
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.action-btn.delete-btn {
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  border-color: var(--el-color-danger-light-5);
}

.action-btn.delete-btn:hover {
  background-color: var(--el-color-danger-light-8);
  border-color: var(--el-color-danger);
}

.mark-all-btn {
  padding: 5px 12px;
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: all 0.3s;
}

.mark-all-btn:hover {
  background-color: var(--el-color-primary-light-8);
}
</style>
