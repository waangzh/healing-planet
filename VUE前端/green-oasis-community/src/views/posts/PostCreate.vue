<script setup>
import { ref, onMounted, shallowRef, onBeforeUnmount, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { uploadFileService } from '@/api/common';
import { getTagList } from '@/api/tag';
import { addPost, getPostDetail, updatePost } from '@/api/post';
import { getGenerateLog } from '@/api/recommend';
import '@wangeditor/editor/dist/css/style.css';
import { Editor, Toolbar } from '@wangeditor/editor-for-vue';
import { VueCropper } from 'vue-cropper';
import 'vue-cropper/dist/index.css';
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router';
import { useUserStore } from '@/stores';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const isEdit = ref(false);
const postId = ref(null);

// 编辑器实例，必须用 shallowRef
const editorRef = shallowRef();

// 内容 HTML
const valueHtml = ref('');

// 编辑器配置
const editorConfig = {
  placeholder: '\u3000请输入内容...',
  scroll: false,
  autoFocus: false,
  MENU_CONF: {
    // 配置图片上传
    uploadImage: {
      // 选择文件时的类型限制
      allowedFileTypes: ['image/*'],
      
      // 最多可上传几个文件（保留此配置）
      maxNumberOfFiles: 10,
      
      // 使用自定义上传函数
      customUpload: async (file, insertFn) => {
        try {
          // 显示上传状态或进度提示
          ElMessage.info('正在上传图片...');
          
          // 文件大小验证
          const isLt5M = file.size / 1024 / 1024 < 5;
          if (!isLt5M) {
            ElMessage.error('图片大小不能超过 5MB!');
            return;
          }
          
          // 使用 uploadFileService 上传
          const res = await uploadFileService(file);
          
          // 处理上传结果
          if (res.data && res.data.code === 200 && res.data.data) {
            // 获取图片URL并插入到编辑器
            insertFn(res.data.data);
            ElMessage.success('图片上传成功');
          } else {
            ElMessage.error(res.data?.message || '图片上传失败');
          }
        } catch (error) {
          console.error('上传图片出错:', error);
          ElMessage.error('图片上传错误: ' + (error.message || '未知错误'));
        }
      }
    }
  },
  // 启用 Markdown
  markdown: {
    enabled: true
  }
};

// 工具栏配置
const toolbarConfig = {
  // 其他配置……
  excludeKeys: ['fullScreen'],
};


// 裁剪相关
const cropperRef = ref(null);
const cropperVisible = ref(false);
const cropperImg = ref('');
const cropperOption = {
  img: cropperImg.value,
  outputSize: 1,
  outputType: 'png',
  info: true,
  canScale: true,
  autoCrop: true,
  autoCropWidth: 300,
  autoCropHeight: 225,
  fixed: true,
  fixedNumber: [4, 3]
};

// 组件销毁时，也及时销毁编辑器
const handleDestroy = () => {
  const editor = editorRef.value;
  if (editor == null) return;
  editor.destroy();
};

const title = ref('');
const coverImg = ref('');
const coverPreview = ref('');
const selectedTags = ref([]);
const tagList = ref([]);
const tagDialogVisible = ref(false);

// 添加标签搜索关键词
const tagSearchKeyword = ref('');

// 过滤后的标签列表
const filteredTagList = computed(() => {
  const keyword = tagSearchKeyword.value.trim().toLowerCase();
  if (!keyword) return tagList.value;
  
  return tagList.value.filter(tag => 
    tag.name.toLowerCase().includes(keyword)
  );
});

// 获取标签列表
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

// 处理图片选择
const handleImageSelect = (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  // 验证文件类型和大小
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

  // 读取文件并显示裁剪器
  const reader = new FileReader();
  reader.onload = (e) => {
    cropperImg.value = e.target.result;
    cropperVisible.value = true;
  };
  reader.readAsDataURL(file);
  
  // 清空input的value,以便可以重复选择同一文件
  event.target.value = '';
};

// 处理裁剪完成
const handleCropFinish = () => {
  if (!cropperRef.value) return;
  
  loading.value = true;
  cropperRef.value.getCropData(async (data) => {
    try {
      // 将base64转换为文件
      const file = dataURLtoFile(data, 'cover.png');
      const res = await uploadFileService(file);
      
      if (res.data.code === 200) {
        coverImg.value = res.data.data;
        coverPreview.value = res.data.data;
        ElMessage.success('封面上传成功');
        cropperVisible.value = false;
      }
    } catch (error) {
      console.error('封面上传失败:', error);
      ElMessage.error('封面上传失败');
    } finally {
      loading.value = false;
    }
  });
};

// base64转文件
const dataURLtoFile = (dataurl, filename) => {
  const arr = dataurl.split(',');
  const mime = arr[0].match(/:(.*?);/)[1];
  const bstr = atob(arr[1]);
  let n = bstr.length;
  const u8arr = new Uint8Array(n);
  while (n--) {
    u8arr[n] = bstr.charCodeAt(n);
  }
  return new File([u8arr], filename, { type: mime });
};

// 移除封面
const removeCover = () => {
  coverImg.value = '';
  coverPreview.value = '';
};

// 重置表单
const resetForm = () => {
  if (!title.value && !valueHtml.value && !coverImg.value && selectedTags.value.length === 0) return;
  
  ElMessageBox.confirm('确定要清空所有内容吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    title.value = '';
    valueHtml.value = '';
    coverImg.value = '';
    coverPreview.value = '';
    selectedTags.value = [];
    // 草稿箱也重置
    localStorage.removeItem('postDraft');
    ElMessage.success('已重置');
  }).catch(() => {});
};

