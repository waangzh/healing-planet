<template>
  <div class="post-management">
    <!-- 搜索与筛选区域 -->
    <div class="search-section">
      <el-form :model="searchForm" inline class="inline-search-form" v-auto-submit-form="handleSearch">
        <el-form-item label="文章标题" class="search-item">
          <el-input 
            v-model="searchForm.title" 
            placeholder="请输入文章标题关键字" 
            clearable 
          />
        </el-form-item>
        <el-form-item label="发布日期" class="search-item">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="标签" class="search-item">
          <el-select
            v-model="searchForm.tagIds"
            multiple
            placeholder="请选择标签"
            clearable
            collapse-tags
            collapse-tags-tooltip
          >
            <el-option v-for="tag in allTags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="作者" class="search-item">
          <el-select
            v-model="searchForm.authorId"
            placeholder="请选择作者"
            clearable
            filterable
          >
            <el-option 
              v-for="user in allUsers" 
              :key="user.id" 
              :label="user.alias || user.username" 
              :value="user.id"
            >
              <div class="user-option">
                <el-avatar :src="user.avatar" size="small" />
                <span class="user-name">{{ user.alias || user.username }}</span>
                <span class="post-count">({{ user.postCount }}篇)</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态" class="search-item">
          <el-select
            v-model="searchForm.status"
            placeholder="请选择状态"
            clearable
          >
            <el-option label="已发布" :value="1" />
            <el-option label="审核中" :value="0" />
            <el-option label="未审核" :value="-1" />
            <el-option label="未通过" :value="-2" />
          </el-select>
        </el-form-item>
        <el-form-item class="search-item">
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
          <el-button @click="resetSearch">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 操作区域 -->
    <div class="action-section">
      <el-button type="danger" :disabled="selectedPosts.length === 0" @click="handleBulkDelete">
        <el-icon><Delete /></el-icon> 批量删除
      </el-button>
    </div>

    <!-- 文章列表 -->
    <el-table
      v-loading="loading"
      :data="posts"
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column label="文章标题" min-width="250">
        <template #default="{ row }">
          <div class="post-title-cell">
            <div class="post-info">
              <span class="title-text">{{ row.title }}</span>
              <div class="post-flags">
                <el-tag v-if="row.top" type="danger" size="small">置顶</el-tag>
                <el-tag v-if="row.essence" type="warning" size="small">精华</el-tag>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="作者" width="180">
        <template #default="{ row }">
          <div class="author-cell">
            <el-avatar :src="row.avatar" size="small" />
            <span class="author-name">{{ row.alias || row.username }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="标签" width="200">
        <template #default="{ row }">
          <el-tag
            v-for="tag in row.tags"
            :key="tag.id"
            size="small"
            style="margin-right: 5px; margin-bottom: 5px"
          >
            {{ tag.name }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据" width="150">
        <template #default="{ row }">
          <div class="stats-cell">
            <span><el-icon><View /></el-icon> {{ row.view }}</span>
            <span><el-icon><Pointer /></el-icon> {{ row.likes }}</span>
            <span><el-icon><ChatDotRound /></el-icon> {{ row.comments }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" prop="createTime" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="getStatusTagType(row.status)"
            size="small"
          >
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="viewPostDetail(row.id)">查看</el-button>
          <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="pagination.pageNo"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 文章详情抽屉 -->
    <el-drawer v-model="detailDrawer.visible" title="文章详情" direction="rtl" size="50%">
      <div v-if="detailDrawer.loading" v-loading="detailDrawer.loading" class="drawer-loading"></div>
      <div v-if="!detailDrawer.loading && detailDrawer.data" class="post-detail-content">
        <h2 class="detail-title">{{ detailDrawer.data.topic.title }}</h2>
        
        <!-- 封面图片 -->
        <div v-if="detailDrawer.data.topic.coverImg" class="cover-section">
          <el-image 
            :src="detailDrawer.data.topic.coverImg" 
            fit="cover" 
            class="detail-cover"
            preview-teleported
            :preview-src-list="[detailDrawer.data.topic.coverImg]"
          />
        </div>
        
        <div class="meta-info">
          <div class="author-info">
            <el-avatar :src="detailDrawer.data.user.avatar" />
            <span>{{ detailDrawer.data.user.alias || detailDrawer.data.user.username }}</span>
          </div>
          <span class="create-time">发布于 {{ formatDate(detailDrawer.data.topic.createTime) }}</span>
        </div>
        <div class="tags-section">
          <el-tag v-for="tag in detailDrawer.data.tags" :key="tag.id">{{ tag.name }}</el-tag>
        </div>
        <el-divider />
        <div class="post-content" v-html="detailDrawer.data.topic.content"></div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getPostList, getPostDetail, deletePost } from '@/api/post';
import { getTagsList } from '@/api/tag';
import { getUserList } from '@/api/user';
import { Search, Refresh, Delete, View, Pointer, ChatDotRound } from '@element-plus/icons-vue';

// --- 响应式数据 ---
const loading = ref(false);
const posts = ref([]);
const selectedPosts = ref([]);
const allTags = ref([]);
const allUsers = ref([]);

const searchForm = reactive({
  title: '',
  dateRange: [],
  tagIds: [],
  authorId: '',
  status: undefined,
});

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
});

