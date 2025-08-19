<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { addPost } from '@/api/post';
import { uploadFileService } from '@/api/common';
import { useUserStore } from '@/stores';

const props = defineProps({
  modelValue: Boolean, // For v-model: visibility of the dialog
  availableTags: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['update:modelValue', 'post-created']);

const userStore = useUserStore();

const quickPostDialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
});

const loading = ref(false);
const quickPostForm = reactive({
  title: '',
  content: '',
  tags: [], // Stores selected tag objects {id, name}
  coverImg: '',
  coverPreview: ''
});

const tagSearchKeyword = ref('');

const filteredTagList = computed(() => {
  const keyword = tagSearchKeyword.value.trim().toLowerCase();
  if (!keyword) return props.availableTags;
  return props.availableTags.filter(tag => 
    tag.name.toLowerCase().includes(keyword)
  );
});

// Watch for dialog opening to reset form if needed (optional, depends on desired UX)
watch(() => props.modelValue, (newValue) => {
  if (newValue) {
    // Reset parts of the form if you want it fresh each time
    // quickPostForm.title = ''; // Example: Keep title as it's also bound to HomeView input
    // quickPostForm.content = '';
    // quickPostForm.tags = [];
    // quickPostForm.coverImg = '';
    // quickPostForm.coverPreview = '';
    // tagSearchKeyword.value = '';
  } else {
    // Optionally reset title if it should clear when dialog closes from outside
    // quickPostForm.title = '';
  }
});

const handleDialogClose = () => {
  emit('update:modelValue', false);
  // quickPostForm.title = ''; // Moved title reset to watcher or parent
  document.activeElement?.blur(); 
};

const handleTagSelect = (selectedTagObjects) => {
  // el-checkbox-group v-model directly gives the array of selected objects
  if (selectedTagObjects.length > 3) {
    ElMessage.warning('最多只能添加3个标签');
    // Manually trim if the component doesn't enforce the max strictly for the model
    quickPostForm.tags = selectedTagObjects.slice(0, 3);
  } else {
    quickPostForm.tags = selectedTagObjects;
  }
};

const removeSelectedTag = (tagToRemove) => {
  quickPostForm.tags = quickPostForm.tags.filter(tag => tag.id !== tagToRemove.id);
};


const handleImageSelect = (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  const isImage = /^image\//.test(file.type);
  const isLt2M = file.size / 1024 / 1024 < 2;

  if (!isImage) {
    ElMessage.error('只能上传图片文件!');
    return;
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 2MB!');
    return;
  }

  const reader = new FileReader();
  reader.onload = (e) => {
    quickPostForm.coverPreview = e.target.result;
  };
  reader.readAsDataURL(file);
  
  uploadFile(file);
};

const uploadFile = async (file) => {
  try {
    loading.value = true;
    const res = await uploadFileService(file);
    if (res.data.code === 200) {
      quickPostForm.coverImg = res.data.data;
      ElMessage.success('封面上传成功');
    }
  } catch (error) {
    console.error('封面上传失败:', error);
    ElMessage.error('封面上传失败');
  } finally {
    loading.value = false;
  }
};

const removeCover = () => {
  quickPostForm.coverImg = '';
  quickPostForm.coverPreview = '';
};

const handleQuickPost = async () => {
  if (!quickPostForm.title.trim()) {
    ElMessage.warning('请输入标题');
    return;
  }
  if (!quickPostForm.content.trim()) {
    ElMessage.warning('请输入内容');
    return;
  }

  try {
    loading.value = true;
    const postData = {
      title: quickPostForm.title.trim(),
      content: quickPostForm.content.trim(),
      coverImg: quickPostForm.coverImg,
      tags: quickPostForm.tags.map(tag => tag.id) // Send only tag IDs
    };
    
    const res = await addPost(postData);

    if (res.data.code === 200) {
      ElMessage.success('发布成功');
      emit('update:modelValue', false); // Close dialog
      emit('post-created'); // Notify parent
      
      // 发布成功后立即更新用户帖子数
      userStore.incrementPostCount();
      // 延迟刷新完整用户信息（确保后端数据已更新）
      setTimeout(() => {
        userStore.refreshUserInfo();
      }, 1000);
      
      // Reset form
      Object.assign(quickPostForm, {
        title: '',
        content: '',
        tags: [],
        coverImg: '',
        coverPreview: ''
      });
      tagSearchKeyword.value = '';
    } else {
      ElMessage.error(res.data.message || '发布失败');
    }
  } catch (error) {
    console.error('发布失败:', error);
    ElMessage.error('发布失败');
  } finally {
    loading.value = false;
  }
};

</script>

