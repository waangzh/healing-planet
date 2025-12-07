<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';

const props = defineProps({
  modelValue: String, // For v-model on the input field's display value
  avatar: String // User avatar URL, optional
});

const emit = defineEmits(['update:modelValue', 'open-dialog', 'navigate-to-create']);

const shareOptions = ref([
  { id: 'photo', icon: 'fas fa-image', label: '照片', color: 'rgb(12 188 135)' },
  { id: 'video', icon: 'fas fa-video', label: '视频', color: 'rgb(79 158 248)' },
  { id: 'event', icon: 'fas fa-calendar-plus', label: '活动', color: 'rgb(214 41 62)' },
  { id: 'feeling', icon: 'far fa-laugh', label: '感受/活动', color: 'rgb(247 195 46)' }
]);

const handleFocus = () => {
  emit('open-dialog');
};

const handleOptionClick = () => {
  emit('open-dialog');
};

const goToPostCreatePage = () => {
  emit('navigate-to-create');
};

</script>

<template>
  <div class="post-creator-input-wrapper">
    <el-input
      :model-value="props.modelValue" 
      placeholder="分享你的想法..."
      @focus="handleFocus" 
      class="post-input"
      readonly 
    />
    <div class="post-options">
      <div 
        v-for="option in shareOptions" 
        :key="option.id" 
        class="post-option" 
        @click="handleOptionClick" 
      >
        <i :class="option.icon" :style="{ color: option.color }"></i>
        <span>{{ option.label }}</span>
      </div>
      <div class="post-button">
        <el-button type="primary" @click="goToPostCreatePage">
          去发帖
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.post-creator-input-wrapper {
  background-color: var(--el-bg-color-page);
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 2px 12px var(--el-box-shadow);
  transition: all 0.3s ease;
}

.post-creator-input-wrapper:hover {
  box-shadow: 0 4px 16px var(--el-box-shadow);
}

.post-creator-input-wrapper .post-input {
  margin-bottom: 16px;
}

.post-creator-input-wrapper .post-input :deep(.el-input__wrapper) {
  background-color: transparent !important;
  box-shadow: none !important;
  border: none !important;
  padding: 0 24px;
  transition: background-color 0.3s;
  cursor: pointer;
}

.post-creator-input-wrapper .post-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: none !important;
  /* background-color: transparent; */ /* Keep hover effect on focus */
}

.post-creator-input-wrapper .post-input :deep(.el-input__wrapper:hover) {
  background-color: var(--el-fill-color-light);
}

.post-creator-input-wrapper .post-input :deep(.el-input__inner) {
  height: 52px;
  color: var(--comment-text-primary);
  font-size: 15px;
  background: transparent;
  cursor: pointer;
}

.post-creator-input-wrapper .post-input :deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
}

.post-creator-input-wrapper .post-options {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}

.post-creator-input-wrapper .post-options .post-option {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  gap: 8px;
  color: var(--el-text-color-regular);
}

.post-creator-input-wrapper .post-options .post-option:hover {
  background-color: var(--el-color-primary-light-9);
  transform: translateY(-1px);
}

.post-creator-input-wrapper .post-options .post-option i {
  font-size: 20px;
}

.post-creator-input-wrapper .post-options .post-option span {
  font-size: 14px;
  white-space: nowrap;
}

.post-creator-input-wrapper .post-options .post-button {
  margin-left: auto;
  flex-shrink: 0;
}

.post-creator-input-wrapper .post-options .post-button .el-button {
  border-radius: 24px;
  padding: 12px 28px;
  font-size: 15px;
  font-weight: 600;
  height: 44px;
  transition: all 0.3s ease;
}

.post-creator-input-wrapper .post-options .post-button .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px var(--el-color-primary-light-5);
}

/* Responsive adjustments if needed */
@media (max-width: 768px) {
  .post-creator-input-wrapper {
    padding: 16px;
  }
  
  .post-creator-input-wrapper .post-options {
    gap: 8px;
  }
  
  .post-creator-input-wrapper .post-options .post-option {
    padding: 8px;
  }
  
  .post-creator-input-wrapper .post-options .post-option span {
    display: none;
  }
  
  .post-creator-input-wrapper .post-options .post-option i {
    font-size: 18px;
  }
  
  .post-creator-input-wrapper .post-options .post-button .el-button {
    padding: 10px 20px;
    height: 40px;
    font-size: 14px;
  }
}
</style> 