const detailDrawer = reactive({
  visible: false,
  loading: false,
  data: null,
});

// --- API请求 ---
const fetchPosts = async () => {
  loading.value = true;
  try {
    // 处理日期格式转换
    let startTime, endTime;
    if (searchForm.dateRange && searchForm.dateRange.length === 2) {
      // 将日期转换为API要求的格式
      const startDate = new Date(searchForm.dateRange[0]);
      const endDate = new Date(searchForm.dateRange[1]);
      
      // 设置开始时间为当天的00:00:00
      startDate.setHours(0, 0, 0, 0);
      startTime = startDate.toISOString().replace(/\.\d{3}Z$/, '.000+00:00');
      
      // 设置结束时间为当天的23:59:59
      endDate.setHours(23, 59, 59, 999);
      endTime = endDate.toISOString().replace(/\.\d{3}Z$/, '.999+00:00');
    }
    
    const params = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      title: searchForm.title || undefined,
      authorId: searchForm.authorId || undefined,
      startTime: startTime || undefined,
      endTime: endTime || undefined,
      tagIds: searchForm.tagIds.length > 0 ? searchForm.tagIds : undefined,
      status: searchForm.status !== undefined ? searchForm.status : undefined,
    };
    const res = await getPostList(params);
    if (res.data.code === 200) {
      posts.value = res.data.data.records;
      pagination.total = res.data.data.total;
    } else {
      ElMessage.error(res.data.message || '获取文章列表失败');
    }
  } catch (error) {
    console.error("获取文章列表失败:", error);
    ElMessage.error('获取文章列表失败');
  } finally {
    loading.value = false;
  }
};

const fetchAllTags = async () => {
  try {
    const res = await getTagsList();
    if (res.data.code === 200) {
      allTags.value = res.data.data;
    }
  } catch (error) {
    console.error("获取标签列表失败:", error);
  }
};

const fetchAllUsers = async () => {
  try {
    const res = await getUserList({ pageNo: 1, pageSize: 999 });
    if (res.data.code === 200) {
      allUsers.value = res.data.data.records;
    }
  } catch (error) {
    console.error("获取用户列表失败:", error);
  }
};

const viewPostDetail = async (id) => {
  detailDrawer.visible = true;
  detailDrawer.loading = true;
  try {
    const res = await getPostDetail(id);
    if (res.data.code === 200) {
      detailDrawer.data = res.data.data;
    } else {
      ElMessage.error(res.data.message || '获取文章详情失败');
    }
  } catch (error) {
    console.error("获取文章详情失败:", error);
    ElMessage.error('获取文章详情失败');
  } finally {
    detailDrawer.loading = false;
  }
};

// --- 事件处理 ---
const handleSearch = () => {
  pagination.pageNo = 1;
  fetchPosts();
};

const resetSearch = () => {
  searchForm.title = '';
  searchForm.dateRange = [];
  searchForm.tagIds = [];
  searchForm.authorId = '';
  searchForm.status = undefined;
  handleSearch();
};

const handleSelectionChange = (selection) => {
  selectedPosts.value = selection;
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除文章《${row.title}》吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    await executeDelete([row.id]);
  }).catch(() => {});
};