const isPublished = ref(false);

const fetchPostDetail = async () => {
  if (!postId.value) return;
  
  try {
    loading.value = true;
    const res = await getPostDetail(postId.value);
    if (res.data.code === 200) {
      const post = res.data.data;
      title.value = post.topic.title;
      valueHtml.value = post.topic.content;
      coverImg.value = post.topic.coverImg;
      coverPreview.value = post.topic.coverImg;
      selectedTags.value = post.tags;
      
      // 设置编辑器内容
      if (editorRef.value) {
        editorRef.value.setHtml(post.topic.content);
      }
    }
  } catch (error) {
    console.error('获取文章详情失败:', error);
    ElMessage.error('获取文章详情失败');
  } finally {
    loading.value = false;
  }
};

const handlePublish = async () => {
  if (!validateForm()) return;
  
  const postData = {
    title: title.value,
    content: valueHtml.value,
    coverImg: coverImg.value,
    tags: selectedTags.value.map(tag => tag.id)
  };
  
  try {
    loading.value = true;
    let res;
    
    if (isEdit.value) {
      res = await updatePost({id: postId.value, ...postData});
    } else {
      res = await addPost(postData);
    }
    
    if (res.data.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功！' : '发布成功！');
      isPublished.value = true;
      
      // 发布成功后立即更新用户帖子数
      if (!isEdit.value) {
        userStore.incrementPostCount();
        // 延迟刷新完整用户信息（确保后端数据已更新）
        setTimeout(() => {
          userStore.refreshUserInfo();
        }, 1000);
      }
      
      router.push('/');
    } else {
      ElMessage.error(res.data.message || (isEdit.value ? '更新失败' : '发布失败'));
    }
  } catch (error) {
    console.error(isEdit.value ? '更新失败:' : '发布失败:', error);
    ElMessage.error(isEdit.value ? '更新失败，请重试' : '发布失败，请重试');
  } finally {
    loading.value = false;
  }
};

const validateForm = () => {
  if (!title.value.trim()) {
    ElMessage.warning('请输入标题');
    return false;
  }
  if (!valueHtml.value.trim()) {
    ElMessage.warning('请输入内容');
    return false;
  }
  return true;
};

// 检查是否有未保存的内容
const hasUnsavedContent = () => {
  const hasTitle = title.value && typeof title.value === 'string' && title.value.trim() !== '';
  const hasContent = valueHtml.value && typeof valueHtml.value === 'string' && valueHtml.value.trim() !== '';
  const hasCover = !!coverImg.value;
  const hasTags = Array.isArray(selectedTags.value) && selectedTags.value.length > 0;
  
  return hasTitle || hasContent || hasCover || hasTags;
};

