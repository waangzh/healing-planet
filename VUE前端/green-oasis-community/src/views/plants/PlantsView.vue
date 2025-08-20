<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import { uploadFileService } from '@/api/common';
// import  identifyImg  from '@/assets/img/识别.svg'
import { getPlants, getPlantById, identifyPlant } from '@/api/plants';
import { ElMessage } from 'element-plus';
import { Search, Picture, Loading } from '@element-plus/icons-vue';
import MarkdownIt from 'markdown-it';

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
});

const plantList = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(12);
const loading = ref(false);
const finished = ref(false);
const selectedPlant = ref(null);
const selectedPlantId = ref(null);
const hoveredPlant = ref(null);
const searchKeyword = ref('');
const identifyDialogVisible = ref(false);
const identifyLoading = ref(false);
const identifyResult = ref('');
const identifyImageUrl = ref('');

const fetchPlants = async (reset = false) => {
  if ((loading.value || finished.value) && !reset) return;
  if (reset) {
    pageNo.value = 1;
    plantList.value = [];
    finished.value = false;
  }
  
  loading.value = true;
  try {
    const params = { 
      pageNo: pageNo.value, 
      size: pageSize.value
    };
    
    if (searchKeyword.value.trim()) {
      params.key = searchKeyword.value.trim();
    }
    
    const res = await getPlants(params);
    if (res.data.code === 200) {
      const records = res.data.data.records;
      if (pageNo.value === 1) {
        plantList.value = records;
      } else {
        plantList.value = [...plantList.value, ...records];
      }
      total.value = res.data.data.total;
      finished.value = plantList.value.length >= total.value;
    }
  } finally {
    loading.value = false;
  }
};

watch(searchKeyword, (newValue, oldValue) => {
  if (newValue !== oldValue) {
    const debounceTimer = setTimeout(() => {
      fetchPlants(true);
    }, 300);
    
    return () => clearTimeout(debounceTimer);
  }
});

const handleScroll = () => {
  const scrollTop = document.documentElement.scrollTop || document.body.scrollTop;
  const windowHeight = window.innerHeight;
  const documentHeight = document.documentElement.scrollHeight;
  if (scrollTop + windowHeight >= documentHeight - 100 && !loading.value && !finished.value) {
    pageNo.value += 1;
    fetchPlants();
  }
};

const selectPlant = async (plant, index) => {
  if (selectedPlantId.value === plant.id) {
    selectedPlant.value = null;
    selectedPlantId.value = null;
  } else {
    selectedPlantId.value = plant.id;
    const res = await getPlantById(plant.id);
    if (res.data.code === 200) {
      selectedPlant.value = { ...res.data.data, index };
    }
  }
};

const handleMouseEnter = async (plant, index) => {
  if (selectedPlantId.value !== null) {
    return;
  }
  try {
    const res = await getPlantById(plant.id);
    if (res.data.code === 200) {
      hoveredPlant.value = { ...res.data.data, index };
    }
  } catch (error) {
    console.error('获取植物详情失败', error);
  }
};

const handleMouseLeave = () => {
  hoveredPlant.value = null;
};

const detailToShow = (index) => {
  if (selectedPlant.value && selectedPlant.value.index === index) return selectedPlant.value;
  if (hoveredPlant.value && hoveredPlant.value.index === index) return hoveredPlant.value;
  return null;
};

// 处理图片上传识别
const handleImageUpload = (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  // 验证文件类型和大小
  const isImage = /^image\//.test(file.type);
  const isLt5M = file.size / 1024 / 1024 < 5;

  if (!isImage) {
    ElMessage.error('只能上传图片文件!');
    return;
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB!');
    return;
  }

  identifyPlantImage(file);
  // 清空input值，确保可以重复上传同一文件
  event.target.value = '';
};

// 识别植物图片
const identifyPlantImage = async (file) => {
  try {
    identifyLoading.value = true;
    
    // 1. 上传图片
    const uploadRes = await uploadFileService(file);
    if (uploadRes.data.code !== 200) {
      ElMessage.error('图片上传失败');
      return;
    }
    
    const imageUrl = uploadRes.data.data;
    identifyImageUrl.value = imageUrl;
    
    // 2. 调用识别接口
    const identifyRes = await identifyPlant(imageUrl);
    if (identifyRes.data.code === 200) {
      // 使用markdown-it解析markdown文本为HTML
      identifyResult.value = md.render(identifyRes.data.data);
      identifyDialogVisible.value = true;
      ElMessage.success('植物识别成功');
    } else {
      ElMessage.error(identifyRes.data.message || '识别失败');
    }
  } catch (error) {
    console.error('植物识别失败:', error);
    ElMessage.error('识别失败，请重试');
  } finally {
    identifyLoading.value = false;
  }
};