const handleBulkDelete = () => {
  if (selectedPosts.value.length === 0) {
    ElMessage.warning('请至少选择一篇文章');
    return;
  }
  ElMessageBox.confirm(`确定要删除选中的 ${selectedPosts.value.length} 篇文章吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    const ids = selectedPosts.value.map(post => post.id);
    await executeDelete(ids);
  }).catch(() => {});
};

const executeDelete = async (ids) => {
  loading.value = true;
  try {
    const res = await deletePost(ids);
    if (res.data.code === 200) {
      ElMessage.success('删除成功');
      // 如果当前页数据删完，返回上一页
      if (posts.value.length === ids.length && pagination.pageNo > 1) {
        pagination.pageNo -= 1;
      }
      fetchPosts();
    } else {
      ElMessage.error(res.data.message || '删除失败');
    }
  } catch (error) {
    console.error("删除文章失败:", error);
    ElMessage.error('删除失败');
  } finally {
    loading.value = false;
  }
};

const handleSizeChange = (size) => {
  pagination.pageSize = size;
  fetchPosts();
};

const handlePageChange = (page) => {
  pagination.pageNo = page;
  fetchPosts();
};

// 移除已选的标签（用于下方 tag 行）
const removeTag = (id) => {
  const idx = searchForm.tagIds.indexOf(id);
  if (idx > -1) searchForm.tagIds.splice(idx, 1);
};

// 根据 id 获取标签名称
const getTagName = (id) => {
  const found = allTags.value.find(t => t.id === id);
  return found ? found.name : id;
};

// --- 辅助函数 ---
const formatDate = (dateString) => {
  if (!dateString) return 'N/A';
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN');
};

// 获取状态文本
const getStatusText = (status) => {
  const statusMap = {
    1: '已发布',
    0: '审核中',
    '-1': '未审核',
    '-2': '未通过'
  };
  return statusMap[status] || '未知';
};

// 获取状态标签类型
const getStatusTagType = (status) => {
  const typeMap = {
    1: 'success',
    0: 'warning',
    '-1': 'info',
    '-2': 'danger'
  };
  return typeMap[status] || 'info';
};

// --- 生命周期钩子 ---
onMounted(() => {
  fetchPosts();
  fetchAllTags();
  fetchAllUsers();
});
</script>

<style lang="scss" scoped>
.post-management {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial;
  color: #2c3e50;
}

/* 搜索区 */
.search-section {
  padding: 16px;
  background: #fff;
  border-radius: 10px;
  margin-bottom: 18px;
  box-shadow: 0 1px 3px rgba(16,24,40,0.04);
  
  .inline-search-form {
    width: 100%;
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 12px;
    
    .search-item {
      margin-bottom: 12px !important;
      margin-right: 0 !important;
      flex-shrink: 0;
      
      .el-form-item__label {
        white-space: nowrap;
        font-size: 14px;
        color: #374151;
        min-width: 60px;
      }
      
      .el-form-item__content {
        margin-left: 8px !important;
      }
      
      // 为不同的输入控件设置自适应宽度
      .el-input {
        min-width: 120px;
        max-width: 200px;
        width: auto;
      }
      
      .el-select {
        min-width: 120px;
        max-width: 220px;
        width: auto;
      }
      
      .el-date-editor {
        min-width: 220px;
        max-width: 280px;
        width: auto;
      }
    }
  }
  
  /* 响应式处理：在较小屏幕下换行显示 */
  @media (max-width: 1400px) {
    .inline-search-form {
      .search-item {
        .el-input,
        .el-select {
          min-width: 120px;
          max-width: 180px;
        }
        
        .el-date-editor {
          min-width: 220px;
          max-width: 260px;
        }
      }
    }
  }
  
  @media (max-width: 1200px) {
    .inline-search-form {
      gap: 10px;
      
      .search-item {
        margin-bottom: 10px;
        
        .el-input,
        .el-select {
          min-width: 110px;
          max-width: 160px;
        }
        
        .el-date-editor {
          min-width: 200px;
          max-width: 240px;
        }
      }
    }
  }
  
  @media (max-width: 900px) {
    .inline-search-form {
      display: block;
      
      .search-item {
        display: block;
        width: 100% !important;
        margin-bottom: 16px;
        
        .el-form-item__content {
          margin-left: 0 !important;
        }
        
        .el-input,
        .el-select,
        .el-date-editor {
          width: 100% !important;
          min-width: unset !important;
          max-width: unset !important;
        }
      }
    }
  }
}

/* 用户选项样式 */
.user-option {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .user-name {
    flex: 1;
    color: #2b3440;
    font-size: 14px;
  }
  
  .post-count {
    color: #9ca3af;
    font-size: 12px;
  }
}

/* 操作区 */
.action-section {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 12px;
  gap: 10px;
}

/* 表格优化 */
.el-table {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  /* 表头加轻微阴影分割 */
  .el-table__header {
    background: #fafafa;
  }
}

/* 行高与单元格内距 */
.el-table th,
.el-table td {
  padding: 12px 14px;
  vertical-align: middle;
  font-size: 14px;
  color: #334155;
}

/* 悬停与斑马线 */
.el-table .el-table__body tr:hover > td {
  background: #f4f6f8;
}
.el-table .el-table__body tr:nth-child(even) > td {
  background: #ffffff;
}
.el-table .el-table__body tr:nth-child(odd) > td {
  background: #fcfdff;
}

/* 文章标题区域：限制宽度、溢出省略 */
.post-title-cell {
  display: flex;
  align-items: center;
  .post-info {
    display: flex;
    flex-direction: column;
    min-width: 0;
    .title-text {
      font-weight: 600;
      margin-bottom: 6px;
      color: #111827;
      max-width: 520px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      display: inline-block;
    }
    .post-flags {
      .el-tag {
        margin-right: 6px;
      }
    }
  }
}

/* 作者列 */
.author-cell {
  display: flex;
  align-items: center;
  .el-avatar {
    width: 32px;
    height: 32px;
  }
  .author-name {
    margin-left: 10px;
    color: #2b3440;
    font-size: 13px;
  }
}

/* 标签列：允许换行并更紧凑 */
.el-table .el-tag {
  margin-right: 6px;
  margin-bottom: 6px;
  font-size: 12px;
}

/* 数据统计列 */
.stats-cell {
  display: flex;
  gap: 12px;
  color: #6b7280;
  align-items: center;
  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
  }
  .el-icon {
    color: #9ca3af;
  }
}

/* 操作按钮样式微调 */
.el-table .el-button {
  margin-right: 6px;
  padding: 4px 8px;
  font-size: 13px;
}

/* 分页 */
.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

/* 抽屉（详情）优化 */
.post-detail-content {
  padding: 20px 24px;
  .detail-title {
    font-size: 22px;
    font-weight: 700;
    margin-bottom: 14px;
    color: #0f172a;
  }
  .cover-section {
    margin-bottom: 18px;
    .detail-cover {
      width: 100%;
      max-height: 360px;
      border-radius: 8px;
      object-fit: cover;
      display: block;
    }
  }
  .meta-info {
    display: flex;
    align-items: center;
    justify-content: space-between;
    color: #9aa4b2;
    font-size: 13px;
    margin-bottom: 14px;
    .author-info {
      display: flex;
      align-items: center;
      span {
        margin-left: 10px;
        color: #2b3440;
        font-weight: 500;
      }
    }
  }
  .tags-section {
    margin-bottom: 12px;
    .el-tag {
      margin-right: 8px;
    }
  }
  .post-content {
    line-height: 1.9;
    color: #2d3748;
    font-size: 15px;
    :deep(img) {
      max-width: 100%;
      height: auto;
      border-radius: 8px;
      display: block;
      margin: 12px 0;
    }
    /* 代码块等内容容器优化 */
    :deep(pre) {
      background: #0b1220;
      color: #e6edf3;
      padding: 12px;
      border-radius: 6px;
      overflow-x: auto;
    }
  }
}

/* 抽屉加载占位 */
.drawer-loading {
  height: 100%;
}

/* 响应式：抽屉在小屏时更宽（覆盖更多） */
@media (max-width: 900px) {
  /* ElDrawer 内容宽度控制（ElementPlus 使用 transform 实现，需要调整 content 宽度） */
  :deep(.el-drawer__content) {
    width: 92% !important;
    max-width: 920px;
  }
  .search-section {
    padding: 12px;
  }
  .post-title-cell .title-text {
    max-width: 200px;
  }
}

/* 小调整：让表格选择列居中 */
.el-table .el-checkbox {
  margin: 0;
}
</style>