// 保存到草稿箱
const saveToDraft = () => {
  const draft = {
    title: title.value,
    content: valueHtml.value,
    coverImg: coverImg.value,
    tags: selectedTags.value,
    lastModified: new Date().toISOString()
  };
  localStorage.setItem('postDraft', JSON.stringify(draft));
  ElMessage.success('已保存到草稿箱');
};

// 从草稿箱加载
const loadFromDraft = () => {
  const draft = localStorage.getItem('postDraft');
  if (draft) {
    try {
      const parsedDraft = JSON.parse(draft);
      title.value = parsedDraft.title;
      valueHtml.value = parsedDraft.content;
      coverImg.value = parsedDraft.coverImg;
      coverPreview.value = parsedDraft.coverImg;
      selectedTags.value = parsedDraft.tags;
    } catch (error) {
      console.error('加载草稿失败:', error);
    }
  }
};

// 清除草稿
const clearDraft = () => {
  localStorage.removeItem('postDraft');
};

// 路由离开前的提示
onBeforeRouteLeave((to, from, next) => {
  if (isPublished.value || (!title.value && !valueHtml.value)) {
    next();
    return;
  }

  ElMessageBox.confirm(
    '是否保存到草稿箱？',
    '提示',
    {
      confirmButtonText: '保存',
      cancelButtonText: '不保存',
      type: 'warning'
    }
  )
    .then(() => {
      saveToDraft();
      next();
    })
    .catch(() => {
      next();
    });
});

// 页面刷新前的提示
// window.addEventListener('beforeunload', (e) => {
//   if (hasUnsavedContent()) {
//     e.preventDefault();
//     e.returnValue = '';
//   }
// });

// 添加一键成文相关状态
const generateLoading = ref(false);
const generateDialogVisible = ref(false);
const userPrompt = ref('');

// 显示一键成文对话框
const showGenerateDialog = () => {
  generateDialogVisible.value = true;
  userPrompt.value = '';
};

// 处理一键成文
const handleGenerateContent = async () => {
  try {
    generateDialogVisible.value = false;
    generateLoading.value = true;
    
    const res = await getGenerateLog(userPrompt.value);
    if (res.data && res.data.code === 200 && res.data.data) {
      // 将生成的内容设置到编辑器和valueHtml
      valueHtml.value = res.data.data;
      if (editorRef.value) {
        editorRef.value.setHtml(res.data.data);
      }
      ElMessage.success('文章生成成功');
      
      // 保存到草稿箱，确保页面刷新后不丢失
      saveToDraft();
    } else {
      ElMessage.error(res.data.message || '生成失败');
    }
  } catch (error) {
    console.error('生成文章失败:', error);
    ElMessage.error('生成失败，请重试');
  } finally {
    generateLoading.value = false;
  }
};

onMounted(() => {
  postId.value = route.query.id;
  isEdit.value = !!postId.value;
  
  if (isEdit.value) {
    fetchPostDetail();
  }
  fetchTags();
  loadFromDraft();
});

onBeforeUnmount(() => {
  handleDestroy();
  window.removeEventListener('beforeunload', () => {});
});
</script>

