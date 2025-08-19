<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import PostList from '@/views/posts/PostList.vue';
import LeftSidebar from '@/components/layout/LeftSidebar.vue';
import RightSidebar from '@/components/layout/RightSidebar.vue';
import VideoCarousel from '@/components/home/VideoCarousel.vue';
import QuickPostDialog from '@/components/home/QuickPostDialog.vue';
import PostCreatorInput from '@/components/home/PostCreatorInput.vue';
import avatarImg from '@/assets/img/用户.svg';
import { getTagList } from '@/api/tag';
import { useUserStore } from '@/stores';
import { useRouter } from 'vue-router';

const storiesListRef = ref(null);
const postListRef = ref(null);
const rightSidebarRef = ref(null);
const leftSidebarRef = ref(null);

const userStore = useUserStore();
const userInfo = userStore.user;
const avatar = userInfo.avatar || avatarImg;
const router = useRouter();

const quickPostDialogVisible = ref(false);
const quickPostTitleForDisplay = ref('');

const tagList = ref([]);

const showLeftButton = ref(false);
const showRightButton = ref(false);
const showBackTop = ref(false);

const storyUsers = ref([
  { id: 1, name: 'Post a Story', isAddButton: true, avatar: '' },
  { id: 2, name: 'Judy Nguyen', avatar: avatar },
  { id: 3, name: 'Samuel Bishop', avatar: avatar },
  { id: 4, name: 'Carolyn Ortiz', avatar: avatar },
  { id: 5, name: 'Amanda Reed', avatar: avatar }
]);

const checkScrollButtons = () => {
  if (!storiesListRef.value) return;
  const { scrollLeft, scrollWidth, clientWidth } = storiesListRef.value;
  showLeftButton.value = scrollLeft > 0;
  showRightButton.value = scrollLeft < scrollWidth - clientWidth;
};

const scrollStories = (direction) => {
  if (!storiesListRef.value) return;
  const scrollAmount = 330;
  const newScrollLeft = storiesListRef.value.scrollLeft + (direction === 'right' ? scrollAmount : -scrollAmount);
  storiesListRef.value.scrollTo({
    left: newScrollLeft,
    behavior: 'smooth'
  });
};

const handlePageScroll = () => {
  showBackTop.value = window.pageYOffset > 300;
};

// 全局鼠标滚轮事件处理
let scrollTimeout = null;
const SCROLL_SPEED_MULTIPLIER = 2; // 调整滚动速度，让滚动更自然
const SCROLL_THROTTLE_MS = 16; // 滚动节流时间，约60fps

const handleGlobalWheel = (e) => {
  // 检查是否在右侧边栏内滚动
  const rightSidebar = rightSidebarRef.value;
  const leftSidebar = leftSidebarRef.value;
  
  // 清除之前的定时器
  if (scrollTimeout) {
    clearTimeout(scrollTimeout);
  }
  
  // 如果不在右侧边栏内，同时滚动页面和右侧边栏
  const deltaY = e.deltaY;
  const scrollAmount = deltaY * SCROLL_SPEED_MULTIPLIER;
  
  // 滚动主页面
  const currentScrollTop = window.pageYOffset || document.documentElement.scrollTop;
  const maxPageScroll = document.documentElement.scrollHeight - window.innerHeight;
  const newScrollTop = Math.max(0, Math.min(maxPageScroll, currentScrollTop + scrollAmount));
  
    // 滚动右侧边栏
    if (rightSidebar || leftSidebar) {
      if (rightSidebar) {
        const currentSidebarScroll = rightSidebar.scrollTop;
        const maxSidebarScroll = rightSidebar.scrollHeight - rightSidebar.clientHeight;
        const newSidebarScroll = Math.max(0, Math.min(maxSidebarScroll, currentSidebarScroll + scrollAmount));
        rightSidebar.scrollTo({ 
          top: newSidebarScroll
        });
      }
      if (leftSidebar) {
        const currentLeftScroll = leftSidebar.scrollTop;
        const maxLeftScroll = leftSidebar.scrollHeight - leftSidebar.clientHeight;
        const newLeftScroll = Math.max(0, Math.min(maxLeftScroll, currentLeftScroll + scrollAmount));
        leftSidebar.scrollTo({ 
          top: newLeftScroll
        });
      }
    
      // 执行平滑滚动
      window.scrollTo({ 
        top: newScrollTop
      });
    
    } else {
      window.scrollTo({ 
        top: newScrollTop
      });
    }
  
  // 设置延迟，避免滚动过于频繁
  scrollTimeout = setTimeout(() => {
    scrollTimeout = null;
  }, SCROLL_THROTTLE_MS); // 约60fps
  
  // 阻止默认滚动行为
  e.preventDefault();
};

const scrollToTop = () => {
  window.scrollTo({ top: 0 });
};

