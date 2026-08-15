<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { searchTopic } from '@/api/search';
import { searchEvidence } from '@/api/rag';
import { ElMessage } from 'element-plus';

const route = useRoute();
const router = useRouter();
const searchResults = ref([]);
const loading = ref(false);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const semanticResults = ref([]);
const semanticLoading = ref(false);

const fetchSemanticResults = async (keyword) => {
  semanticLoading.value = true;
  try {
    const evidence = await searchEvidence({ query: keyword });
    semanticResults.value = evidence
      .filter((item) => item.type === 'COMMUNITY_POST')
      .slice(0, 6);
  } catch (error) {
    console.warn('语义检索暂时不可用:', error);
    semanticResults.value = [];
  } finally {
    semanticLoading.value = false;
  }
};

const fetchSearchResults = async () => {
  const keyword = route.query.keyword;
  if (!keyword) return;

  fetchSemanticResults(keyword);

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

    <section v-if="semanticLoading || semanticResults.length" class="semantic-section">
      <div class="semantic-heading">
        <div><span>GREENCARE RAG</span><h3>AI 语义发现</h3></div>
        <small>不只匹配关键词，也理解症状与养护场景</small>
      </div>
      <el-skeleton v-if="semanticLoading" :rows="2" animated />
      <div v-else class="semantic-grid">
        <button
          v-for="evidence in semanticResults"
          :key="evidence.id"
          type="button"
          class="semantic-card"
          @click="goToPostDetail(evidence.sourceId)"
        >
          <span class="semantic-type">社区经验</span>
          <strong>{{ evidence.title }}</strong>
          <p>{{ evidence.content }}</p>
          <span class="semantic-score">
            相关度 {{ Math.round((evidence.finalScore ?? evidence.rerankScore ?? evidence.retrievalScore ?? 0) * 100) }}%
          </span>
        </button>
      </div>
    </section>

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

.semantic-section {
  margin-bottom: 28px;
  padding: 20px;
  border: 1px solid #d9e9df;
  border-radius: 14px;
  background: linear-gradient(145deg, #f7fbf5, #f2f8f4 68%, #faf5e8);
}

.semantic-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 16px;
}

.semantic-heading span {
  color: #7e9b8c;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .14em;
}

.semantic-heading h3 {
  margin: 3px 0 0;
  color: #214f3c;
  font-family: "STZhongsong", "SimSun", serif;
  font-size: 20px;
}

.semantic-heading small {
  color: #75887f;
  font-size: 12px;
}

.semantic-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.semantic-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid #deebe4;
  border-radius: 11px;
  color: #315f4c;
  background: rgba(255, 255, 255, .88);
  text-align: left;
  cursor: pointer;
  transition: .2s ease;
}

.semantic-card:hover {
  transform: translateY(-2px);
  border-color: #99cdb1;
  box-shadow: 0 9px 22px rgba(46, 105, 78, .09);
}

.semantic-type, .semantic-score {
  color: #5d987a;
  font-size: 10px;
}

.semantic-card strong {
  display: block;
  margin: 7px 0;
  overflow: hidden;
  color: #244c3c;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.semantic-card p {
  display: -webkit-box;
  margin: 0 0 10px;
  overflow: hidden;
  color: #75887f;
  font-size: 11px;
  line-height: 1.6;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
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

@media (max-width: 900px) {
  .semantic-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 600px) {
  .semantic-heading { align-items: flex-start; flex-direction: column; gap: 5px; }
  .semantic-grid { grid-template-columns: 1fr; }
}
</style>