<template>
  <div class="post-create">
    <div class="post-header">
      <h2>发布主题</h2>
    </div>
    
    <div class="post-form">
      <!-- 标题输入 -->
      <div class="form-item">
        <el-input
          v-model="title"
          placeholder="请输入标题"
          maxlength="50"
          show-word-limit
          class="title-input"
        />
      </div>
      
      <!-- 富文本编辑器 -->
      <div class="form-item editor-container">
        <div class="editor-toolbar-wrapper">
          <Toolbar
            style="border-bottom: 1px solid var(--el-border-color-light)"
            :editor="editorRef"
            :defaultConfig="toolbarConfig"
            mode="default"
            class="editor-toolbar"
          />
          
          <!-- 一键成文按钮 -->
          <el-tooltip content="AI一键生成文章内容" placement="top">
            <el-button 
              type="primary" 
              class="generate-btn" 
              @click="showGenerateDialog"
              :loading="generateLoading"
            >
              <i class="fas fa-magic"></i> 一键成文
            </el-button>
          </el-tooltip>
        </div>
        
        <!-- 编辑器加载蒙层 -->
        <div v-if="generateLoading" class="editor-loading-mask">
          <div class="loading-spinner"></div>
          <div class="loading-text">AI 正在创作中，请稍候...</div>
        </div>
        
        <Editor
          style="height: 500px"
          v-model="valueHtml"
          :defaultConfig="editorConfig"
          mode="default"
          @onCreated="editorRef = $event"
          class="editor-content"
        />
      </div>
      
      <!-- 封面上传 -->
      <div class="form-item cover-upload">
        <div class="section-title">封面图片</div>
        <div v-if="!coverPreview" class="upload-area">
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
          <img :src="coverPreview" alt="封面预览">
          <div class="cover-actions">
            <el-button type="danger" size="small" @click="removeCover">
              移除封面
            </el-button>
          </div>
        </div>
      </div>
      
      <!-- 标签选择 -->
      <div class="form-item">
        <div class="section-title">添加标签</div>
        <div class="tags-container">
          <div class="tags-wrapper">
            <div class="selected-tags">
              <el-tag
                v-for="tag in selectedTags"
                :key="tag.id"
                closable
                @close="selectedTags = selectedTags.filter(t => t.id !== tag.id)"
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
                  :disabled="selectedTags.length >= 3"
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
                  <el-checkbox-group v-model="selectedTags" :max="3">
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
      
      <!-- 底部操作按钮 -->
      <div class="form-actions">
        <el-button @click="resetForm">重置</el-button>
        <el-button @click="saveToDraft" :disabled="!hasUnsavedContent()">
          保存到草稿箱
        </el-button>
        <el-button type="primary" @click="handlePublish" :loading="loading">
          {{ isEdit ? '更新' : '发布' }}
        </el-button>
      </div>
    </div>
    
    <!-- 标签选择对话框 -->
    <el-dialog
      v-model="tagDialogVisible"
      title="选择标签"
      width="500px"
    >
      <div class="tag-list">
        <el-checkbox-group v-model="selectedTags" :max="3">
          <el-checkbox
            v-for="tag in tagList"
            :key="tag.id"
            :label="tag"
          >
            {{ tag.name }}
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="tagDialogVisible = false">
          确定
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 图片裁剪对话框 -->
    <el-dialog
      v-model="cropperVisible"
      title="裁剪封面"
      width="700px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      destroy-on-close
    >
      <div class="cropper-container">
        <vue-cropper
          ref="cropperRef"
          :img="cropperImg"
          :info="true"
          :outputSize="1"
          :outputType="'png'"
          :autoCrop="true"
          :fixed="true"
          :fixedNumber="[4, 3]"
          :autoCropWidth="300"
          :autoCropHeight="225"
          :centerBox="true"
          :high="true"
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cropperVisible = false">取消</el-button>
          <el-button 
            type="primary" 
            @click="handleCropFinish"
            :loading="loading"
          >
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 一键成文对话框 -->
    <el-dialog
      v-model="generateDialogVisible"
      title="AI 一键成文"
      width="500px"
    >
      <div class="generate-form">
        <p class="generate-tip">输入一些描述信息，AI 将为您生成文章内容</p>
        <el-input
          v-model="userPrompt"
          type="textarea"
          :rows="4"
          placeholder="请简单描述您想要生成的文章主题或内容，例如：'关于多肉植物的养护指南'"
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="generateDialogVisible = false">取消</el-button>
          <el-button 
            type="primary" 
            @click="handleGenerateContent"
          >
            开始生成
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.post-create {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  background-color: var(--el-fill-color-blank);
  border-radius: 12px;
  box-shadow: 0 2px 12px var(--el-box-shadow);
}