<template>
  <el-dialog
    v-model="quickPostDialogVisible" 
    title="发布帖子"
    width="600px"
    :close-on-click-modal="false"
    @close="handleDialogClose" 
  >
    <div class="quick-post-form">
      <!-- 标题输入 -->
      <el-input
        v-model="quickPostForm.title"
        placeholder="请输入标题"
        maxlength="50"
        show-word-limit
        class="title-input"
      />
      
      <!-- 内容输入 -->
      <el-input
        v-model="quickPostForm.content"
        type="textarea"
        placeholder="请输入内容"
        :rows="6"
        maxlength="1000"
        show-word-limit
        class="content-input"
      />
      
      <!-- 标签选择 -->
      <div class="form-item">
        <div class="section-title">添加标签</div>
        <div class="tags-container">
          <div class="tags-wrapper">
            <div class="selected-tags">
              <el-tag
                v-for="tag in quickPostForm.tags"
                :key="tag.id"
                closable
                @close="removeSelectedTag(tag)"
              >
                {{ tag.name }}
              </el-tag>
            </div>
            <el-popover
              placement="bottom-start"
              :width="300"
              trigger="click"
              popper-class="tag-popover" 
            >
              <template #reference>
                <el-button 
                  class="add-tag-btn"
                  :disabled="quickPostForm.tags.length >= 3"
                >
                  选择标签
                </el-button>
              </template>
              
              <div class="tag-selector">
                <div class="search-box">
                  <el-input
                    v-model="tagSearchKeyword"
                    placeholder="搜索标签"
                    clearable
                    prefix-icon="el-icon-search"
                  />
                </div>
                <div class="tag-list">
                  <el-checkbox-group v-model="quickPostForm.tags" :max="3" @change="handleTagSelect">
                    <template v-if="filteredTagList.length > 0">
                      <el-checkbox
                        v-for="tag in filteredTagList"
                        :key="tag.id"
                        :label="tag" 
                        class="tag-item"
                      >
                        {{ tag.name }}
                      </el-checkbox>
                    </template>
                    <div v-else class="no-result">
                      没有找到相关标签
                    </div>
                  </el-checkbox-group>
                </div>
              </div>
            </el-popover>
          </div>
        </div>
      </div>
      
      <!-- 封面上传 -->
      <div class="cover-upload">
        <div v-if="!quickPostForm.coverPreview" class="upload-area">
          <input
            type="file"
            accept="image/*"
            @change="handleImageSelect"
            class="file-input"
          >
          <i class="el-icon-plus"></i>
          <span>上传封面</span>
          <div class="upload-tip">建议尺寸 300x225</div>
        </div>
        <div v-else class="cover-preview">
          <img :src="quickPostForm.coverPreview" alt="封面预览">
          <div class="cover-actions">
            <el-button type="danger" size="small" @click="removeCover">
              移除封面
            </el-button>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleDialogClose">取消</el-button>
        <el-button type="primary" @click="handleQuickPost" :loading="loading">
          发布
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.quick-post-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.quick-post-form .title-input :deep(.el-input__inner) {
  font-size: 18px;
  padding: 12px;
  border-radius: 8px;
}

.quick-post-form .content-input :deep(.el-textarea__inner) {
  padding: 12px;
  border-radius: 8px;
  font-size: 15px;
  line-height: 1.6;
}

.quick-post-form .form-item {
   margin-bottom: 24px;
}

.quick-post-form .form-item .section-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}

.quick-post-form .tags-container .tags-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.quick-post-form .tags-container .selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px; /* Ensure consistent height */
  padding: 4px 0;
}

.quick-post-form .tags-container .add-tag-btn {
  border-style: dashed;
}

/* Styles for tag popover - can be global if used elsewhere */
.tag-popover .tag-selector {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tag-popover .search-box {
  padding: 0 5px;
}

.tag-popover .tag-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 0 5px;
}

.tag-popover .tag-item {
  display: block; /* Make each checkbox take full width */
  margin-bottom: 5px;
}

.tag-popover .no-result {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: 10px 0;
}


.quick-post-form .cover-upload .upload-area {
  position: relative;
  width: 300px;
  height: 225px;
  border: 2px dashed var(--el-border-color);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
}

.quick-post-form .cover-upload .upload-area:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.quick-post-form .cover-upload .upload-area .file-input {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.quick-post-form .cover-upload .upload-area i {
  font-size: 24px;
  margin-bottom: 8px;
}

.quick-post-form .cover-upload .upload-area span {
  font-size: 14px;
  margin-bottom: 4px;
}

.quick-post-form .cover-upload .upload-area .upload-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.quick-post-form .cover-upload .cover-preview {
  position: relative;
  width: 300px;
  height: 225px;
  border-radius: 8px;
  overflow: hidden;
}

.quick-post-form .cover-upload .cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.quick-post-form .cover-upload .cover-preview .cover-actions {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  opacity: 0;
  transition: opacity 0.3s;
}

.quick-post-form .cover-upload .cover-preview:hover::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
}

.quick-post-form .cover-upload .cover-preview:hover .cover-actions {
  opacity: 1;
  z-index: 1;
}

.dialog-footer {
  text-align: right;
}
</style> 