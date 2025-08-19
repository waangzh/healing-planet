<script setup>
import { ref, onMounted } from 'vue';
import { getDailyMessage } from '@/api/dailymessage';

const message = ref({
  content: '',
  author: ''
});

const fetchDailyMessage = async () => {
  try {
    const res = await getDailyMessage();
    if (res.data.code === 200) {
      message.value = {
        content: res.data.data.content,
        author: res.data.data.author
      };
    }
  } catch (error) {
    console.error('获取每日一句失败:', error);
  }
};

onMounted(() => {
  fetchDailyMessage();
});
</script>

<template>
  <div class="daily-message">
    <div class="message-header">
      <i class="fas fa-quote-left"></i>
      <h3>每日一句</h3>
    </div>
    <div class="message-content">
      <p class="content">{{ message.content }}</p>
      <p class="author">—— {{ message.author }}</p>
    </div>
  </div>
</template>

<style scoped>
.daily-message {
  background-color: var(--el-bg-color-page);
  border-radius: 10px;
  padding: 15px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px var(--el-box-shadow);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;
}

.message-header i {
  color: var(--primary);
  font-size: 18px;
}

.message-header h3 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
}

.message-content {
  padding: 0 10px;
}

.content {
  color: var(--text);
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 10px;
}

.author {
  color: var(--text-secondary);
  font-size: 12px;
  text-align: right;
  margin: 0;
}
</style>