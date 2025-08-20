<script setup>
import TopNavbar from '@/components/layout/TopNavbar.vue';
</script>

<template>
  <div class="layout-container">
    <TopNavbar />
    <div class="main-content">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.layout-container {
  width: 100%;
  min-height: 100vh;
  background-color: #EFF2F6;
}

.main-content {
  max-width: 1400px;
  margin: 20px auto;
  padding: 0 20px;
}

@media (max-width: 768px) {
  .main-content {
    margin: 10px auto;
    padding: 0 10px;
  }
}
</style>
