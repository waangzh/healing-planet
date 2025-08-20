<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { Bell, ArrowUp, ArrowDown } from '@element-plus/icons-vue';
import avatar from '@/assets/img/用户.svg';
import { useUserStore } from '@/stores/modules/user'; 
import { ElMessage } from 'element-plus';
import { getBillboard } from '@/api/billboard';

const router = useRouter();
const userStore = useUserStore();
const userInfo = userStore.user;

// 公告相关
const bulletins = ref([]);
const showAllBulletins = ref(false);

// 获取公告数据
const fetchBillboards = async () => {
  try {
    const response = await getBillboard();
    if (response.data.code === 200) {
      bulletins.value = response.data.data.filter(item => item.show);
    }
  } catch (error) {
    console.error('获取公告失败:', error);
  }
};

// 切换显示所有公告
const toggleBulletins = () => {
  showAllBulletins.value = !showAllBulletins.value;
};

// 显示的公告列表
const displayedBulletins = computed(() => {
  if (showAllBulletins.value || bulletins.value.length <= 2) {
    return bulletins.value;
  }
  return bulletins.value.slice(0, 2);
});

const userProfile = ref({
  alias: userInfo.alias,
  title: userInfo.message || '这个人很懒，什么都没写~',
  profileImage: userInfo.avatar || avatar,
  coverImage: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb',
  bio: userInfo.bio || '这个人很懒，什么都没写~',
  stats: {
    posts: userInfo.postCount || 0,
    followers: userInfo.followerCount || 0,
    following: userInfo.followingCount || 0
  }
});

const menuItems = ref([
  { icon: 'fas fa-home', label: '主页', active: true, route: '/home' },
  { icon: 'fas fa-user-friends', label: '我的联系人', route: '/message?tab=chat', wip: true },
  { icon: 'far fa-newspaper', label: '最新动态', route: '/home', wip: true },
  { icon: 'far fa-calendar-alt', label: '活动', route: '/home', wip: true },
  { icon: 'far fa-bell', label: '通知', route: '/message?tab=notification' },
  { icon: 'fas fa-cog', label: '设置', route: '/setting' }
]);

const handleNavigation = (item) => {
  if (item.wip) {
    ElMessage({
      message: '功能开发中，敬请期待！',
      type: 'info',
      duration: 2000
    });
    return;
  }
  menuItems.value.forEach(i => i.active = false);
  item.active = true;
  try {
    router.push(item.route);
  } catch (error) {
    console.error('路由跳转失败:', error);
    // 如果路由跳转失败，回退到首页
    router.push('/home');
  }
};

// 统计数据点击跳转
const goToUserTopics = () => {
  router.push('/user/topics');
};

const goToUserFollowers = () => {
  router.push('/user/followers');
};

const goToUserFollowing = () => {
  router.push('/user/following');
};

// 组件挂载时获取公告
onMounted(() => {
  fetchBillboards();
});
</script>

<template>
  <aside class="left-sidebar">
    <!-- 公告栏卡片 -->
    <el-card class="bulletin-card" v-if="bulletins.length > 0" shadow="hover">
      <template #header>
        <div class="bulletin-header">
          <el-icon class="bulletin-icon"><bell /></el-icon>
          <span class="bulletin-title">系统公告</span>
        </div>
      </template>
      <div class="bulletin-content">
        <div 
          v-for="bulletin in displayedBulletins" 
          :key="bulletin.id" 
          class="bulletin-item"
        >
          <div class="bulletin-text">{{ bulletin.content }}</div>
          <div class="bulletin-time" v-if="bulletin.modifyTime">
            {{ new Date(bulletin.modifyTime).toLocaleDateString('zh-CN') }}
          </div>
        </div>
        <div 
          v-if="bulletins.length > 2" 
          class="bulletin-toggle"
          @click="toggleBulletins"
        >
          <span>{{ showAllBulletins ? '收起' : '查看更多' }}</span>
          <el-icon class="toggle-icon">
            <arrow-up v-if="showAllBulletins" />
            <arrow-down v-else />
          </el-icon>
        </div>
      </div>
    </el-card>
    
    <div class="profile-group">
      <div class="profile-card">
      <div class="cover-image">
        <img :src="userProfile.coverImage" alt="封面图片">
      </div>
      <div class="profile-image-container">
        <div class="profile-image">
          <img :src="userProfile.profileImage" alt="用户头像">
        </div>
      </div>
      <div class="profile-info">
        <h2 class="user-name">{{ userProfile.alias }}</h2>
        <p class="user-bio">{{ userProfile.bio }}</p>
      </div>
      <div class="user-title">
        <p>{{ userProfile.title }}</p>
      </div>
      <div class="user-stats">
        <div class="stat-item" @click="goToUserTopics">
          <div class="stat-value">{{ userProfile.stats.posts }}</div>
          <div class="stat-label">发布</div>
        </div>
        <div class="stat-item" @click="goToUserFollowers">
          <div class="stat-value">{{ userProfile.stats.followers.toLocaleString() }}</div>
          <div class="stat-label">粉丝</div>
        </div>
        <div class="stat-item" @click="goToUserFollowing">
          <div class="stat-value">{{ userProfile.stats.following }}</div>
          <div class="stat-label">关注</div>
        </div>
      </div>
      </div>
      <nav class="sidebar-menu">
        <ul>
          <li v-for="(item, index) in menuItems" :key="index" :class="{ active: item.active }">
            <a @click.prevent="handleNavigation(item)" href="#" :style="{ cursor: 'pointer' }">
              <i :class="item.icon"></i>
              <span>{{ item.label }}</span>
            </a>
          </li>
        </ul>
      </nav>
      <div class="view-profile-btn">
        <a href="/user">查看个人资料</a>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.left-sidebar {
  /* background-color: var(--el-bg-color-page); */
  border-radius: 10px;
  /* box-shadow: 0 2px 8px var(--el-box-shadow); */
  overflow: visible;
}

