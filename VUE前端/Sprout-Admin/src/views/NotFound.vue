<template>
  <div class="not-found">
    <div class="not-found-content">
      <div class="error-image">
        <el-icon size="120" color="#909399">
          <DocumentRemove />
        </el-icon>
      </div>
      
      <div class="error-text">
        <h1 class="error-title">404</h1>
        <p class="error-description">抱歉，您访问的页面不存在</p>
        <p class="error-suggestion">请检查网址是否正确，或返回管理后台首页</p>
      </div>
      
      <div class="error-actions">
        <el-button type="primary" size="large" @click="goHome">
          <el-icon><House /></el-icon>
          返回首页
        </el-button>
        <el-button size="large" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回上页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { DocumentRemove, House, ArrowLeft } from '@element-plus/icons-vue'

const router = useRouter()

const goHome = () => {
  router.push('/dashboard')
}

const goBack = () => {
  router.go(-1)
}
</script>

<style lang="scss" scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  
  .not-found-content {
    text-align: center;
    padding: 60px 40px;
    background: white;
    border-radius: 20px;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
    max-width: 500px;
    width: 100%;
    
    .error-image {
      margin-bottom: 30px;
    }
    
    .error-text {
      margin-bottom: 40px;
      
      .error-title {
        font-size: 72px;
        font-weight: 700;
        color: #409eff;
        margin: 0 0 20px 0;
        line-height: 1;
      }
      
      .error-description {
        font-size: 20px;
        color: #303133;
        margin: 0 0 12px 0;
        font-weight: 500;
      }
      
      .error-suggestion {
        font-size: 14px;
        color: #909399;
        margin: 0;
        line-height: 1.5;
      }
    }
    
    .error-actions {
      display: flex;
      gap: 16px;
      justify-content: center;
      flex-wrap: wrap;
    }
  }
}

@media (max-width: 768px) {
  .not-found {
    padding: 20px;
    
    .not-found-content {
      padding: 40px 24px;
      
      .error-title {
        font-size: 60px;
      }
      
      .error-description {
        font-size: 18px;
      }
      
      .error-actions {
        flex-direction: column;
        align-items: center;
      }
    }
  }
}
</style>
