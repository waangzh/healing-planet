<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { searchTopic } from '@/api/search';
import { ElMessage } from 'element-plus';

const route = useRoute();
const router = useRouter();
const searchResults = ref([]);
const loading = ref(false);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);

const fetchSearchResults = async () => {
  const keyword = route.query.keyword;
  if (!keyword) return;

  try {
      loading.value = true;
    console.log(keyword, currentPage.value, pageSize.value);
    
    const res = await searchTopic({
      keyword,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    });

    if (res.data.code === 200) {
      searchResults.value = res.data.data.records;
      total.value = res.data.data.total;
    } else {
      ElMessage.error(res.data.message);
    }
  } catch (error) {
    console.error('搜索失败:', error);
    ElMessage.error('搜索失败，请重试');
  } finally {
    loading.value = false;
  }
};

const handlePageChange = (page) => {
  currentPage.value = page;
  fetchSearchResults();
};

const goToPostDetail = (postId) => {
  router.push(`/post/${postId}`);
};

const formatDate = (dateString) => {
  const date = new Date(dateString);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
};

watch(() => route.query.keyword, () => {
  currentPage.value = 1;
  fetchSearchResults();
});

onMounted(() => {
  fetchSearchResults();
});
</script>

<template>
  <div class="search-container">
    <div class="search-header">
      <h2>搜索结果: {{ route.query.keyword }}</h2>
      <div class="result-count">共 {{ total }} 条结果</div>
    </div>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="searchResults.length === 0" class="no-results">
      暂无搜索结果
    </div>

    <div v-else class="search-results">
      <div 
        v-for="post in searchResults" 
        :key="post.id" 
        class="result-item"
        @click="goToPostDetail(post.id)"
      >
        <div class="post-info">
          <h3 class="post-title">{{ post.title }}</h3>
          <div class="post-meta">
            <span class="author">{{ post.username }}</span>
            <span class="dot">·</span>
            <span class="date">{{ formatDate(post.createTime) }}</span>
            <span class="dot">·</span>
            <span class="view-count">{{ post.view }} 浏览</span>
          </div>
          <div class="tags">
            <el-tag
              v-for="tag in post.tags"
              :key="tag.id"
              size="small"
              effect="plain"
              class="tag"
            >
              #{{ tag.name }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <div class="pagination-container" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        @current-change="handlePageChange"
        layout="prev, pager, next"
      />
    </div>
  </div>
</template>

<style scoped>
.search-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.search-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color);
}

.search-header h2 {
  font-size: 24px;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.result-count {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.loading-container {
  padding: 20px;
}

.no-results {
  text-align: center;
  padding: 40px;
  color: var(--el-text-color-secondary);
  font-size: 16px;
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-item {
  padding: 20px;
  background-color: var(--el-bg-color-overlay);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px var(--el-box-shadow-light);
}

.result-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--el-box-shadow);
}

.post-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}

.post-meta {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.dot {
  font-size: 12px;
}

.tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  color: var(--el-color-primary);
  background: none;
  border: none;
  padding: 0;
}

.pagination-container {
  margin-top: 32px;
  display: flex;
  justify-content: center;
}
</style>