const fetchTags = async () => {
  try {
    const res = await getTagList(1);
    if (res.data.code === 200) {
      tagList.value = res.data.data;
    }
  } catch (error) {
    console.error('获取标签列表失败:', error);
  }
};

onMounted(() => {
  checkScrollButtons();
  if (storiesListRef.value) {
    storiesListRef.value.addEventListener('scroll', checkScrollButtons);
  }
  fetchTags();
  window.addEventListener('scroll', handlePageScroll);
  // 添加全局轮播事件监听：当用户滚动鼠标滚轮时调用 handleGlobalWheel。
  // 传入 { passive: false } 是为了允许在事件处理器中调用 e.preventDefault()
  // 来阻止默认滚动行为（否则某些浏览器会忽略 preventDefault）。
  window.addEventListener('wheel', handleGlobalWheel, { passive: false });
});

onBeforeUnmount(() => {
  if (storiesListRef.value) {
    storiesListRef.value.removeEventListener('scroll', checkScrollButtons);
  }
  window.removeEventListener('scroll', handlePageScroll);
  // 移除全局滚轮事件监听
  window.removeEventListener('wheel', handleGlobalWheel);
});

const handleOpenDialog = () => {
  quickPostDialogVisible.value = true;
};

const handleNavigateToCreate = () => {
  router.push('/post/create');
};

const handlePostCreated = async () => {
  await postListRef.value?.refreshList();
  quickPostTitleForDisplay.value = '';
};

</script>

<template>
  <div class="home-container">
  <div class="left-sidebar-wrapper" ref="leftSidebarRef">
      <LeftSidebar />
    </div>
    
    <div class="main-content">
      <VideoCarousel class="video-carousel-wrapper" />

      <PostCreatorInput 
        v-model="quickPostTitleForDisplay" 
        :avatar="userInfo.avatar" 
        @open-dialog="handleOpenDialog"
        @navigate-to-create="handleNavigateToCreate"
      />
      
      <QuickPostDialog 
        v-model="quickPostDialogVisible" 
        :available-tags="tagList"
        @post-created="handlePostCreated"
        v-model:title="quickPostTitleForDisplay" 
      />
      
      <PostList ref="postListRef" />
    </div>

    <div class="right-sidebar-wrapper" ref="rightSidebarRef">
      <RightSidebar />
    </div>

    <transition name="fade">
      <div 
        v-show="showBackTop" 
        class="back-to-top"
        @click="scrollToTop"
      >
        <i class="fas fa-arrow-up"></i>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.el-input {
  --el-input-border-color: transparent !important;
  --el-input-hover-border-color: transparent !important;
  --el-input-focus-border-color: transparent !important;
  --el-input-hover-border: none !important;
  --el-input-focus-border: none !important;
}

.home-container {
  display: flex;
  gap: 20px;
}

.left-sidebar-wrapper {
  width: 280px;
  flex-shrink: 0;
  position: sticky;
  top: 100px; /* 与右侧保持一致 */
  align-self: flex-start;
  max-height: calc(100vh - 120px); /* 与右侧差不多的可视高度 */
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
}

.left-sidebar-wrapper::-webkit-scrollbar {
  display: none;
}

.main-content {
  flex: 1;
  min-width: 0;
  max-width: 1400px !important;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.right-sidebar-wrapper {
  width: 300px;
  flex-shrink: 0;
  /* 如果希望右侧继续固定可保留 sticky；若要统一滚动可注释下面三行 */
  position: sticky;
  top: 100px;
  align-self: flex-start;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
}

.video-carousel-wrapper {
  margin-bottom: 0;
}

.stories-section {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  position: relative;
}

.stories-list {
  display: flex;
  overflow-x: auto;
  scroll-behavior: smooth;
  gap: 15px;
  padding: 10px 0;
  flex-grow: 1;
}

.stories-list::-webkit-scrollbar {
  display: none;
}

.story-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  min-width: 80px;
}

.story-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: 2px solid var(--el-color-primary);
  object-fit: cover;
  margin-bottom: 5px;
}

.story-item span {
  font-size: 12px;
  text-align: center;
}

.scroll-button {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 50%;
  width: 30px;
  height: 30px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
}
.scroll-button.left {
  left: -15px;
}
.scroll-button.right {
  right: -15px;
}

@media (max-width: 1100px) {
  .right-sidebar-wrapper {
    display: none;
  }
}

@media (max-width: 768px) {
  .left-sidebar-wrapper {
    display: none;
  }
  
  .home-container {
    gap: 10px;
  }
}

.back-to-top {
  position: fixed;
  right: 40px;
  bottom: 40px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  transition: all 0.3s;
  z-index: 99;
}

.back-to-top:hover {
  transform: translateY(-2px);
  background-color: var(--el-color-primary-light-3);
}

.back-to-top i {
  font-size: 18px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .back-to-top {
    right: 20px;
    bottom: 20px;
  }
}
</style>
