<script setup>
import { RouterView } from 'vue-router'
import { onMounted, computed } from 'vue';
import LayoutContainer from '@/views/layout/LayoutContainer.vue';
// import { StagewiseToolbar } from '@stagewise/toolbar-vue';

// onMounted(() => {
//   // 检查用户是否设置了主题偏好
//   const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
//   const savedTheme = localStorage.getItem('theme');
  
//   if (savedTheme) {
//     document.documentElement.setAttribute('data-theme', savedTheme);
//   } else if (prefersDark) {
//     document.documentElement.setAttribute('data-theme', 'dark');
//   }
// });

// // 只在开发模式下显示工具栏
// const showToolbar = computed(() => process.env.NODE_ENV === 'development');

// // 工具栏配置
// const toolbarConfig = {
//   plugins: []
// };
</script>

<template>
  <div id="app">
    <router-view />
    
    <!-- 开发模式下显示工具栏
    <StagewiseToolbar v-if="showToolbar" :config="toolbarConfig" /> -->
  </div>
</template>

<style scoped>
</style>