/* 组合卡片整体容器 */
.profile-group {
  background-color: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 2px 6px rgba(0,0,0,0.04);
  margin-bottom: 16px;
  transition: box-shadow .3s;
}

.profile-group:hover {
  box-shadow: 0 4px 14px rgba(0,0,0,0.08);
}

.profile-group .profile-card { /* 去掉单独背景与额外圆角 */
  background: transparent;
  border-radius: 0;
  padding-bottom: 10px;
}

.profile-group .sidebar-menu {
  background: transparent;
  border-top: 1px solid var(--el-border-color-lighter);
}

.profile-group .view-profile-btn {
  background: transparent;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 公告栏卡片样式 */
.bulletin-card {
  margin-bottom: 16px;
  border-radius: 12px;
  overflow: hidden;
  
  :deep(.el-card__header) {
    padding: 16px;
    background: linear-gradient(135deg, var(--el-color-warning-light-8), var(--el-color-warning-light-9));
    border-bottom: 1px solid var(--el-color-warning-light-5);
  }
  
  :deep(.el-card__body) {
    padding: 16px;
  }
}

.bulletin-header {
  display: flex;
  align-items: center;
  color: var(--el-color-warning-dark-2);
  font-weight: 600;
  font-size: 14px;
  margin: 0;
}

.bulletin-icon {
  margin-right: 8px;
  font-size: 16px;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.bulletin-title {
  flex: 1;
}

.bulletin-content {
  margin: 0;
}

.bulletin-item {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
  transition: all 0.3s ease;
}

.bulletin-item:last-of-type {
  margin-bottom: 0;
}

.bulletin-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 12px rgba(var(--el-color-warning), 0.15);
}

.bulletin-text {
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  margin-bottom: 6px;
  word-break: break-word;
}

.bulletin-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  text-align: right;
}

.bulletin-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  cursor: pointer;
  font-size: 12px;
  color: var(--el-color-warning-dark-2);
  border-top: 1px solid var(--el-color-warning-light-5);
  margin-top: 10px;
  transition: all 0.3s ease;
  border-radius: 6px;
}

.bulletin-toggle:hover {
  background: var(--el-color-warning-light-9);
}

.toggle-icon {
  margin-left: 4px;
  font-size: 12px;
}

/* 原有的profile样式保持不变 */
.profile-card {
  background-color: var(--el-bg-color-page);
  position: relative;
  padding-bottom: 15px;
}

.cover-image {
  height: 100px;
  overflow: hidden;
}

.cover-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-image-container {
  position: relative;
  display: flex;
  justify-content: center;
  margin-top: -40px;
  margin-bottom: 10px;
}

.profile-image {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 4px solid var(--el-bg-color-overlay);
  overflow: hidden;
  background-color: var(--el-bg-color-overlay);
}

.profile-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-info {
  text-align: center;
  padding: 0 15px;
}

.user-name {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--el-text-color-primary);
}

.user-bio {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin: 5px 0 10px;
}

.user-title {
  padding: 0 15px 15px;
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  border-bottom: 1px solid var(--el-border-color-light);
}

.user-title p {
  margin: 0;
}

.user-stats {
  display: flex;
  padding: 15px;
  text-align: center;
  justify-content: space-between;
}

.stat-item {
  flex: 1;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.stat-item:hover {
  background-color: var(--el-color-primary-light-9);
  transform: translateY(-2px);
}

.stat-value {
  font-weight: 600;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sidebar-menu {
  background-color: var(--el-bg-color-page);
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-light);
}

.sidebar-menu ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.sidebar-menu li {
  margin: 2px 0;
}

.sidebar-menu li a {
  display: flex;
  align-items: center;
  padding: 10px 15px;
  text-decoration: none;
  color: var(--el-text-color-regular);
  font-weight: 500;
  transition: all 0.3s;
}

.sidebar-menu li.active a {
  color: var(--el-color-primary);
}

.sidebar-menu li a:hover {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.sidebar-menu li i {
  width: 24px;
  margin-right: 10px;
}

.view-profile-btn {
  background-color: var(--el-bg-color-page);
  padding: 15px;
  text-align: center;
  border-top: 1px solid var(--el-border-color-light);
}

.view-profile-btn a {
  display: block;
  text-decoration: none;
  color: var(--el-color-primary);
  font-weight: 500;
  padding: 8px 0;
  border-radius: 6px;
  transition: all 0.3s;
}

.view-profile-btn a:hover {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
</style>