onMounted(() => {
  fetchPlants();
  window.addEventListener('scroll', handleScroll);
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll);
});
</script>

<template>
  <div class="plants-view-container">
    <div class="plants-list">
      <div class="search-container">
        <div class="search-identify-wrapper">
          <el-input
            v-model="searchKeyword"
            placeholder="请输入植物名称关键词搜索"
            class="search-input"
            clearable
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          
          <div class="identify-btn-wrapper">
            <el-tooltip content="上传图片识别植物" placement="top">
              <div class="identify-btn" :class="{ 'is-loading': identifyLoading }">
                <input 
                  type="file" 
                  accept="image/*" 
                  class="identify-input"
                  @change="handleImageUpload" 
                  :disabled="identifyLoading"
                />
                <el-icon v-if="identifyLoading" class="is-loading"><Loading /></el-icon>
                <el-icon v-else><Picture /></el-icon>
                <span>识别植物</span>
              </div>
            </el-tooltip>
          </div>
        </div>
      </div>
      
      <!-- 识别结果对话框 -->
      <el-dialog
        v-model="identifyDialogVisible"
        title="植物识别结果"
        width="600px"
        height="calc(100vh - 200px)"
      >
        <div class="identify-result-container">
          <div v-if="identifyImageUrl" class="identify-image-wrapper">
            <img :src="identifyImageUrl" alt="识别图片" class="identify-image" />
          </div>
          
          <div class="identify-result-content markdown-body" v-html="identifyResult"></div>
        </div>
      </el-dialog>
      
      <el-row :gutter="24">
        <el-col
          v-for="(plant, index) in plantList"
          :key="plant.id"
          :span="8"
          class="plant-col"
          @mouseenter="handleMouseEnter(plant, index)"
          @mouseleave="handleMouseLeave"
        >
          <div
            class="plant-card"
            :class="{ active: plant.id === selectedPlantId }"
            @click.stop="selectPlant(plant, index)"
          >
            <img :src="plant.coverImg" class="plant-img" />
            <div class="plant-name">{{ plant.commonName }}</div>
            <div class="plant-scientific">{{ plant.scientificName }}</div>
          </div>
          <transition name="fade">
            <div
              v-if="detailToShow(index)"
              class="plant-detail-card"
              :class="{ 'right-position': index % 3 !== 2, 'left-position': index % 3 === 2 }"
            >
              <img :src="detailToShow(index).coverImg" class="detail-img" />
              <div class="detail-title">{{ detailToShow(index).commonName }}</div>
              <div class="detail-scientific">{{ detailToShow(index).scientificName }}</div>
              <div class="detail-desc">{{ detailToShow(index).detailAdvice }}</div>
              <div class="detail-attrs">
                <div class="attr-item">
                  <span>养护难度：</span>
                  <el-rate :model-value="detailToShow(index).difficulty" disabled show-score :max="5" />
                </div>
                <div class="attr-item">
                  <span>光照需求：</span>
                  <span>{{ detailToShow(index).lightRequirements }}</span>
                </div>
                <div class="attr-item">
                  <span>加水频率：</span>
                  <span>{{ detailToShow(index).wateringFrequency }}</span>
                </div>
                <div class="attr-item">
                  <span>适宜温度：</span>
                  <span>{{ detailToShow(index).temperaturePreference }}</span>
                </div>
                <div class="attr-item">
                  <span>适宜湿度：</span>
                  <span>{{ detailToShow(index).humidityPreference }}</span>
                </div>
                <div class="attr-item">
                  <span>施肥建议：</span>
                  <span>{{ detailToShow(index).fertilizingTips }}</span>
                </div>
              </div>
            </div>
          </transition>
        </el-col>
      </el-row>
      
      <el-empty v-if="plantList.length === 0 && !loading" description="暂无数据"></el-empty>
      
      <div v-if="loading" class="loading-more">
        <el-icon class="loading"><loading /></el-icon> 加载中...
      </div>
      <div v-if="finished && plantList.length > 0" class="no-more">没有更多植物了</div>
    </div>
  </div>
