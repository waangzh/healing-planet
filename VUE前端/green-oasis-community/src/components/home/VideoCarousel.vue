<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue';
import coverImg from '@/assets/img/轮播图封面.jpg';

// 新增：显式导入视频资源（确保路径与项目中实际文件一致）
import video1 from '@/assets/display/1.mp4';
import video2 from '@/assets/display/2.mp4';
import video3 from '@/assets/display/3.mp4';
import video4 from '@/assets/display/4.mp4';
import video5 from '@/assets/display/5.mp4';

// 添加自动轮播相关代码
const videoList = ref([
  {
    id: 1,
    title: '项目背景',
    url: video1,
    cover: coverImg,
    description: '在快节奏的生活中，静静绽放'
  },
  {
    id: 2,
    title: '设备展示',
    url: video2,
    cover: coverImg,
    description: '让您的绿植管理更简单'
  },
  {
    id: 3,
    title: '情感交流',
    url: video3,
    cover: coverImg,
    description: '一次近距离的交谈'
  },
  {
    id: 4,
    title: '小程序展示',
    url: video4,
    cover: coverImg,
    description: '多端同步，随时随地管理您的绿植'
  },
  {
    id: 5,
    title: '我们的开发历程',
    url: video5,
    cover: coverImg,
    description: '从零开始，一步步走来'
  },
]);

// 当前激活的视频索引
const activeVideoIndex = ref(0);

// 播放/暂停状态
const isPlaying = ref(false);
const currentVideo = ref(null);
const autoPlayTimer = ref(null);
const autoPlayInterval = 8000; // 自动轮播间隔，单位毫秒

// 添加视频加载状态跟踪
const videoLoading = ref(false);

// 切换视频
const changeVideo = (index) => {
  // 如果有正在播放的视频，先暂停
  if (currentVideo.value) {
    currentVideo.value.pause();
  }
  
  // 清除自动播放计时器
  if (autoPlayTimer.value) {
    clearTimeout(autoPlayTimer.value);
    autoPlayTimer.value = null;
  }
  
  activeVideoIndex.value = index;
  isPlaying.value = false;
  
  // 延迟加载新视频
  nextTick(() => {
    const videoElement = document.getElementById(`video-carousel-${index}`);
    if (videoElement) {
      currentVideo.value = videoElement;
      
      // 如果设备支持自动播放，则自动播放视频
      if (window.matchMedia('(prefers-reduced-motion: no-preference)').matches) {
        playVideo();
      }
    }
    
    // 设置自动轮播计时器
    startAutoPlayTimer();
  });
};

// 自动播放下一个视频
const autoPlayNext = () => {
  const nextIndex = (activeVideoIndex.value + 1) % videoList.value.length;
  changeVideo(nextIndex);
};

// 启动自动播放计时器
const startAutoPlayTimer = () => {
  if (autoPlayTimer.value) {
    clearTimeout(autoPlayTimer.value);
  }
  
  autoPlayTimer.value = setTimeout(() => {
    if (!isPlaying.value) { // 只有当前视频不在播放时才自动切换
      autoPlayNext();
    } else {
      // 如果视频在播放，重新设置计时器
      startAutoPlayTimer();
    }
  }, autoPlayInterval);
};

// 播放视频
const playVideo = () => {
  if (!currentVideo.value) return;
  
  // 捕获视频播放错误
  const playPromise = currentVideo.value.play();
  
  if (playPromise !== undefined) {
    playPromise.then(() => {
      isPlaying.value = true;
    }).catch((error) => {
      console.error('自动播放失败:', error);
      isPlaying.value = false;
    });
  }
};

// 播放/暂停当前视频
const togglePlay = () => {
  if (!currentVideo.value) return;
  
  if (isPlaying.value) {
    currentVideo.value.pause();
    isPlaying.value = false;
  } else {
    playVideo();
  }
  
  // 重置自动播放计时器
  startAutoPlayTimer();
};

// 视频状态监听
const handleVideoEnded = () => {
  isPlaying.value = false;
  autoPlayNext();
};

// 暂停和恢复自动播放（当鼠标进入/离开轮播区域时）
const pauseAutoPlay = () => {
  if (autoPlayTimer.value) {
    clearTimeout(autoPlayTimer.value);
    autoPlayTimer.value = null;
  }
};

const resumeAutoPlay = () => {
  if (!autoPlayTimer.value) {
    startAutoPlayTimer();
  }
};

// 处理视频加载状态
const handleVideoLoadStart = () => {
  videoLoading.value = true;
};

const handleVideoCanPlay = () => {
  videoLoading.value = false;
};


// 初始化视频播放器
onMounted(() => {
  nextTick(() => {
    const videoElement = document.getElementById(`video-carousel-${activeVideoIndex.value}`);
    if (videoElement) {
      currentVideo.value = videoElement;
       // 如果设备支持自动播放，则自动播放视频
      if (window.matchMedia('(prefers-reduced-motion: no-preference)').matches && videoList.value.length > 0) {
        playVideo();
      }
    }
    
    // 启动自动轮播
    startAutoPlayTimer();
  });
});

// 在组件销毁前清除计时器
onBeforeUnmount(() => {
  if (autoPlayTimer.value) {
    clearTimeout(autoPlayTimer.value);
    autoPlayTimer.value = null;
  }
  
  // 如果有视频正在播放，暂停它
  if (currentVideo.value) {
    currentVideo.value.pause();
    currentVideo.value = null;
  }
});

