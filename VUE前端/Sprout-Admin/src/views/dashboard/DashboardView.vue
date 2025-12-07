<template>
  <div class="dashboard">
    <div class="page-title">仪表盘</div>
    
    <!-- 概览卡片 -->
    <el-row :gutter="24" class="overview-cards">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card primary">
          <el-icon class="stat-icon"><User /></el-icon>
          <div class="stat-number">{{ overview.totalUsers.toLocaleString() }}</div>
          <div class="stat-label">用户总数</div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card success">
          <el-icon class="stat-icon"><UserFilled /></el-icon>
          <div class="stat-number">{{ overview.activeUsers.toLocaleString() }}</div>
          <div class="stat-label">活跃用户</div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card warning">
          <el-icon class="stat-icon"><Document /></el-icon>
          <div class="stat-number">{{ overview.totalPosts.toLocaleString() }}</div>
          <div class="stat-label">帖子总数</div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card danger">
          <el-icon class="stat-icon"><ChatDotRound /></el-icon>
          <div class="stat-number">{{ overview.aiConversations.toLocaleString() }}</div>
          <div class="stat-label">AI对话数</div>
        </div>
      </el-col>
    </el-row>
    
    <!-- 图表区域 -->
    <el-row :gutter="24" class="charts-row">
      <!-- 用户增长趋势 -->
      <el-col :xs="24" :lg="12">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">用户增长趋势</h3>
          </div>
          <div class="card-body">
            <div class="chart-container">
              <v-chart :option="userGrowthOption" autoresize />
            </div>
          </div>
        </div>
      </el-col>
      
      <!-- 植物识别统计 -->
      <el-col :xs="24" :lg="12">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">植物识别统计</h3>
          </div>
          <div class="card-body">
            <div class="chart-container">
              <v-chart :option="plantRecognitionOption" autoresize />
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
    
    <el-row :gutter="24" class="charts-row">
      <!-- 帖子发布趋势 -->
      <el-col :xs="24" :lg="16">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">帖子发布趋势</h3>
          </div>
          <div class="card-body">
            <div class="chart-container">
              <v-chart :option="postTrendsOption" autoresize />
            </div>
          </div>
        </div>
      </el-col>
      
      <!-- 热门标签 -->
      <el-col :xs="24" :lg="8">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">热门标签</h3>
          </div>
          <div class="card-body">
            <div class="tag-list">
              <div
                v-for="(tag, index) in charts.hotTags"
                :key="tag.name"
                class="tag-item"
              >
                <span class="tag-rank">{{ index + 1 }}</span>
                <span class="tag-name">{{ tag.name }}</span>
                <span class="tag-count">{{ tag.count }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
    
    <!-- 快捷操作 -->
    <div class="admin-card">
      <div class="card-header">
        <h3 class="card-title">快捷操作</h3>
      </div>
      <div class="card-body">
        <el-row :gutter="16" class="quick-actions">
          <el-col :xs="12" :sm="6" :md="4">
            <div class="action-card" @click="$router.push('/posts')">
              <el-badge :value="pendingItems.pendingPosts" :hidden="pendingItems.pendingPosts === 0">
                <el-icon class="action-icon"><Document /></el-icon>
              </el-badge>
              <span class="action-text">文章管理</span>
            </div>
          </el-col>
          
          <el-col :xs="12" :sm="6" :md="4">
            <div class="action-card" @click="$router.push('/users')">
              <el-icon class="action-icon"><User /></el-icon>
              <span class="action-text">用户管理</span>
            </div>
          </el-col>
          
          <el-col :xs="12" :sm="6" :md="4">
            <div class="action-card" @click="$router.push('/comments')">
              <el-badge :value="pendingItems.pendingComments" :hidden="pendingItems.pendingComments === 0">
                <el-icon class="action-icon"><ChatLineRound /></el-icon>
              </el-badge>
              <span class="action-text">评论管理</span>
            </div>
          </el-col>
          
          <el-col :xs="12" :sm="6" :md="4">
            <div class="action-card" @click="$router.push('/plants')">
              <el-icon class="action-icon"><Orange /></el-icon>
              <span class="action-text">植物管理</span>
            </div>
          </el-col>
          
          <el-col :xs="12" :sm="6" :md="4">
            <div class="action-card" @click="$router.push('/reports')">
              <el-badge :value="pendingItems.pendingReports" :hidden="pendingItems.pendingReports === 0">
                <el-icon class="action-icon"><Warning /></el-icon>
              </el-badge>
              <span class="action-text">举报管理</span>
            </div>
          </el-col>
          
          <el-col :xs="12" :sm="6" :md="4">
            <div class="action-card" @click="$router.push('/settings')">
              <el-icon class="action-icon"><Setting /></el-icon>
              <span class="action-text">系统设置</span>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>
    
    <!-- 系统状态 -->
    <div class="admin-card">
      <div class="card-header">
        <h3 class="card-title">系统状态</h3>
      </div>
      <div class="card-body">
        <el-row :gutter="24">
          <el-col :xs="24" :sm="8">
            <div class="status-item">
              <span class="status-label">系统运行状态</span>
              <el-tag :type="systemStatusColor">{{ systemStatusText }}</el-tag>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="status-item">
              <span class="status-label">今日新增帖子</span>
              <span class="status-value">{{ overview.todayPosts }}</span>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="status-item">
              <span class="status-label">植物数据库</span>
              <span class="status-value">{{ overview.totalPlants }} 种</span>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import {
  User,
  UserFilled,
  Document,
  ChatDotRound,
  ChatLineRound,
  Orange,
  Warning,
  Setting
} from '@element-plus/icons-vue'

// 注册ECharts组件
use([
  CanvasRenderer,
  LineChart,
  PieChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const dashboardStore = useDashboardStore()

// 响应式数据
const loading = ref(false)

// 计算属性
const overview = computed(() => dashboardStore.overview)
const charts = computed(() => dashboardStore.charts)
const pendingItems = computed(() => dashboardStore.pendingItems)
const systemStatusColor = computed(() => dashboardStore.systemStatusColor)
const systemStatusText = computed(() => dashboardStore.systemStatusText)

// 用户增长趋势图配置
const userGrowthOption = computed(() => ({
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    data: ['新增用户', '活跃用户']
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    data: charts.value.userGrowth.map(item => item.date.slice(5))
  },
  yAxis: {
    type: 'value'
  },
  series: [
    {
      name: '新增用户',
      type: 'line',
      data: charts.value.userGrowth.map(item => item.users),
      smooth: true,
      itemStyle: { color: '#40b884' }
    },
    {
      name: '活跃用户',
      type: 'line',
      data: charts.value.userGrowth.map(item => item.activeUsers),
      smooth: true,
      itemStyle: { color: '#67c23a' }
    }
  ]
}))

// 植物识别统计图配置
const plantRecognitionOption = computed(() => ({
  tooltip: {
    trigger: 'item',
    formatter: '{a} <br/>{b}: {c} ({d}%)'
  },
  legend: {
    orient: 'vertical',
    left: 'left'
  },
  series: [
    {
      name: '识别结果',
      type: 'pie',
      radius: ['40%', '70%'],
      data: charts.value.plantRecognition,
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }
  ]
}))

// 帖子发布趋势图配置
const postTrendsOption = computed(() => ({
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    data: ['帖子数', '评论数']
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    data: charts.value.postTrends.map(item => item.date.slice(5))
  },
  yAxis: {
    type: 'value'
  },
  series: [
    {
      name: '帖子数',
      type: 'bar',
      data: charts.value.postTrends.map(item => item.posts),
      itemStyle: { color: '#40b884' }
    },
    {
      name: '评论数',
      type: 'bar',
      data: charts.value.postTrends.map(item => item.comments),
      itemStyle: { color: '#67c23a' }
    }
  ]
}))

// 生命周期
onMounted(async () => {
  loading.value = true
  await dashboardStore.fetchDashboardData()
  loading.value = false
})
</script>

<style lang="scss" scoped>
.dashboard {
  .overview-cards {
    margin-bottom: 24px;
  }
  
  .charts-row {
    margin-bottom: 24px;
  }
  
  .tag-list {
    .tag-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 0;
      border-bottom: 1px solid var(--admin-border);
      
      &:last-child {
        border-bottom: none;
      }
      
      .tag-rank {
        width: 24px;
        height: 24px;
        border-radius: 50%;
        background: var(--admin-primary);
        color: white;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 12px;
        font-weight: 600;
        
        &:nth-child(1) {
          background: var(--admin-danger);
        }
        &:nth-child(2) {
          background: var(--admin-warning);
        }
        &:nth-child(3) {
          background: var(--admin-success);
        }
      }
      
      .tag-name {
        flex: 1;
        margin-left: 12px;
        font-size: 14px;
        color: var(--admin-text);
      }
      
      .tag-count {
        font-size: 12px;
        color: var(--admin-text-lighter);
        font-weight: 600;
      }
    }
  }
  
  .quick-actions {
    .action-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 24px 16px;
      background: var(--admin-primary-lightest);
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.3s;
      position: relative;
      border: 1px solid var(--admin-border);
      
      &:hover {
        background: var(--admin-primary-lighter);
        transform: translateY(-2px);
        box-shadow: var(--el-box-shadow-light);
      }
      
      .action-icon {
        font-size: 32px;
        color: var(--admin-primary);
      }
      
      .action-text {
        font-size: 14px;
        color: var(--admin-text);
        font-weight: 500;
      }
    }
  }
  
  .status-item {
    display: flex;
    flex-direction: column;
    gap: 8px;
    
    .status-label {
      font-size: 14px;
      color: var(--admin-text-lighter);
    }
    
    .status-value {
      font-size: 18px;
      font-weight: 600;
      color: var(--admin-text);
    }
  }
}
</style>