</template>
<style scoped>
.plants-view-container {
  padding: 32px 0;
  display: flex;
  justify-content: center;
  background: linear-gradient(to bottom, #f0f9eb, #ffffff);
  min-height: 100vh;
}
.plants-list {
  width: 1100px;
}
.search-container {
  margin-bottom: 30px;
  max-width: 800px;
  margin-left: auto;
  margin-right: auto;
}
.search-identify-wrapper {
  display: flex;
  align-items: center;
  gap: 16px;
}
.search-input {
  flex: 1;
}
.identify-btn-wrapper {
  position: relative;
}
.identify-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  background: var(--el-color-primary);
  color: white;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
  font-size: 14px;
  white-space: nowrap;
}
.identify-btn:hover {
  background: var(--el-color-primary-light-3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64,184,132,0.2);
}
.identify-btn.is-loading {
  background: var(--el-color-primary-light-5);
  cursor: not-allowed;
}
.identify-input {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}
.identify-result-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.identify-image-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.identify-image {
  max-width: 150px;
  max-height: 150px;
  border-radius: 16px;
  object-fit: contain;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.identify-result-content {
  max-height: 400px;
  overflow-y: auto;
  padding: 16px;
  border-radius: 8px;
  background-color: #f8f9fa;
}
.plant-col {
  position: relative;
  margin-bottom: 70px;
}
.plant-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(64,184,132,0.08);
  padding: 24px 12px 18px 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
  height: 100%;
  position: relative;
  overflow: hidden;
}
.plant-card.active, .plant-card:hover {
  border: 2px solid var(--el-color-primary);
  box-shadow: 0 4px 16px rgba(64,184,132,0.18);
  background: var(--el-color-primary-light-9);
  transform: translateY(-3px);
}
.plant-img {
  width: 120px;
  height: 120px;
  object-fit: cover;
  margin-bottom: 12px;
  border-radius: 12px;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s;
}
.plant-card:hover .plant-img {
  transform: scale(1.05);
}
.plant-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
  margin-bottom: 4px;
}
.plant-scientific {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-style: italic;
  margin-bottom: 8px;
}
.plant-detail-card {
  pointer-events: none;
  position: absolute;
  top: -50%;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 24px rgba(64,184,132,0.3);
  padding: 32px 28px 24px 28px;
  min-width: 320px;
  max-width: 350px;
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 100;
}
.plant-detail-card.right-position {
  left: calc(100% + 20px);
}
.plant-detail-card.left-position {
  right: calc(100% + 20px);
}
.detail-img {
  width: 150px;
  height: 150px;
  object-fit: cover;
  margin-bottom: 18px;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  border: 4px solid #f0f9eb;
}
.detail-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary);
  margin-bottom: 4px;
  position: relative;
}
.detail-title::after {
  content: '';
  display: block;
  width: 40px;
  height: 3px;
  background: var(--el-color-primary);
  margin: 8px auto;
  border-radius: 3px;
}
.detail-scientific {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
  font-style: italic;
}
.detail-desc {
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-bottom: 18px;
  text-align: center;
  line-height: 1.6;
  background-color: #f0f9eb;
  padding: 12px;
  border-radius: 8px;
}
.detail-attrs {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #f9f9f9;
  padding: 15px;
  border-radius: 12px;
}
.attr-item {
  font-size: 14px;
  color: var(--el-text-color-regular);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px dashed #eaeaea;
}
.attr-item:last-child {
  border-bottom: none;
}
.attr-item span:first-child {
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
  min-width: 80px;
}
.loading-more, .no-more {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: 20px 0;
  margin-top: 12px;
  font-size: 14px;
}
.loading-more .el-icon {
  margin-right: 5px;
  animation: rotating 2s linear infinite;
}
@keyframes rotating {
  0% {
    transform: rotate(0);
  }
  100% {
    transform: rotate(360deg);
  }
}
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
@media (max-width: 768px) {
  .plants-list {
    width: 100%;
    padding: 0 20px;
  }
  
  .search-identify-wrapper {
    flex-direction: column;
  }
  
  .identify-btn-wrapper {
    width: 100%;
  }
  
  .identify-btn {
    width: 100%;
    justify-content: center;
  }
  
  .plant-detail-card {
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    max-width: 90%;
    width: 90%;
  }
  
  .plant-detail-card.right-position,
  .plant-detail-card.left-position {
    left: 50%;
    right: auto;
  }
}
</style>

<style>
/* 应用于 Markdown 内容的样式 */
.markdown-body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif;
  line-height: 1.6;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  color: var(--el-color-primary-dark-2);
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
}

.markdown-body p {
  margin-bottom: 16px;
}

.markdown-body ul {
  margin-bottom: 16px;
}

.markdown-body a {
  color: var(--el-color-primary);
}

.markdown-body blockquote {
  border-left: 4px solid var(--el-color-primary-light-5);
  padding-left: 16px;
  color: var(--el-text-color-secondary);
}

.markdown-body code {
  background-color: #f0f9eb;
  padding: 2px 4px;
  border-radius: 4px;
}

.markdown-body strong {
  color: var(--el-color-primary-dark-2);
}
</style>