</script>

<template>
  <div class="video-carousel" @mouseenter="pauseAutoPlay" @mouseleave="resumeAutoPlay">
    <div class="video-slides-container">
      <div class="video-slides" :style="{ transform: `translateX(-${activeVideoIndex * 100}%)` }">
        <div 
          v-for="(video, index) in videoList" 
          :key="video.id"
          class="video-slide"
          :class="{ active: index === activeVideoIndex }"
        >
          <div class="video-wrapper">
            <video
              :id="`video-carousel-${index}`"
              class="video-player"
              :src="video.url"
              preload="metadata"
              autoplay
              loop
              :poster="video.cover"
              @ended="handleVideoEnded"
              @play="isPlaying = true"
              @pause="isPlaying = false"
              @loadstart="handleVideoLoadStart"
              @canplay="handleVideoCanPlay"
              @waiting="videoLoading = true"
              @playing="videoLoading = false"
              muted
              playsinline
            ></video>
            <div class="video-overlay" @click="togglePlay">
              <div v-if="videoLoading && activeVideoIndex === index" class="loading-indicator">
                <i class="el-icon-loading"></i>
                <span>加载中...</span>
              </div>
              <div v-else-if="!isPlaying || activeVideoIndex !== index" class="play-btn">
                <i class="fas fa-play"></i>
              </div>
            </div>
          </div>
          <div class="video-info">
            <h2 class="video-title">{{ video.title }}</h2>
            <p class="video-description">{{ video.description }}</p>
          </div>
        </div>
      </div>
    </div>
    
    <div class="carousel-controls">
      <button class="control-prev" @click="changeVideo((activeVideoIndex - 1 + videoList.length) % videoList.length)">
        <i class="fas fa-chevron-left"></i>
      </button>
      <div class="carousel-indicators">
        <span 
          v-for="(video, index) in videoList" 
          :key="index" 
          class="indicator"
          :class="{ active: index === activeVideoIndex }"
          @click="changeVideo(index)"
        ></span>
      </div>
      <button class="control-next" @click="autoPlayNext">
        <i class="fas fa-chevron-right"></i>
      </button>
    </div>
  </div>
</template>

<style scoped>
.video-carousel {
  position: relative;
  width: 100%;
  border-radius: 16px;
  overflow: hidden;
  /* margin-bottom: 20px; */ /* Removed to be controlled by parent */
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  background-color: var(--el-bg-color-page);
}

.video-slides-container {
  width: 100%;
  overflow: hidden;
}

.video-slides {
  display: flex;
  transition: transform 0.5s ease;
  width: 100%;
}

.video-slide {
  flex: 0 0 100%;
  position: relative;
}

.video-slide.active {
  z-index: 2;
}

.video-wrapper {
  position: relative;
  width: 100%;
  height: 0;
  padding-bottom: 56.25%; /* 16:9 的宽高比 */
  overflow: hidden;
  background-color: #ebe8e8;
}

.video-player {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  height: 100%;
  object-fit: cover; /* Changed from fill for better aspect ratio handling */
}

.video-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 0.3s;
  cursor: pointer;
}

.video-overlay:hover {
  opacity: 1;
}

.video-info {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  padding: 20px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.4));
  color: white;
  z-index: 2;
}

.video-title {
  font-size: 24px;
  margin-bottom: 8px;
  font-weight: 600;
}

.video-description {
  font-size: 16px;
  opacity: 0.9;
}

.carousel-controls {
  position: absolute;
  bottom: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 3;
}

.control-prev, .control-next {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(5px);
  border: none;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
}

.control-prev:hover, .control-next:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.1);
}

.control-prev i, .control-next i {
  font-size: 16px;
}

.carousel-indicators {
  display: flex;
  gap: 8px;
}

.indicator {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  cursor: pointer;
  transition: all 0.3s;
}

.indicator.active {
  background: white;
  transform: scale(1.2);
}

.indicator:hover {
  background: rgba(255, 255, 255, 0.6);
}

/* 适配移动设备 */
@media (max-width: 768px) {
  .video-carousel .video-info {
    padding: 15px;
  }
  .video-carousel .video-info .video-title {
    font-size: 18px;
  }
  .video-carousel .video-info .video-description {
    font-size: 14px;
  }
  .video-carousel .carousel-controls {
    bottom: 15px;
    right: 15px;
  }
  .video-carousel .carousel-controls .control-prev, .video-carousel .carousel-controls .control-next {
    width: 32px;
    height: 32px;
  }
  .video-carousel .carousel-controls .carousel-indicators .indicator {
    width: 8px;
    height: 8px;
  }
  .video-carousel .video-wrapper .video-overlay .play-btn {
    width: 50px;
    height: 50px;
  }
  .video-carousel .video-wrapper .video-overlay .play-btn i {
    font-size: 18px;
  }
}

.loading-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  gap: 10px;
}

.loading-indicator i {
  font-size: 24px;
  animation: rotate 1.2s linear infinite;
}

.loading-indicator span {
  font-size: 14px;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.play-btn {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.play-btn i {
  color: white;
  font-size: 24px;
  margin-left: 4px; /* 微调播放图标位置 */
}

.play-btn:hover {
  transform: scale(1.1);
  background: rgba(255, 255, 255, 0.3);
}
</style>