.post-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  
  h2 {
    font-size: 20px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    margin: 0;
  }
}

.post-form {
  .form-item {
    margin-bottom: 24px;
  }
  
  .section-title {
    font-size: 16px;
    font-weight: 500;
    color: var(--el-text-color-primary);
    margin-bottom: 12px;
  }
}

.title-input {
  :deep(.el-input__wrapper) {
    font-size: 18px;
    padding: 12px;
    border-radius: 8px;
    background-color: var(--el-fill-color-blank);
  }
}

.editor-container {
  position: relative;
  margin-bottom: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
}

.editor-toolbar-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
}

.editor-toolbar {
  flex: 1;
}

:deep(.toolbar-container) {
  position: sticky;
  top: 0;
  z-index: 2;
  background-color: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

:deep(.editor-content-view) {
  padding: 0 16px;
  min-height: 500px;
  max-height: 800px;
  overflow-y: auto;
}

:deep(.w-e-text-container) {
  min-height: 500px !important;
  max-height: 800px !important;
  overflow-y: auto !important;
}

.generate-btn {
  position: absolute;
  right: 10px;
  top: calc(50% + 15px);
  transform: translateY(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 5px;
  border-radius: 4px;
  padding: 8px 12px;
  font-size: 14px;
  z-index: 0;
}

.editor-loading-mask {
  position: absolute;
  top: 40px; /* 考虑工具栏高度 */
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 20;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--el-border-color-lighter);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

.loading-text {
  font-size: 16px;
  color: var(--el-text-color-primary);
  margin-top: 10px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.generate-form {
  padding: 0 20px;
}

.generate-tip {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
  line-height: 1.5;
}

.cover-upload {
  .upload-area {
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
    background-color: var(--el-fill-color-blank);
    
    &:hover {
      border-color: var(--el-color-primary);
      color: var(--el-color-primary);
    }
    
    .file-input {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      opacity: 0;
      cursor: pointer;
    }
    
    i {
      font-size: 24px;
      margin-bottom: 8px;
    }
    
    span {
      font-size: 14px;
      margin-bottom: 4px;
    }
    
    .upload-tip {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
  
  .cover-preview {
    position: relative;
    width: 300px;
    height: 225px;
    border-radius: 8px;
    overflow: hidden;
    background-color: var(--el-fill-color-blank);
    
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    
    .cover-actions {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      opacity: 0;
      transition: opacity 0.3s;
    }
    
    &:hover {
      &::after {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.5);
      }
      
      .cover-actions {
        opacity: 1;
        z-index: 1;
      }
    }
  }
}

.tags-container {
  .tags-wrapper {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }
  
  .selected-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    min-height: 32px;
    padding: 4px 0;
  }
  
  .add-tag-btn {
    border-style: dashed;
  }
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-light);
  
  .el-button {
    min-width: 100px;
  }
}

.tag-list {
  max-height: 300px;
  overflow-y: auto;
  padding: 16px;
  
  .el-checkbox-group {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 12px;
  }
}

.cropper-container {
  height: 500px;
  width: 100%;
  
  :deep(.vue-cropper) {
    height: 100%;
    width: 100%;
    background-color: var(--el-bg-color);
  }
}

@media (max-width: 768px) {
  .post-create {
    padding: 15px;
  }
}

:deep(.tag-popover) {
  padding: 0;
  
  .tag-selector {
    .search-box {
      padding: 12px;
      border-bottom: 1px solid var(--el-border-color-light);
      
      .el-input {
        font-size: 14px;
      }
    }
    
    .tag-list {
      max-height: 300px;
      overflow-y: auto;
      padding: 12px;
      
      .el-checkbox-group {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      
      .tag-item {
        margin-right: 0;
        padding: 8px 12px;
        border-radius: 4px;
        transition: all 0.3s;
        
        &:hover {
          background-color: var(--el-fill-color-light);
        }
      }
      
      .no-result {
        padding: 20px 0;
        text-align: center;
        color: var(--el-text-color-secondary);
        font-size: 14px;
      }
    }
  }
}
</style>


